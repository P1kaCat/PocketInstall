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

data class LinuxDownloadState(val active: Boolean = false, val message: String = "", val bytes: Long = 0,
    val total: Long = 0, val prepared: Long = 0)
object LinuxDownloadStore {
    internal val mutable = MutableStateFlow(LinuxDownloadState())
    val state = mutable.asStateFlow()
}

/** An explicit user-started download continues while the activity is in the background. */
class LinuxDownloadService : Service() {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var connection: HttpURLConnection? = null
    private var worker: Thread? = null
    private var wake: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action == CANCEL) { cancel(); return START_NOT_STICKY }
        if(worker != null || intent?.action != START) return START_NOT_STICKY
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"Téléchargement Debian",NotificationManager.IMPORTANCE_LOW))
        try { startForeground(3,notification("Préparation de Debian…")) }
        catch(e: Exception) {
            LinuxDownloadStore.mutable.update { it.copy(active=false,message="Téléchargement indisponible : ${e.message}") }
            stopSelf(); return START_NOT_STICKY
        }
        if(ServerStore.state.value.status in setOf(ServerStatus.RUNNING,ServerStatus.STARTING) || WinPeDownloadStore.state.value.active || ServerStore.state.value.importingWinPe || WindowsDownloadStore.state.value.active) {
            LinuxDownloadStore.mutable.update { it.copy(active=false,message="Arrête le serveur et attends la fin des imports avant de télécharger.") }
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        LinuxDownloadStore.mutable.update { it.copy(active=true,message="Connexion à Debian…",bytes=0,total=0) }
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PocketInstall:LinuxDownload").apply { acquire(2*60*60*1000L) }
        worker = Thread({
            try {
                var last = 0L
                LinuxStorage.prepare(this,{cancelled.get()},{connection=it}) { name,bytes,total ->
                    if(SystemClock.elapsedRealtime()-last>=400 || bytes==total) {
                        last=SystemClock.elapsedRealtime()
                        val message="Debian · $name · ${bytes/1048576} Mio"
                        LinuxDownloadStore.mutable.update { it.copy(bytes=bytes,total=total,message=message) }
                        notifications.notify(3,notification(message,if(total>0)(bytes*100/total).toInt() else null))
                    }
                }
                LinuxDownloadStore.mutable.update { it.copy(active=false,message="Debian prêt · fichiers et routes vérifiés.",prepared=it.prepared+1) }
            } catch(e: Exception) {
                LinuxDownloadStore.mutable.update { it.copy(active=false,message=if(cancelled.get()) "Téléchargement annulé." else "Téléchargement non terminé : ${e.message}") }
            } finally {
                runCatching { if(wake?.isHeld == true) wake?.release() }
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            }
        },"PocketInstall-ISO").apply { start() }
        return START_NOT_STICKY
    }
    private fun notification(message: String, percent: Int? = null): Notification {
        val cancel = PendingIntent.getService(this,3,Intent(this,javaClass).setAction(CANCEL),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this,3,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_pocketinstall).setContentTitle("PocketInstall · Debian")
            .setContentText(message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100,percent ?: 0,percent == null).addAction(0,"Annuler",cancel).build()
    }
    private fun cancel() { cancelled.set(true); connection?.disconnect(); worker?.interrupt() }
    override fun onTimeout(startId: Int, fgsType: Int) { cancel(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { cancel(); runCatching { if(wake?.isHeld == true) wake?.release() }; super.onDestroy() }
    companion object {
        private const val CHANNEL = "linux-download"
        private const val START = "app.pocketinstall.DOWNLOAD_LINUX"
        private const val CANCEL = "app.pocketinstall.CANCEL_LINUX"
        fun start(context: Context) {
            LinuxDownloadStore.mutable.update { it.copy(active=true,message="Démarrage du téléchargement…",bytes=0,total=0) }
            try { ContextCompat.startForegroundService(context,Intent(context,LinuxDownloadService::class.java).setAction(START)) }
            catch(e: Exception) { LinuxDownloadStore.mutable.update { it.copy(active=false,message="Téléchargement indisponible : ${e.message}") } }
        }
        fun cancel(context: Context) { context.startService(Intent(context,LinuxDownloadService::class.java).setAction(CANCEL)) }
    }
}
