package app.pocketinstall.server

import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket

/** The Android service, import preflight and VM use exactly this route assembly. */
object WinPeHttp {
    val aliases = mapOf("/boot.ipxe" to "winpe/boot.ipxe")
    val injected = setOf("boot.ipxe", "pocketinstall.cmd", "pocketinstall.ps1", "winpeshl.ini")
    fun resources(directory: File, base: String): Map<String, BootResource> {
        val script = WinPeBundle.script(base) // Generate once, before advertising any URL.
        val result = WinPeBundle.names.associate { name ->
            val file = File(directory, name)
            require(file.isFile && file.length() > 0) { "Fichier absent : $name" }
            "winpe/$name" to BootResource(file.length(), "application/octet-stream") { file.inputStream() }
        }.toMutableMap()
        fun text(name: String, bytes: ByteArray) { result["winpe/$name"] = BootResource(bytes.size.toLong(), "text/plain") { bytes.inputStream() } }
        text("boot.ipxe", script)
        // wimboot injects these files into X:\Windows\System32. Only WinPE runs this callback.
        text("winpeshl.ini", "[LaunchApps]\r\n%SYSTEMROOT%\\System32\\cmd.exe, /k %SYSTEMROOT%\\System32\\pocketinstall.cmd\r\n".toByteArray(Charsets.US_ASCII))
        text("pocketinstall.cmd", """@echo off
wpeinit
cls
echo PocketInstall boot successful (WinPE)
echo No installation or formatting has been requested.
echo This command prompt remains available.
ipconfig
if exist X:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe (
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File X:\Windows\System32\pocketinstall.ps1
) else (
  echo Phone notification unavailable in this bundle. Confirm boot on this screen.
)
""".replace("\n", "\r\n").toByteArray(Charsets.US_ASCII))
        val endpoint = java.net.URI(base)
        text("pocketinstall.ps1", """
§client = New-Object System.Net.Sockets.TcpClient
try {
  §client.ReceiveTimeout = 15000
  §client.SendTimeout = 15000
  §pending = §client.BeginConnect("${endpoint.host}", ${endpoint.port}, §null, §null)
  if (-not §pending.AsyncWaitHandle.WaitOne(15000)) { throw "Connection timed out" }
  §client.EndConnect(§pending)
  §stream = §client.GetStream()
  §request = [System.Text.Encoding]::ASCII.GetBytes("GET ${endpoint.rawPath}/winpe/started HTTP/1.1`r`nHost: ${endpoint.host}:${endpoint.port}`r`nConnection: close`r`n`r`n")
  §stream.Write(§request, 0, §request.Length)
  §reader = New-Object System.IO.StreamReader(§stream, [System.Text.Encoding]::ASCII)
  §response = §reader.ReadToEnd()
  if (-not §response.StartsWith("HTTP/1.1 200 ")) { throw "Notification refused" }
  Write-Host "WinPE startup reported to PocketInstall."
} catch {
  Write-Host "Boot confirmed on screen; phone notification unavailable."
  Write-Host §_.Exception.Message
} finally {
  §client.Close()
}
""".replace('§', '$').replace("\n", "\r\n").toByteArray(Charsets.US_ASCII))
        text("started", "WinPE startup signal received.\n".toByteArray(Charsets.US_ASCII))
        return result
    }
    fun freeboxConfig(address: Inet4Address): ByteArray {
        require(Ipv4Subnet.isPrivate(address) || address.isLoopbackAddress)
        return """#!ipxe
:retry
chain http://${address.hostAddress}:8080/boot.ipxe || goto waiting
exit
:waiting
echo PocketInstall - demarrer le serveur sur le telephone.
sleep 3
goto retry
""".toByteArray(Charsets.US_ASCII)
    }
    /** Real socket probes, including generated script and first/last byte of every payload. */
    fun check(server: LocalHttpServer, address: Inet4Address, directory: File) {
        val script = get(address, server.port, "/${server.session}/winpe/boot.ipxe")
        check(script.contentEquals(WinPeBundle.script(server.baseUrl))) { "Route boot.ipxe incohérente." }
        check(get(address, server.port, "/boot.ipxe").contentEquals(script)) { "Route automatique incohérente." }
        for (name in WinPeBundle.names) {
            val file = File(directory, name)
            for (offset in setOf(0L, file.length() - 1)) {
                val data = get(address, server.port, "/${server.session}/winpe/$name", "bytes=$offset-$offset", file.length())
                java.io.RandomAccessFile(file, "r").use { it.seek(offset); check(data.size == 1 && data[0].toInt() and 255 == it.read()) }
            }
        }
        for (name in injected - "boot.ipxe") check(get(address, server.port, "/${server.session}/winpe/$name").isNotEmpty())
    }
    private fun get(address: Inet4Address, port: Int, path: String, range: String? = null, length: Long? = null): ByteArray = Socket().use { socket ->
        socket.connect(java.net.InetSocketAddress(address, port), 3000); socket.soTimeout = 5000
        socket.getOutputStream().write(("GET $path HTTP/1.1\r\nHost: ${address.hostAddress}:$port\r\n" +
            (range?.let { "Range: $it\r\n" } ?: "") + "\r\n").toByteArray(Charsets.US_ASCII))
        val response = socket.getInputStream().readBytes()
        val text = response.toString(Charsets.ISO_8859_1)
        check(text.startsWith("HTTP/1.1 ${if (range == null) 200 else 206} ")) { "Route HTTP inaccessible : $path (${text.substringBefore("\r\n")})" }
        if (length != null) check(text.contains("/$length\r\n")) { "Longueur HTTP incorrecte : $path" }
        response.copyOfRange(text.indexOf("\r\n\r\n") + 4, response.size)
    }
    fun preflight(directory: File) {
        val address = InetAddress.getByName("127.0.0.1") as Inet4Address
        LocalHttpServer(address, Ipv4Subnet(address, 8), emptyMap(), resourceFactory = { resources(directory, it) },
            publicAliases = aliases, requestedPort = 0, allowLoopbackForTests = true).use { server ->
            server.start(); check(server, address, directory)
        }
    }
}
