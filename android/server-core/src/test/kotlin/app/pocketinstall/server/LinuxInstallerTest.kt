package app.pocketinstall.server

import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class LinuxInstallerTest {
    private fun fixture(): File {
        val dir=Files.createTempDirectory("linux-routes").toFile()
        val kernel=ByteArray(1024); "HdrS".toByteArray().copyInto(kernel,0x202)
        File(dir,"linux").writeBytes(kernel)
        val initrd=ByteArray(1024); initrd[0]=0x1f; initrd[1]=0x8b.toByte()
        File(dir,"initrd.gz").writeBytes(initrd)
        File(dir,"SHA256SUMS").writeText(LinuxInstaller.names.joinToString("\n") { "${LinuxInstaller.digest(File(dir,it))}  ./netboot/debian-installer/amd64/$it" })
        return dir
    }
    @Test fun verifiedFilesAndAutomaticAliasWorkForBothProfiles() {
        val dir=fixture()
        try {
            LinuxInstaller.verify(dir)
            LinuxProfile.entries.forEach { profile -> LinuxHttp.preflight(dir,profile) }
            File(dir,"linux").appendBytes(byteArrayOf(5))
            assertThrows(IllegalArgumentException::class.java) { LinuxInstaller.verify(dir) }
            File(dir,"SHA256SUMS").delete()
            assertThrows(IllegalArgumentException::class.java) { LinuxInstaller.verify(dir) }
        } finally {dir.deleteRecursively()}
    }
    @Test fun callbackCannotReplaceCompleteTransferAndInstallerSignal() {
        fun event(name: String,peer: String="192.168.0.26",method: String="GET",bytes: Long=10,status: Int=200)=HttpEvent(1,0,peer,method,"linux/$name",status,RequestPhase.FINISHED,10,bytes)
        var state=LinuxProgress()
        assertEquals(state,state.accept(event("started")))
        listOf("boot.ipxe","linux","initrd.gz").forEach { name ->
            assertFalse(state.accept(event(name,method="HEAD")).completed.contains(name))
            assertFalse(state.accept(event(name,bytes=5)).completed.contains(name))
            assertFalse(state.accept(event(name,status=206)).completed.contains(name))
            state=state.accept(event(name))
        }
        assertEquals(LinuxStage.SENT,state.stage)
        assertEquals(LinuxStage.SENT,state.accept(event("started")).stage)
        state=state.accept(event("preseed.cfg"))
        assertEquals(LinuxStage.SENT,state.stage)
        assertEquals(LinuxStage.SENT,state.accept(event("installed")).stage)
        assertEquals(LinuxStage.SENT,state.accept(event("started",peer="192.168.0.27")).stage)
        state=state.accept(event("started")); assertEquals(LinuxStage.STARTED,state.stage)
        assertEquals(LinuxStage.INSTALLED,state.accept(event("installed")).stage)
    }
    @Test fun profileUsesGuidedDiskAndAccountQuestionsAndDistinctTasks() {
        val base="http://192.168.0.35:8080/0123456789abcdef0123456789abcdef"
        LinuxProfile.entries.forEach { profile ->
            val preseed=LinuxInstaller.preseed(base,profile).toString(Charsets.US_ASCII)
            assertTrue(preseed.contains(profile.tasks)); assertTrue(preseed.contains("$base/linux/started"))
            assertFalse(preseed.contains("partman"));assertFalse(preseed.contains("password"));assertFalse(preseed.contains("install-recommends"))
            val script=LinuxInstaller.script(base).toString(Charsets.US_ASCII)
            assertTrue(script.contains("initrd=initrd.gz"));assertTrue(script.contains("url=$base/linux/preseed.cfg"))
        }
        assertThrows(IllegalArgumentException::class.java) { LinuxInstaller.hashes("0".repeat(64)+"  ./wrong/linux") }
        assertThrows(IllegalArgumentException::class.java) { LinuxInstaller.script("http://example.org/a") }
    }
}
