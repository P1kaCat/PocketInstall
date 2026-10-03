package app.pocketinstall.server

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WinPeBundleTest {
    private fun efi(): ByteArray = ByteArray(256).also {
        val b = ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(0, 0x5a4d.toShort()); b.putInt(60, 64); b.putInt(64, 0x4550)
        b.putShort(68, 0x8664.toShort()); b.putShort(88, 0x20b); b.putShort(156, 10)
    }
    private fun files() = WinPeBundle.names.associateWith {
        when (it) { "boot.wim" -> byteArrayOf(77,83,87,73,77,0,0,0,1)
            "bootmgfw.efi", "snponly.efi" -> efi()
            else -> byteArrayOf(1,2,3) }
    }
    private fun manifest(files: Map<String, ByteArray>) = files.map { (name, bytes) ->
        WinPeEntry(name, bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    }
    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            (files + ("manifest.json" to byteArrayOf(1))).forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
    private fun importing(files: Map<String, ByteArray>, expected: List<WinPeEntry>, success: Boolean) {
        val parent = Files.createTempDirectory("winpe-test").toFile()
        val destination = java.io.File(parent, "bundle")
        try {
            if (success) {
                WinPeBundle.extract(ByteArrayInputStream(zip(files)), destination) { expected }
                WinPeBundle.verify(destination, expected)
                assertTrue(destination.isDirectory)
            } else {
                assertThrows(IllegalArgumentException::class.java) {
                    WinPeBundle.extract(ByteArrayInputStream(zip(files)), destination) { expected }
                }
                assertFalse(destination.exists())
            }
        } finally { parent.deleteRecursively() }
    }
    @Test fun importsAndRechecksCompleteBundle() { val f = files(); importing(f, manifest(f), true) }
    @Test fun rejectsTraversalAndDeletesPartialImport() { val f = files(); importing(f + ("../escape" to byteArrayOf(1)), manifest(f), false) }
    @Test fun rejectsMissingResources() { val f = files(); importing(f - "BCD", manifest(f), false) }
    @Test fun rejectsChangedBytes() { val f = files(); importing(f + ("BCD" to byteArrayOf(3,2,1)), manifest(f), false) }
    @Test fun rejectsDuplicateManifestNames() { val f = files(); importing(f, manifest(f).dropLast(1) + manifest(f).first(), false) }
    @Test fun rejectsWrongArchitectureDespiteMatchingHashes() {
        val f = files().toMutableMap(); f["snponly.efi"] = efi().also { it[68] = 0x4c; it[69] = 1 }
        importing(f, manifest(f), false)
    }
    @Test fun rejectsOversizedManifestBeforeDecode() {
        val parent = Files.createTempDirectory("winpe-limit").toFile()
        try {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry("manifest.json")); it.write(ByteArray(65537)) }
            assertThrows(IllegalArgumentException::class.java) {
                WinPeBundle.extract(ByteArrayInputStream(out.toByteArray()), java.io.File(parent, "bundle")) { error("must not decode") }
            }
        } finally { parent.deleteRecursively() }
    }
    @Test fun scriptUsesExactSessionAndFailureConsole() {
        val base = "http://192.168.0.35:8080/" + "a".repeat(32)
        val script = WinPeBundle.script(base).toString(Charsets.US_ASCII)
        assertTrue(script.startsWith("#!ipxe\n")); assertTrue(script.contains("set base $base\n")); assertTrue(script.contains("/winpe/boot.wim boot.wim"))
        assertTrue(script.endsWith("\nshell\n")); assertEquals(WinPeBundle.script(base.replace('a','b')).size, script.length)
        for (bad in listOf("http://8.8.8.8:8080/" + "a".repeat(32), base + "\nreboot", base + "?x=1")) {
            assertThrows(Exception::class.java) { WinPeBundle.script(bad) }
        }
    }
    @Test fun servesGeneratedScriptAndWimRangeInSameSession() {
        val address = java.net.InetAddress.getByName("127.0.0.1") as java.net.Inet4Address
        lateinit var server: LocalHttpServer
        // Keep the fixed HTTP port length identical, as in the Android service.
        val sample = WinPeBundle.script("http://127.0.0.1:8080/" + "0".repeat(32))
        val resources = mapOf(
            "winpe/boot.ipxe" to BootResource(sample.size.toLong(), "text/plain") {
                WinPeBundle.script("http://127.0.0.1:8080/${server.session}").inputStream()
            },
            "winpe/boot.wim" to BootResource(9, "application/octet-stream") { files().getValue("boot.wim").inputStream() }
        )
        server = LocalHttpServer(address, Ipv4Subnet(address, 8), resources, requestedPort = 0, allowLoopbackForTests = true)
        server.use { s ->
            s.start()
            fun fetch(name: String, range: String = "") = java.net.Socket(address, s.port).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write("GET /${s.session}/winpe/$name HTTP/1.1\r\nHost: localhost\r\n$range\r\n".toByteArray())
                socket.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
            }
            val script = fetch("boot.ipxe")
            assertTrue(script.startsWith("HTTP/1.1 200")); assertTrue(script.contains("Content-Length: ${sample.size}"))
            assertTrue(script.substringAfter("\r\n\r\n").contains(s.session))
            val range = fetch("boot.wim", "Range: bytes=0-4\r\n")
            assertTrue(range.startsWith("HTTP/1.1 206")); assertEquals("MSWIM", range.substringAfter("\r\n\r\n"))
            assertTrue(fetch("unknown.wim").startsWith("HTTP/1.1 404"))
        }
    }
}
