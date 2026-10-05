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
import app.pocketinstall.server.LinuxHttp
import app.pocketinstall.server.LinuxInstaller
import app.pocketinstall.server.LinuxProfile
import app.pocketinstall.server.WinPeHttp
import app.pocketinstall.server.WinPeBundle
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
import java.io.File
import org.json.JSONObject

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
        if (intent?.action != ACTION_START || server != null || stopping.get() || ServerStore.state.value.status == ServerStatus.STARTING || ServerStore.state.value.importingWinPe || WindowsDownloadStore.state.value.active || LinuxDownloadStore.state.value.active || WinPeDownloadStore.state.value.active) return START_NOT_STICKY
        try {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Session PocketInstall", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("PocketInstall · serveur local")
                .setContentText("Session locale limitée à 30 minutes.")
                .setContentIntent(open).setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", stop).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(1, notification)
            val candidate = intent.getStringExtra(EXTRA_CANDIDATE) ?: ""
            val usb = intent.getBooleanExtra(EXTRA_USB, false)
            val pxe = intent.getBooleanExtra(EXTRA_PXE, false)
            val winPe = intent.getBooleanExtra(EXTRA_WINPE, false)
            val linux = intent.getStringExtra(EXTRA_LINUX)?.let { LinuxProfile.valueOf(it) }
            require(linux == null || (!winPe && !usb && !pxe))
            require(!winPe || (!usb && !pxe))
            require(!usb || !pxe) { "PXE USB is not supported by this prototype" }
            ServerStore.mutable.value = ServerSnapshot(status = ServerStatus.STARTING, usbMode = usb, pxeMode = pxe, winPeMode = winPe, linuxProfile = linux,
                message = "Ouverture de la session…")
            scope.launch {
                try { startSession(candidate, usb, pxe, winPe, linux) }
                catch (e: Exception) {
                    if (!stopping.get()) {
                        stopSession("Impossible de démarrer : ${e.message ?: e.javaClass.simpleName}.")
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

    private fun startSession(candidateId: String, usb: Boolean, pxe: Boolean, winPe: Boolean, linux: LinuxProfile?) {
        if (stopping.get() || server != null) return
        val lan = LanNetwork.candidates(this, usb).firstOrNull { it.id == candidateId }
            ?: error("Interface privée absente ou modifiée")
        selected = lan
        val length = assets.openFd("boot/bootx64.efi").use { it.length }
        val subnet = Ipv4Subnet(lan.address, lan.prefix)
        val resources = mutableMapOf("bootx64.efi" to BootResource(length, "application/efi") { assets.open("boot/bootx64.efi") })
        val directory = if (winPe) WinPeStorage.current(this)?.also { WinPeStorage.verify(it) }
            ?: error("Importe un bundle WinPE d'abord.") else null
        val linuxDirectory = if(linux != null) LinuxStorage.current(this)?.also { LinuxInstaller.verify(it) }
            ?: error("Télécharge Debian dans Préparer.") else null
        val windows = if (winPe && WindowsStorage.enabled(this)) WindowsStorage.current(this)
            ?: error("Importe une image Windows avant de préparer l'installation.") else null
        val imageInfo = windows?.let { WindowsStorage.info(it, verifyHash = true) }
        val installPlan = imageInfo?.let { WindowsStorage.plan(WindowsStorage.selection(this), it) }
            ?: "{\"enabled\":false}".toByteArray()
        // Large immutable images are verified outside the lifecycle lock so stopping the service stays responsive.
        synchronized(lock) {
            if (stopping.get() || server != null) return@synchronized
            val http = LocalHttpServer(lan.address, subnet, if (winPe || linux != null) emptyMap() else resources,
                resourceFactory = if(linuxDirectory != null && linux != null) ({ base ->
                    val loaderLength=assets.openFd("boot/snponly.efi").use { it.length }
                    LinuxHttp.resources(linuxDirectory,base,linux) + mapOf("linux/snponly.efi" to BootResource(loaderLength,"application/efi") {assets.open("boot/snponly.efi")})
                }) else if (directory != null) ({ base ->
                    WinPeHttp.resources(directory, base, installPlan) + if(windows != null && imageInfo != null)
                        mapOf("install/image.wim" to BootResource(imageInfo.bytes,"application/octet-stream") { File(windows,"image.wim").inputStream() }) else emptyMap()
                }) else null,
                publicAliases = if(linux != null) LinuxHttp.aliases else if (winPe) WinPeHttp.aliases else emptyMap(),
                monotonicMillis = { SystemClock.elapsedRealtime() },
                onEvent = { event ->
                    if (stopping.get() || event.peer == lan.address.hostAddress) return@LocalHttpServer
                    val now = System.currentTimeMillis()
                    if (event.phase == RequestPhase.STARTED && event.status in listOf(200, 206)) {
                        if (peers.size < 128 || peers.containsKey(event.peer)) peers[event.peer] = now
                    }
                    peers.entries.removeIf { now - it.value > 5 * 60 * 1000 }
                    ServerStore.mutable.update { old ->
                        val entries = old.events.filterNot { it.id == event.id } + event
                        old.copy(requests = old.requests + if (event.phase == RequestPhase.STARTED) 1 else 0,
                            clientsSeen = peers.size, events = entries.takeLast(50),
                            linuxProgress = if(linux != null) old.linuxProgress.accept(event) else old.linuxProgress,
                            winPeProgress = if (winPe) old.winPeProgress.accept(event) else old.winPeProgress)
                    }
                }, onStop = { reason -> stopSession(reason) },
                reportHandler = if(winPe) ({ peer, body -> acceptReport(peer,body) }) else null)
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
            if(linuxDirectory != null && linux != null) LinuxHttp.check(http,lan.address,linuxDirectory,linux)
            if (directory != null) WinPeHttp.check(http, lan.address, directory)
            if (windows != null) WinPeHttp.checkImage(http,lan.address,File(windows,"image.wim"))
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
                url = if(linux != null) "${http.baseUrl}/linux/boot.ipxe" else if (winPe) "${http.baseUrl}/winpe/boot.ipxe" else http.bootUrl,
                loaderUrl = if(linux != null) "${http.baseUrl}/linux/snponly.efi" else if (winPe) "${http.baseUrl}/winpe/snponly.efi" else "", subnet = subnet.toString(), expiresAt = System.currentTimeMillis() + 30 * 60 * 1000,
                message = if(linux != null) "Serveur Debian démarré · routes vérifiées. Démarre le PC en PXE." else if (winPe) "Serveur démarré · routes WinPE vérifiées. Démarre le PC en PXE." else if (pxe) "HTTP et TFTP ouverts · configuration DHCP / relais requise. Compatibilité physique non vérifiée."
                    else if (usb) "Serveur USB prêt · compatibilité UEFI non vérifiée. Vérifie le succès sur le PC."
                    else "Test EFI prêt. Vérifie le message de succès sur le PC.") }
        }
    }

    private fun acceptReport(peer: String, body: ByteArray): Boolean = runCatching {
        val current = ServerStore.state.value
        val incomingStage = JSONObject(body.toString(Charsets.UTF_8)).getString("stage")
        require(current.winPeMode && (current.winPeProgress.peer == peer ||
            incomingStage == "windows-started" && current.installMessage.startsWith("Windows appliqué")) &&
            current.winPeProgress.stage == app.pocketinstall.server.WinPeStage.STARTED)
        val json = JSONObject(body.toString(Charsets.UTF_8))
        val labels = mapOf("inventory" to "Matériel détecté", "awaiting-disk" to "Choisis le disque sur le PC",
            "awaiting-confirmation" to "Confirme l'effacement sur le PC", "partitioning" to "Partitionnement du disque confirmé sur le PC",
            "downloading" to "Transfert de l'image Windows", "verifying" to "Vérification de l'image sur le PC",
            "applying" to "Installation de Windows", "configuring" to "Configuration et débloat",
            "prepared" to "Windows appliqué · premier démarrage à confirmer sur le PC",
            "windows-started" to "Windows démarré · termine la configuration sur le PC", "error" to "Installation interrompue")
        if(incomingStage == "windows-started") require(current.installMessage.startsWith("Windows appliqué"))
        val stage = json.getString("stage"); require(stage in labels)
        val hardware = json.optJSONObject("hardware")
        val summary = hardware?.let { "${it.optString("model").take(100)} · ${it.optString("cpu").take(150)} · " +
            "${it.optLong("ramBytes") / 1073741824} Go RAM · ${it.optInt("cores")} cœurs · TPM ${it.optString("tpm").take(30)}" }
        ServerStore.mutable.update { it.copy(pcHardware=summary ?: it.pcHardware,
            installMessage=labels.getValue(stage) + json.optString("message").take(500).let { detail -> if(detail.isEmpty()) "" else "\n$detail" }) }
        true
    }.getOrDefault(false)

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
        const val EXTRA_LINUX = "linuxProfile"
        const val EXTRA_WINPE = "winPeMode"
        private const val CHANNEL = "pocketinstall-session"
    }
}
