package app.pocketinstall

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.pocketinstall.server.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean

data class WinPeDownloadState(val active: Boolean = false, val message: String = "", val bytes: Long = 0,
    val total: Long = 0, val prepared: Long = 0)
object WinPeDownloadStore {
    internal val mutable = MutableStateFlow(WinPeDownloadState())
    val state = mutable.asStateFlow()
}

/** An explicit user-started download continues while the activity is in the background. */
class WinPeDownloadService : Service() {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var connection: HttpURLConnection? = null
    private var worker: Thread? = null
    private var wake: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action == CANCEL) { cancel(); return START_NOT_STICKY }
        if(worker != null || intent?.action != START) return START_NOT_STICKY
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"Téléchargement WinPE",NotificationManager.IMPORTANCE_LOW))
        try { startForeground(4,notification("Préparation de WinPE…")) }
        catch(e: Exception) {
            WinPeDownloadStore.mutable.update { it.copy(active=false,message="Téléchargement indisponible : ${e.message}") }
            stopSelf(); return START_NOT_STICKY
        }
        if(ServerStore.state.value.status in setOf(ServerStatus.RUNNING,ServerStatus.STARTING) || ServerStore.state.value.importingWinPe || WindowsDownloadStore.state.value.active || LinuxDownloadStore.state.value.active) {
            WinPeDownloadStore.mutable.update { it.copy(active=false,message="Arrête le serveur et attends la fin des imports avant de télécharger.") }
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        WinPeDownloadStore.mutable.update { it.copy(active=true,message="Connexion à GitHub…",bytes=0,total=0) }
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PocketInstall:WinPeDownload").apply { acquire(2*60*60*1000L) }
        val approvedBytes=intent.getLongExtra("approvedBytes",0)
        worker = Thread({
            val file=File(cacheDir,"winpe-download.zip.part")
            try {
                var last = 0L
                GithubBundle.download(file,approvedBytes,{cancelled.get()},{connection=it}) { bytes,total ->
                    if(SystemClock.elapsedRealtime()-last>=400 || bytes==total) {
                        last=SystemClock.elapsedRealtime()
                        val message="WinPE · ${bytes/1048576} / ${total/1048576} Mio"
                        WinPeDownloadStore.mutable.update { it.copy(bytes=bytes,total=total,message=message) }
                        notifications.notify(4,notification(message,if(total>0)(bytes*100/total).toInt() else null))
                    }
                }
                if(cancelled.get())throw InterruptedException()
                ServerStore.mutable.update { it.copy(importingWinPe=true) }
                WinPeDownloadStore.mutable.update { it.copy(message="Import et validation de WinPE…") }
                file.inputStream().use { WinPeStorage.importBundle(this,it) }
                WinPeDownloadStore.mutable.update { it.copy(active=false,message="WinPE prêt · fichiers et routes vérifiés.",prepared=it.prepared+1) }
            } catch(e: Exception) {
                WinPeDownloadStore.mutable.update { it.copy(active=false,message=if(cancelled.get()) "Téléchargement annulé." else "Téléchargement non terminé : ${e.message}") }
            } finally {
                file.delete()
                ServerStore.mutable.update { it.copy(importingWinPe=false) }
                runCatching { if(wake?.isHeld == true) wake?.release() }
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            }
        },"PocketInstall-ISO").apply { start() }
        return START_NOT_STICKY
    }
    private fun notification(message: String, percent: Int? = null): Notification {
        val cancel = PendingIntent.getService(this,4,Intent(this,javaClass).setAction(CANCEL),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this,4,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_pocketinstall).setContentTitle("PocketInstall · WinPE")
            .setContentText(message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100,percent ?: 0,percent == null).addAction(0,"Annuler",cancel).build()
    }
    private fun cancel() { cancelled.set(true); connection?.disconnect(); worker?.interrupt() }
    override fun onTimeout(startId: Int, fgsType: Int) { cancel(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { cancel(); runCatching { if(wake?.isHeld == true) wake?.release() }; super.onDestroy() }
    companion object {
        private const val CHANNEL = "winpe-download"
        private const val START = "app.pocketinstall.DOWNLOAD_WINPE"
        private const val CANCEL = "app.pocketinstall.CANCEL_WINPE"
        fun start(context: Context,approvedBytes: Long) {
            WinPeDownloadStore.mutable.update { it.copy(active=true,message="Démarrage du téléchargement…",bytes=0,total=0) }
            try { ContextCompat.startForegroundService(context,Intent(context,WinPeDownloadService::class.java).setAction(START).putExtra("approvedBytes",approvedBytes)) }
            catch(e: Exception) { WinPeDownloadStore.mutable.update { it.copy(active=false,message="Téléchargement indisponible : ${e.message}") } }
        }
        fun cancel(context: Context) { context.startService(Intent(context,WinPeDownloadService::class.java).setAction(CANCEL)) }
    }
}
