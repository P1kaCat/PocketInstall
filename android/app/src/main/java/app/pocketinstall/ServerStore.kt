package app.pocketinstall

import app.pocketinstall.server.WinPeProgress
import app.pocketinstall.server.HttpEvent
import app.pocketinstall.server.TftpEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ServerStatus { STOPPED, STARTING, RUNNING, ERROR }
data class ServerSnapshot(
    val status: ServerStatus = ServerStatus.STOPPED,
    val ip: String = "—",
    val url: String = "",
    val subnet: String = "",
    val expiresAt: Long = 0,
    val requests: Long = 0,
    val clientsSeen: Int = 0,
    val events: List<HttpEvent> = emptyList(),
    val message: String = "Importe l’environnement Windows PE, puis démarre le serveur.",
    val winPeMode: Boolean = false,
    val winPeProgress: WinPeProgress = WinPeProgress(),
    val importingWinPe: Boolean = false,
    val loaderUrl: String = "",
    val usbMode: Boolean = false,
    val pxeMode: Boolean = false,
    val tftpPort: Int = 0,
    val bootFilename: String = "",
    val tftpMessage: String = "",
    val tftpEvents: List<TftpEvent> = emptyList(),
)
object ServerStore {
    internal val mutable = MutableStateFlow(ServerSnapshot())
    val state = mutable.asStateFlow()
}
