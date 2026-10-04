package app.pocketinstall.server

import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket

/** The Android service, import preflight and VM use exactly this route assembly. */
object WinPeHttp {
    val aliases = mapOf("/boot.ipxe" to "winpe/boot.ipxe")
    val injected = setOf("boot.ipxe", "pocketinstall.cmd", "pocketinstall.ps1", "winpeshl.ini", "install.ps1", "install-plan.json")
    fun resources(directory: File, base: String, installPlan: ByteArray = "{\"enabled\":false}".toByteArray()): Map<String, BootResource> {
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
title PocketInstall - Installation Windows
color 0B
cls
echo.
echo   POCKETINSTALL
 echo   Installation Windows depuis ton telephone
 echo   -----------------------------------------
echo.
echo   [1/3] Initialisation de WinPE et du reseau...
wpeinit > X:\PocketInstall-network.log 2>&1
if exist X:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe (
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File X:\Windows\System32\pocketinstall.ps1
) else (
  echo PowerShell absent. WinPE a demarre, mais l installation ne peut pas continuer.
)
""".replace("\n", "\r\n").toByteArray(Charsets.US_ASCII))
        val endpoint = java.net.URI(base)
        text("install-plan.json", installPlan)
        val installer = checkNotNull(WinPeHttp::class.java.getResourceAsStream("/Install-Windows.ps1")) { "Installateur absent." }.use { it.readBytes() }
        text("install.ps1", installer)
        text("pocketinstall.ps1", """
§ErrorActionPreference = "Stop"
§network = Start-Process -FilePath wpeutil.exe -ArgumentList InitializeNetwork -PassThru -NoNewWindow -RedirectStandardOutput X:\PocketInstall-network-init.log -RedirectStandardError X:\PocketInstall-network-error.log
if (-not §network.WaitForExit(45000)) { §network.Kill(); Write-Host "DHCP encore indisponible; nouvelles tentatives..." }
Write-Host "  [2/3] Connexion au telephone..."
§reported = §false
for (§attempt = 0; §attempt -lt 12; §attempt++) {
  §client = New-Object System.Net.Sockets.TcpClient
  try {
    §client.ReceiveTimeout = 5000; §client.SendTimeout = 5000
    §pending = §client.BeginConnect("${endpoint.host}", ${endpoint.port}, §null, §null)
    if (-not §pending.AsyncWaitHandle.WaitOne(3000)) { throw "Connexion indisponible" }
    §client.EndConnect(§pending)
    §stream = §client.GetStream()
    §request = [System.Text.Encoding]::ASCII.GetBytes("GET ${endpoint.rawPath}/winpe/started HTTP/1.1`r`nHost: ${endpoint.host}:${endpoint.port}`r`nConnection: close`r`n`r`n")
    §stream.Write(§request, 0, §request.Length)
    §reader = New-Object System.IO.StreamReader(§stream, [System.Text.Encoding]::ASCII)
    §response = §reader.ReadToEnd()
    if (§response.StartsWith("HTTP/1.1 404 ")) { throw "SESSION_EXPIRED" }
    if (-not §response.StartsWith("HTTP/1.1 200 ")) { throw "Notification refusee" }
    §reported = §true; break
  } catch {
    §failure = §_.Exception.Message
    if (§failure -eq "SESSION_EXPIRED") { break }
  } finally { §client.Close() }
  Write-Progress -Activity "Connexion au telephone" -Status "Tentative §(§attempt+1) sur 12" -PercentComplete ([int](100*(§attempt+1)/12))
  Start-Sleep -Seconds 3
}
Write-Progress -Activity "Connexion au telephone" -Completed
if (-not §reported) {
  Write-Host "WinPE demarre, mais le telephone n'est pas joignable: §failure"
  Write-Host "Garde la meme session serveur ouverte puis redemarre le PC en PXE. Aucun disque modifie."
  return
}
Write-Host "  [3/3] PC connecte. Preparation de l installation..."
& X:\Windows\System32\install.ps1 -BaseUrl "$base"
""".replace('§', '$').replace("\n", "\r\n").toByteArray(Charsets.US_ASCII))
        text("started", "WinPE startup signal received.\n".toByteArray(Charsets.US_ASCII))
        return result
    }
    fun freeboxConfig(address: Inet4Address): ByteArray {
        require(Ipv4Subnet.isPrivate(address) || address.isLoopbackAddress)
        return """#!ipxe
# iPXE console sequences: https://ipxe.org/cmd/set
set esc:hex 1b
set screen ${'$'}{esc:string}[2J${'$'}{esc:string}[H
:retry
echo ${'$'}{screen}
echo
 echo   POCKETINSTALL / DEMARRAGE RESEAU
 echo   --------------------------------
 echo   En attente du telephone...
 echo   Ouvre PocketInstall et demarre le serveur.
echo
chain --quiet --timeout 5000 http://${address.hostAddress}:8080/boot.ipxe || goto waiting
exit
:waiting
prompt --key 0x02 --timeout 3000 Ctrl-B : diagnostic && shell ||
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
    fun checkImage(server: LocalHttpServer, address: Inet4Address, file: File) {
        require(file.isFile && file.length() in 208..WindowsImage.MAX_BYTES)
        for(offset in setOf(0L,file.length()-1)) {
            val data = get(address,server.port,"/${server.session}/install/image.wim","bytes=$offset-$offset",file.length())
            java.io.RandomAccessFile(file,"r").use { input -> input.seek(offset); check(data.size == 1 && data[0].toInt() and 255 == input.read()) }
        }
    }
    fun preflight(directory: File) {
        val address = InetAddress.getByName("127.0.0.1") as Inet4Address
        LocalHttpServer(address, Ipv4Subnet(address, 8), emptyMap(), resourceFactory = { resources(directory, it) },
            publicAliases = aliases, requestedPort = 0, allowLoopbackForTests = true).use { server ->
            server.start(); check(server, address, directory)
        }
    }
}

