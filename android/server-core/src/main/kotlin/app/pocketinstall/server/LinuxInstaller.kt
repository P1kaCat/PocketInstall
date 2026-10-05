package app.pocketinstall.server

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class LinuxProfile(val label: String, val tasks: String) {
    DESKTOP("Debian 13 · bureau Xfce", "standard, xfce-desktop"),
    SERVER("Debian 13 · serveur", "standard, ssh-server")
}

/** Debian's official amd64 netboot images. No disk selection or erase is preseeded. */
object LinuxInstaller {
    const val SOURCE = "https://deb.debian.org/debian/dists/trixie/main/installer-amd64/current/images/"
    val names = setOf("linux", "initrd.gz")
    private const val PREFIX = "./netboot/debian-installer/amd64/"
    fun hashes(sums: String): Map<String, String> {
        require(sums.length <= 262144)
        val result = mutableMapOf<String, String>()
        sums.lineSequence().forEach { line ->
            val parts = line.trim().split(Regex("\\s+"), limit=2)
            if(parts.size == 2 && parts[1].startsWith(PREFIX)) {
                val name = parts[1].removePrefix(PREFIX)
                if(name in names) {
                    require(parts[0].matches(Regex("[a-fA-F0-9]{64}")) && name !in result) { "Empreintes Debian invalides" }
                    result[name] = parts[0].lowercase()
                }
            }
        }
        require(result.keys == names) { "Empreintes Debian manquantes" }
        return result
    }
    fun digest(file: File): String {
        val sha=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val buffer=ByteArray(65536); while(true) { val n=stream.read(buffer); if(n<0)break; sha.update(buffer,0,n) } }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }
    fun verify(directory: File) {
        val sums=File(directory,"SHA256SUMS")
        require(sums.isFile && sums.length() in 1..262144) { "Empreintes absentes" }
        hashes(sums.readText()).forEach { (name,hash) ->
            val file=File(directory,name)
            require(file.isFile && file.length() in 1024..134217728 && digest(file)==hash) { "Fichier Debian absent ou corrompu : $name" }
        }
        java.io.RandomAccessFile(File(directory,"linux"),"r").use { file ->
            file.seek(0x202); val header=ByteArray(4); file.readFully(header)
            require(header.contentEquals("HdrS".toByteArray())) { "Noyau Linux invalide" }
        }
        File(directory,"initrd.gz").inputStream().use { require(it.read()==0x1f && it.read()==0x8b) { "Initrd invalide" } }
    }
    fun download(directory: File, cancelled: () -> Boolean, connection: (HttpURLConnection) -> Unit,
                 progress: (String,Long,Long) -> Unit, approvedSizes: Map<String,Long>? = null) {
        require(directory.isDirectory)
        fun fetch(path: String, target: File, max: Long) {
            val conn=URL(SOURCE+path).openConnection() as HttpURLConnection
            connection(conn); conn.connectTimeout=15000; conn.readTimeout=15000; conn.instanceFollowRedirects=false
            conn.setRequestProperty("Accept-Encoding","identity")
            try {
                require(!cancelled()) { "Téléchargement annulé" }
                require(conn.responseCode==200) { "Debian : HTTP ${conn.responseCode}" }
                val total=conn.contentLengthLong
                if(approvedSizes != null) DownloadMetadata.checkApproved(total,approvedSizes.getValue(target.name))
                require(total <= max) { "Fichier trop volumineux" }
                conn.inputStream.use { input -> target.outputStream().use { output ->
                    val buffer=ByteArray(65536); var bytes=0L
                    while(true) {
                        if(cancelled()) throw InterruptedException()
                        val n=input.read(buffer); if(n<0)break
                        bytes+=n; require(bytes<=max) { "Fichier trop volumineux" }
                        output.write(buffer,0,n); progress(target.name,bytes,total)
                    }
                    require(bytes>0 && (total<0 || total==bytes)) { "Téléchargement incomplet" }
                } }
            } finally { conn.disconnect() }
        }
        fetch("SHA256SUMS",File(directory,"SHA256SUMS"),262144)
        val expected=hashes(File(directory,"SHA256SUMS").readText())
        names.forEach { name ->
            fetch("netboot/debian-installer/amd64/$name",File(directory,name),134217728)
            require(digest(File(directory,name))==expected.getValue(name)) { "Empreinte incorrecte : $name" }
        }
        verify(directory)
    }
    fun script(base: String): ByteArray {
        validateBase(base)
        return """#!ipxe
set esc:hex 1b
echo ${'$'}{esc:string}[2J${'$'}{esc:string}[H
 echo   POCKETINSTALL / DEBIAN 13
 echo   Chargement de l installateur...
imgfree
kernel $base/linux/linux initrd=initrd.gz auto=true priority=high netcfg/choose_interface=auto netcfg/get_hostname=pocketinstall netcfg/get_domain=local url=$base/linux/preseed.cfg || goto failed
initrd $base/linux/initrd.gz || goto failed
boot || goto failed
:failed
echo Le chargement a echoue. Verifie le telephone et le reseau.
prompt Appuie sur une touche pour ouvrir le diagnostic.
shell
""".toByteArray(Charsets.US_ASCII)
    }
    fun preseed(base: String, profile: LinuxProfile): ByteArray {
        validateBase(base)
        return """# PocketInstall: account and partition decisions remain on the PC.
d-i debian-installer/locale string fr_FR.UTF-8
d-i keyboard-configuration/xkb-keymap select fr
d-i netcfg/get_hostname string pocketinstall
d-i netcfg/get_domain string
d-i mirror/country string manual
d-i mirror/http/hostname string deb.debian.org
d-i mirror/http/directory string /debian
d-i mirror/http/proxy string
d-i mirror/suite string trixie
d-i passwd/root-login boolean false
d-i time/zone string Europe/Paris
d-i clock-setup/utc boolean true
tasksel tasksel/first multiselect ${profile.tasks}
d-i pkgsel/upgrade select safe-upgrade
popularity-contest popularity-contest/participate boolean false
d-i preseed/early_command string wget -q -T 5 -O /dev/null $base/linux/started || true
d-i preseed/late_command string wget -q -T 5 -O /dev/null $base/linux/installed || true
""".toByteArray(Charsets.US_ASCII)
    }
    private fun validateBase(base: String) {
        val uri=java.net.URI(base)
        require(uri.scheme=="http" && uri.port in 1..65535 && uri.rawPath.matches(Regex("/[a-f0-9]{32}")) && uri.rawQuery==null && uri.rawFragment==null && uri.userInfo==null)
        val address=java.net.InetAddress.getByName(uri.host)
        require(address is java.net.Inet4Address && (Ipv4Subnet.isPrivate(address) || address.isLoopbackAddress))
    }
}
