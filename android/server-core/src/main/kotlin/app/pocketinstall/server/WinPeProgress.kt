package app.pocketinstall.server

enum class WinPeStage(val label: String) {
    WAITING("En attente du PC"), DETECTED("PC détecté"), IPXE("iPXE connecté"),
    LOADING("Chargement WinPE"), SENT("WinPE envoyé · démarrage à confirmer"),
    STARTED("WinPE démarré · signal reçu du PC")
}
data class WinPeProgress(
    val peer: String = "", val stage: WinPeStage = WinPeStage.WAITING,
    val completed: Set<String> = emptySet(), val wimBytes: Long = 0, val wimTotal: Long = 0,
    val error: String = "",
) {
    fun accept(event: HttpEvent): WinPeProgress {
        if (event.method != "GET" || !event.resource.startsWith("winpe/") || (peer.isNotEmpty() && peer != event.peer)) return this
        if (event.status !in listOf(200, 206)) return copy(error = "${event.resource} : HTTP ${event.status}. Voir le diagnostic.")
        var next = if (peer.isEmpty()) copy(peer = event.peer, stage = WinPeStage.DETECTED) else this
        val name = event.resource.removePrefix("winpe/")
        val full = event.phase == RequestPhase.FINISHED && event.status == 200 && event.sentBytes == event.expectedBytes
        if (name == "boot.ipxe" && full && next.stage.ordinal < WinPeStage.IPXE.ordinal) next = next.copy(stage = WinPeStage.IPXE, error = "")
        if (name in WinPeBundle.names || name in WinPeHttp.injected - "boot.ipxe") {
            if (next.stage.ordinal < WinPeStage.LOADING.ordinal) next = next.copy(stage = WinPeStage.LOADING, error = "")
            if (full) next = next.copy(completed = next.completed + name)
            if (name == "boot.wim") next = next.copy(wimBytes = event.sentBytes, wimTotal = event.expectedBytes)
            if (event.phase == RequestPhase.FINISHED && !full && event.status == 200) next = next.copy(error = "Transfert interrompu : $name. Redémarre le PC en PXE.")
        }
        val required = WinPeBundle.names - "snponly.efi" + (WinPeHttp.injected - "boot.ipxe")
        if (next.completed.containsAll(required) && next.stage.ordinal < WinPeStage.SENT.ordinal) next = next.copy(stage = WinPeStage.SENT)
        // A download, HEAD or partial transfer must never count as a WinPE startup signal.
        if (name == "started" && full && next.completed.containsAll(required)) next = next.copy(stage = WinPeStage.STARTED, error = "")
        return next
    }
}
