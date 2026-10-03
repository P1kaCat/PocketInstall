package app.pocketinstall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import app.pocketinstall.server.BootResource
import app.pocketinstall.server.Ipv4Subnet
import app.pocketinstall.server.LocalHttpServer
import app.pocketinstall.server.LocalTftpServer
import app.pocketinstall.server.RequestPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class PocketInstallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopping = AtomicBoolean(false)
    private val lock = Any()
    private var server: LocalHttpServer? = null
    private var tftp: LocalTftpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var selected: LanCandidate? = null
    private val peers = ConcurrentHashMap<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSession("Serveur arrêté.")
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || server != null || stopping.get()) return START_NOT_STICKY
        try {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Session PocketInstall", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("PocketInstall · serveur local")
                .setContentText("Session limitée à 30 minutes. Aucun accès disque.")
                .setContentIntent(open).setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", stop).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(1, notification)
            val candidate = intent.getStringExtra(EXTRA_CANDIDATE) ?: ""
            val usb = intent.getBooleanExtra(EXTRA_USB, false)
            val pxe = intent.getBooleanExtra(EXTRA_PXE, false)
            require(!usb || !pxe) { "PXE USB is not supported by this prototype" }
            ServerStore.mutable.value = ServerSnapshot(status = ServerStatus.STARTING, usbMode = usb, pxeMode = pxe,
                message = "Ouverture de la session…")
            scope.launch {
                try { startSession(candidate, usb, pxe) }
                catch (e: Exception) {
                    if (!stopping.get()) {
                        stopSession("Impossible de démarrer : ${e.javaClass.simpleName}. Vérifie l'interface choisie et le port 8080.")
                        ServerStore.mutable.update { it.copy(status = ServerStatus.ERROR) }
                    }
                }
            }
        } catch (e: Exception) {
            stopSession("Service refusé : ${e.javaClass.simpleName}.")
            ServerStore.mutable.update { it.copy(status = ServerStatus.ERROR) }
        }
        return START_NOT_STICKY
    }

    private fun startSession(candidateId: String, usb: Boolean, pxe: Boolean) = synchronized(lock) {
        if (stopping.get() || server != null) return@synchronized
        val lan = LanNetwork.candidates(this, usb).firstOrNull { it.id == candidateId }
            ?: error("Interface privée absente ou modifiée")
        selected = lan
        val length = assets.openFd("boot/bootx64.efi").use { it.length }
        val subnet = Ipv4Subnet(lan.address, lan.prefix)
        val resources = mapOf("bootx64.efi" to BootResource(length, "application/efi") { assets.open("boot/bootx64.efi") })
        val http = LocalHttpServer(lan.address, subnet, resources,
            monotonicMillis = { SystemClock.elapsedRealtime() },
            onEvent = { event ->
                if (stopping.get()) return@LocalHttpServer
                val now = System.currentTimeMillis()
                if (event.phase == RequestPhase.STARTED && event.status in listOf(200, 206)) {
                    if (peers.size < 128 || peers.containsKey(event.peer)) peers[event.peer] = now
                }
                peers.entries.removeIf { now - it.value > 5 * 60 * 1000 }
                ServerStore.mutable.update { old ->
                    val entries = old.events.filterNot { it.id == event.id } + event
                    old.copy(requests = old.requests + if (event.phase == RequestPhase.STARTED) 1 else 0,
                        clientsSeen = peers.size, events = entries.takeLast(50))
                }
            }, onStop = { reason -> stopSession(reason) })
        server = http
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketInstall:session")
            .apply { acquire(30 * 60 * 1000L) }
        @Suppress("DEPRECATION")
        if (lan.network != null && getSystemService(ConnectivityManager::class.java).getNetworkCapabilities(lan.network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            wifiLock = applicationContext.getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PocketInstall:LAN").apply { acquire() }
        }
        http.start()
        if (stopping.get()) return@synchronized
        if (pxe) {
            fun createTftp(port: Int) = LocalTftpServer(lan.address, subnet, resources,
                requestedPort = port, session = http.session,
                monotonicMillis = { SystemClock.elapsedRealtime() },
                onEvent = { event ->
                    if (stopping.get()) return@LocalTftpServer
                    if (event.phase == RequestPhase.STARTED && (peers.size < 128 || peers.containsKey(event.peer)))
                        peers[event.peer] = System.currentTimeMillis()
                    ServerStore.mutable.update { old -> old.copy(
                        clientsSeen = peers.size,
                        tftpEvents = (old.tftpEvents.filterNot { it.id == event.id } + event).takeLast(50)) }
                }, onStop = { reason -> stopSession(reason) })
            val standard = createTftp(69)
            var fallbackReason = ""
            tftp = try { standard.start(); standard }
                catch (e: Exception) {
                    standard.close()
                    fallbackReason = e.javaClass.simpleName
                    createTftp(6969).also { it.start() }
                }
            val activeTftp = checkNotNull(tftp)
            ServerStore.mutable.update { it.copy(tftpPort = activeTftp.port, bootFilename = activeTftp.bootFilename,
                tftpMessage = if (activeTftp.port == 69)
                    "TFTP standard ouvert. Le DHCP doit encore annoncer ce téléphone et le fichier de boot."
                else "Port UDP 69 indisponible ($fallbackReason). TFTP ouvert sur 6969 : relais ou redirection UDP 69 obligatoire. PXE direct ne peut pas utiliser ce port.") }
        }
        monitor(lan)
        scope.launch {
            while (!stopping.get()) {
                delay(15000)
                val now = System.currentTimeMillis()
                peers.entries.removeIf { now - it.value > 5 * 60 * 1000 }
                ServerStore.mutable.update { it.copy(clientsSeen = peers.size) }
            }
        }
        ServerStore.mutable.update { it.copy(status = ServerStatus.RUNNING, ip = lan.address.hostAddress ?: "—",
            url = http.bootUrl, subnet = subnet.toString(), expiresAt = System.currentTimeMillis() + 30 * 60 * 1000,
            message = if (pxe) "HTTP et TFTP ouverts · configuration DHCP / relais requise. Compatibilité physique non vérifiée."
                else if (usb) "Serveur USB prêt · compatibilité UEFI non vérifiée. Vérifie le succès sur le PC."
                else "Test EFI prêt. Vérifie le message de succès sur le PC.") }
    }

    private fun monitor(lan: LanCandidate) {
        if (lan.network == null) {
            scope.launch {
                while (!stopping.get()) {
                    delay(1000)
                    if (LanNetwork.usbCandidates().none { it.id == lan.id }) {
                        stopSession("Interface USB perdue ou IP modifiée. Réactive le partage USB puis recommence.")
                        return@launch
                    }
                }
            }
            return
        }
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) { if (network == lan.network) stopSession("Connexion LAN perdue. Redémarre une session.") }
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                if (network == lan.network && properties.linkAddresses.none { it.address == lan.address && it.prefixLength == lan.prefix })
                    stopSession("L'adresse IP a changé. Redémarre une session.")
            }
        }
        callback = cb
        getSystemService(ConnectivityManager::class.java).registerNetworkCallback(NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), cb)
    }

    private fun stopSession(reason: String) {
        if (!stopping.compareAndSet(false, true)) return
        synchronized(lock) {
            tftp?.close(); tftp = null
            server?.close(); server = null
            callback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
            callback = null
            wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
            wifiLock?.let { if (it.isHeld) it.release() }; wifiLock = null
        }
        ServerStore.mutable.update { it.copy(status = ServerStatus.STOPPED, url = "", bootFilename = "", tftpPort = 0,
            expiresAt = 0, message = reason) }
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopSession("Service arrêté."); super.onDestroy() }

    companion object {
        const val ACTION_START = "app.pocketinstall.START"
        const val ACTION_STOP = "app.pocketinstall.STOP"
        const val EXTRA_CANDIDATE = "candidateId"
        const val EXTRA_USB = "usbMode"
        const val EXTRA_PXE = "pxeMode"
        private const val CHANNEL = "pocketinstall-session"
    }
}
