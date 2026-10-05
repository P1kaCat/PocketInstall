package app.pocketinstall.server

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class DownloadMetadataTest {
    private val source = "https://download.microsoft.com/windows.iso"
    private class Headers(url: URL, val status: Int = 200, val bytes: Long = 1024,
        val redirect: String? = null) : HttpURLConnection(url) {
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentLengthLong() = bytes
        override fun getHeaderField(name: String) = if(name=="Location") redirect else null
        override fun getInputStream(): java.io.InputStream = error("Preflight must never read an asset body")
    }
    @Test fun discoversSizeUsingHeadWithoutDownloadingAndClosesConnection() {
        val response = Headers(URL(source))
        assertEquals(1024L,DownloadMetadata.size(source,MicrosoftIso::validUrl,4096,open={response}))
        assertEquals("HEAD",response.requestMethod)
        assertTrue(response.closed)
    }
    @Test fun rejectsUnknownOversizedAndMissingAssets() {
        for(response in listOf(Headers(URL(source),bytes=-1),Headers(URL(source),bytes=4097),Headers(URL(source),status=404))) {
            assertThrows(Exception::class.java) { DownloadMetadata.size(source,MicrosoftIso::validUrl,4096,open={response}) }
            assertTrue(response.closed)
        }
    }
    @Test fun validatesRedirectBeforeOpeningIt() {
        var connections = 0
        assertThrows(IllegalArgumentException::class.java) {
            DownloadMetadata.size(source,MicrosoftIso::validUrl,4096,open={connections++;Headers(it,status=302,redirect="https://evil.example/windows.iso")})
        }
        assertEquals(1,connections)
    }
    @Test fun changedSizeRequiresNewConsentBeforePayloadTransfer() {
        DownloadMetadata.checkApproved(1024,1024)
        assertThrows(IllegalArgumentException::class.java) {DownloadMetadata.checkApproved(2048,1024)}
        assertThrows(IllegalArgumentException::class.java) {DownloadMetadata.checkApproved(1024,0)}
        val directory = java.nio.file.Files.createTempDirectory("consent").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                MicrosoftIso.download(source,java.io.File(directory,"windows.iso"),"test",{false},{},{_,_->},approvedBytes=1048576,
                    open={Headers(it,bytes=2097152)})
            }
            assertFalse(java.io.File(directory,"windows.iso").exists())
        } finally {directory.deleteRecursively()}
    }
}
