package app.pocketinstall.server

enum class LinuxStage(val label: String) {
    WAITING("En attente du PC"), IPXE("iPXE connecté"), LOADING("Chargement Debian"),
    SENT("Fichiers envoyés · démarrage à confirmer"), STARTED("Installateur démarré · continue sur le PC"),
    INSTALLED("Installation terminée · premier démarrage à confirmer sur le PC")
}
data class LinuxProgress(val peer: String="", val stage: LinuxStage=LinuxStage.WAITING,
    val completed: Set<String> = emptySet(), val bytes: Long=0, val total: Long=0, val error: String="") {
    fun accept(event: HttpEvent): LinuxProgress {
        if(event.method!="GET" || !event.resource.startsWith("linux/") || (peer.isNotEmpty() && peer!=event.peer))return this
        val name=event.resource.removePrefix("linux/")
        if(name !in setOf("boot.ipxe","linux","initrd.gz","preseed.cfg","started","installed")) return this
        if(event.status !in setOf(200,206))return copy(error="Debian : HTTP ${event.status}. Consulte le diagnostic.")
        val full=event.phase==RequestPhase.FINISHED && event.status==200 && event.sentBytes==event.expectedBytes
        // Callback probes must never claim a PC or bypass the boot payload sequence.
        if(name in setOf("started","installed") && peer.isEmpty())return this
        var next=if(peer.isEmpty())copy(peer=event.peer) else this
        if(name=="boot.ipxe" && full && next.stage<LinuxStage.IPXE)next=next.copy(stage=LinuxStage.IPXE,error="")
        if(name in LinuxInstaller.names) {
            if(next.stage<LinuxStage.LOADING)next=next.copy(stage=LinuxStage.LOADING,error="")
            if(name=="initrd.gz")next=next.copy(bytes=event.sentBytes,total=event.expectedBytes)
            if(event.phase==RequestPhase.FINISHED && !full && event.status==200)next=next.copy(error="Transfert Debian interrompu. Redémarre en PXE.")
        }
        if(full && name in LinuxInstaller.names + setOf("boot.ipxe","preseed.cfg")) next=next.copy(completed=next.completed+name)
        if(next.completed.containsAll(LinuxInstaller.names + "boot.ipxe") && next.stage<LinuxStage.SENT)next=next.copy(stage=LinuxStage.SENT)
        if(name=="started" && full && next.completed.containsAll(LinuxInstaller.names + setOf("boot.ipxe","preseed.cfg")) && next.stage<LinuxStage.STARTED)
            next=next.copy(stage=LinuxStage.STARTED,error="")
        if(name=="installed" && full && next.stage==LinuxStage.STARTED)next=next.copy(stage=LinuxStage.INSTALLED,error="")
        return next
    }
}
