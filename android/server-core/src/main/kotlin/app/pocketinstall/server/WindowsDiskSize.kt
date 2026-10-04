package app.pocketinstall.server

/** A conservative capacity estimate, not a quota or a guarantee of future free space. */
object WindowsDiskSize {
    const val GIB = 1024L * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 512 * GIB
    const val TEMP_GIB = 10
    const val UPDATE_GIB = 16
    const val SCRATCH_GIB = 2
    fun minimumGiB(version: WindowsVersion, expandedBytes: Long, transferBytes: Long): Int {
        require(expandedBytes in 1..MAX_EXPANDED_BYTES) { "Taille de l’édition absente : choisis une taille manuelle ou importe une image avec ses métadonnées." }
        require(transferBytes in 208..WindowsImage.MAX_BYTES)
        val floor = if(version == WindowsVersion.WINDOWS_11) 64 * GIB else 32 * GIB
        val steady = expandedBytes + (TEMP_GIB + UPDATE_GIB) * GIB
        val peak = expandedBytes + transferBytes + SCRATCH_GIB * GIB
        val bytes = maxOf(floor,steady,peak)
        val rounded = ((bytes + 4 * GIB - 1) / (4 * GIB) * 4).toInt()
        require(rounded in 32..512) { "L’édition choisie dépasse les limites de taille prises en charge." }
        return rounded
    }
    fun selectedGiB(selection: WindowsSelection, info: WindowsImageInfo): Int {
        if(selection.storageLayout == StorageLayout.SINGLE) return selection.systemGiB
        val entry=info.selected(selection)
        val minimum=if(entry.expandedBytes > 0) minimumGiB(selection.version,entry.expandedBytes,info.bytes)
            else {
                require(!selection.autoSystemSize) { "Taille de l’édition absente : le dimensionnement automatique n’est pas disponible." }
                // Legacy images can still use manual sizing with the previous conservative bound.
                ((maxOf(64 * GIB,info.bytes + 58 * GIB) + GIB - 1) / GIB).toInt()
            }
        if(selection.autoSystemSize) return minimum
        require(selection.systemGiB >= minimum) { "Prévois au moins $minimum Gio pour cette édition et le transfert." }
        return selection.systemGiB
    }
}
