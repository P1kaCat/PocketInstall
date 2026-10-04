package app.pocketinstall.server

import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class WinPeHttpTest {
    @Test fun productionRouteAssemblyServesScriptAndEveryPayloadAcrossFreshSessions() {
        val dir = Files.createTempDirectory("winpe-routes").toFile()
        try {
            WinPeBundle.names.forEach { java.io.File(dir, it).writeBytes(byteArrayOf(1,2,3,4)) }
            val address = InetAddress.getByName("127.0.0.1") as Inet4Address
            var previous = ""
            repeat(2) {
                LocalHttpServer(address, Ipv4Subnet(address,8), emptyMap(),
                    resourceFactory = { base -> WinPeHttp.resources(dir, base) }, publicAliases = WinPeHttp.aliases,
                    requestedPort = 0, allowLoopbackForTests = true).use { server ->
                    server.start(); WinPeHttp.check(server, address, dir)
                    if (previous.isNotEmpty()) assertTrue(fetch(server, "/$previous/winpe/boot.ipxe").startsWith("HTTP/1.1 404"))
                    assertTrue(fetch(server, "/boot.ipxe").contains(server.baseUrl))
                    assertTrue(fetch(server, "/winpe/boot.wim").startsWith("HTTP/1.1 404"))
                    assertTrue(fetch(server, "/${server.session}/winpe/unknown").startsWith("HTTP/1.1 404"))
                    previous = server.session
                }
            }
            java.io.File(dir,"BCD").delete()
            assertThrows(IllegalArgumentException::class.java) { WinPeHttp.preflight(dir) }
        } finally { dir.deleteRecursively() }
    }
    @Test fun readingFailureIsNotMisreportedAsMissingRoute() {
        val address = InetAddress.getByName("127.0.0.1") as Inet4Address
        LocalHttpServer(address,Ipv4Subnet(address,8),mapOf("broken" to BootResource(1,"text/plain") { error("unreadable") }),
            requestedPort=0,allowLoopbackForTests=true).use { s -> s.start(); assertTrue(fetch(s,"/${s.session}/broken").startsWith("HTTP/1.1 500")) }
    }
    @Test fun automaticConfigurationHasNoSessionTokenAndRetries() {
        val config = WinPeHttp.freeboxConfig(InetAddress.getByName("192.168.0.35") as Inet4Address).toString(Charsets.US_ASCII)
        assertTrue(config.contains("chain --quiet --timeout 5000 http://192.168.0.35:8080/boot.ipxe")); assertTrue(config.contains("goto retry")); assertTrue(config.contains("[2J")); assertTrue(config.contains("Ctrl-B"))
    }
    @Test fun onlyRuntimeSignalAfterFullTransfersConfirmsWinPe() {
        fun event(name: String, phase: RequestPhase = RequestPhase.FINISHED, method: String = "GET", sent: Long = 10, peer: String = "192.168.0.9") =
            HttpEvent(1,0,peer,method,"winpe/$name",200,phase,10,sent)
        var progress = WinPeProgress().accept(event("boot.ipxe"))
        assertEquals(WinPeStage.IPXE,progress.stage)
        assertNotEquals(WinPeStage.STARTED,progress.accept(event("started")).stage)
        val needed = WinPeBundle.names - "snponly.efi" + (WinPeHttp.injected - "boot.ipxe")
        needed.forEach { name ->
            assertFalse(progress.accept(event(name,method="HEAD")).completed.contains(name))
            assertFalse(progress.accept(event(name,sent=9)).completed.contains(name))
            progress = progress.accept(event(name))
        }
        assertEquals(WinPeStage.SENT,progress.stage)
        assertEquals(WinPeStage.SENT,progress.accept(event("started",peer="192.168.0.10")).stage)
        assertEquals(WinPeStage.SENT,progress.accept(event("started",method="HEAD")).stage)
        assertEquals(WinPeStage.STARTED,progress.accept(event("started")).stage)
    }
    private fun fetch(server: LocalHttpServer, path: String): String = Socket("127.0.0.1",server.port).use { s ->
        s.soTimeout=3000; s.getOutputStream().write("GET $path HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
        s.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
    }
}

