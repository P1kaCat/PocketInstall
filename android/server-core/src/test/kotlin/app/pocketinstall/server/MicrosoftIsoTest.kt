package app.pocketinstall.server

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.nio.file.Files

class MicrosoftIsoTest {
    private val url = "https://software.download.prss.microsoft.com/db/windows.iso?t=temporary"
    @Test fun onlyMicrosoftHttpsIsoLinksAreAccepted() {
        assertTrue(MicrosoftIso.validUrl(url))
        for(value in listOf("http://download.microsoft.com/a.iso","https://download.microsoft.com.evil.org/a.iso",
            "https://download.microsoft.com@evil.org/a.iso","https://download.microsoft.com/a.exe","https://download.microsoft.com:8443/a.iso")) assertFalse(value,MicrosoftIso.validUrl(value))
    }
    private class Response(url: URL, private val data: ByteArray, private val code: Int = 200,
                           private val location: String? = null, private val length: Long = data.size.toLong()) : HttpURLConnection(url) {
        override fun connect() {} ; override fun disconnect() {} ; override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getContentLengthLong() = length
        override fun getInputStream() = ByteArrayInputStream(data)
        override fun getHeaderField(name: String) = if(name == "Location") location else null
    }
    @Test fun streamsCompleteImageAndRejectsTruncation() {
        val dir = Files.createTempDirectory("iso-download").toFile()
        try {
            val file = java.io.File(dir,"test.iso")
            val data = ByteArray(1048576) { 42 }
            var received = 0L
            MicrosoftIso.download(url,file,"test",{false},{},{bytes,_ -> received=bytes}) { Response(it,data) }
            assertEquals(data.size.toLong(),received); assertArrayEquals(data,file.readBytes())
            assertThrows(IllegalStateException::class.java) {
                MicrosoftIso.download(url,file,"test",{false},{},{_,_ ->}) { Response(it,byteArrayOf(1),length=1048576) }
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun redirectsCannotLeaveMicrosoftAndCancellationStopsBeforeConnection() {
        val dir = Files.createTempDirectory("iso-download").toFile()
        try {
            val file = java.io.File(dir,"test.iso")
            assertThrows(IllegalArgumentException::class.java) {
                MicrosoftIso.download(url,file,"test",{false},{},{_,_ ->}) { Response(it,byteArrayOf(),302,"https://evil.org/windows.iso") }
            }
            assertThrows(InterruptedException::class.java) {
                MicrosoftIso.download(url,file,"test",{true},{},{_,_ ->}) { error("Must not connect after cancellation") }
            }
        } finally { dir.deleteRecursively() }
    }
}
