package app.pocketinstall

import app.pocketinstall.server.HttpEvent
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
    val message: String = "Démarre une session pour le test EFI.",
    val usbMode: Boolean = false,
)
object ServerStore {
    internal val mutable = MutableStateFlow(ServerSnapshot())
    val state = mutable.asStateFlow()
}
