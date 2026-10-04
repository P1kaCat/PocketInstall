package app.pocketinstall.server

import org.junit.Assert.*
import org.junit.Test

class WindowsDiskSizeTest {
    private val g=WindowsDiskSize.GIB
    @Test fun automaticSizingUsesEditionAndHeadroomInsteadOf128GiB() {
        assertEquals(64,WindowsDiskSize.minimumGiB(WindowsVersion.WINDOWS_11,20*g,5*g))
        assertEquals(48,WindowsDiskSize.minimumGiB(WindowsVersion.WINDOWS_10,20*g,5*g))
        assertEquals(64,WindowsDiskSize.minimumGiB(WindowsVersion.WINDOWS_10,35*g,6*g))
        assertEquals(76,WindowsDiskSize.minimumGiB(WindowsVersion.WINDOWS_11,50*g,16*g))
        val info=WindowsImageInfo(5*g,"a".repeat(64),listOf(
            WindowsImageEntry(1,"Windows 10 Home","Core",19045,9,20*g),
            WindowsImageEntry(2,"Windows 10 Pro","Professional",19045,9,35*g)))
        assertEquals(48,WindowsDiskSize.selectedGiB(WindowsSelection(version=WindowsVersion.WINDOWS_10),info))
        assertEquals(64,WindowsDiskSize.selectedGiB(WindowsSelection(version=WindowsVersion.WINDOWS_10,edition=WindowsEdition.PRO),info))
    }
    @Test fun refusesUnknownOrOversizedMetadataAndTooSmallManualPartition() {
        for(bytes in listOf(-1L,0L,Long.MAX_VALUE,512*g)) assertThrows(IllegalArgumentException::class.java) {
            WindowsDiskSize.minimumGiB(WindowsVersion.WINDOWS_11,bytes,5*g)
        }
        val info=WindowsImageInfo(5*g,"a".repeat(64),listOf(WindowsImageEntry(1,"Home","Core",26100,9,30*g)))
        assertThrows(IllegalArgumentException::class.java) { WindowsDiskSize.selectedGiB(WindowsSelection(autoSystemSize=false,systemGiB=48),info) }
        assertEquals(80,WindowsDiskSize.selectedGiB(WindowsSelection(autoSystemSize=false,systemGiB=80),info))
        val legacy=info.copy(entries=listOf(info.entries.single().copy(expandedBytes=0)))
        assertThrows(IllegalArgumentException::class.java) { WindowsDiskSize.selectedGiB(WindowsSelection(),legacy) }
        assertEquals(96,WindowsDiskSize.selectedGiB(WindowsSelection(autoSystemSize=false,systemGiB=96),legacy))
    }
}
