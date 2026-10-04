package app.pocketinstall.server

import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class WinPeEntry(val name: String, val bytes: Long, val sha256: String)

/** Flat, bounded ZIP import into a NEW private directory. Never executes payloads. */
object WinPeBundle {
    val names = setOf("boot.wim", "boot.sdi", "BCD", "bootmgfw.efi", "wimboot", "snponly.efi")
    const val MAX_TOTAL = 2L * 1024 * 1024 * 1024
    private fun limit(name: String) = if (name == "boot.wim") MAX_TOTAL - 64 * 1024 * 1024 else 16L * 1024 * 1024

    fun extract(input: InputStream, directory: File, decode: (ByteArray) -> List<WinPeEntry>): List<WinPeEntry> {
        require(!directory.exists()) { "Le dossier d'import existe déjà." }
        check(directory.mkdirs()) { "Impossible de créer le dossier d'import." }
        try {
            val seen = mutableSetOf<String>()
            val actual = mutableMapOf<String, WinPeEntry>()
            var manifest: ByteArray? = null
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory && (name in names || name == "manifest.json") && seen.add(name)) {
                        "Archive invalide : fichiers inconnus, doublons ou sous-dossiers."
                    }
                    val max = if (name == "manifest.json") 65536L else limit(name)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var count = 0L
                    val output = File(directory, name)
                    output.outputStream().use { stream ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) error("Import interrompu.")
                            val n = zip.read(buffer)
                            if (n < 0) break
                            count += n; total += n
                            require(count <= max && total <= MAX_TOTAL) { "Bundle trop volumineux (maximum 2 Gio)." }
                            stream.write(buffer, 0, n); digest.update(buffer, 0, n)
                        }
                    }
                    require(count > 0) { "Fichier vide : $name" }
                    if (name == "manifest.json") manifest = output.readBytes()
                    else actual[name] = WinPeEntry(name, count, digest.digest().joinToString("") { "%02x".format(it) })
                    zip.closeEntry()
                }
            }
            require(actual.keys == names && manifest != null) { "Bundle incomplet : six fichiers et manifest.json requis." }
            val expected = decode(checkNotNull(manifest))
            verify(directory, expected, actual)
            return expected
        } catch (e: Exception) {
            directory.deleteRecursively()
            throw e
        }
    }

    fun verify(directory: File, expected: List<WinPeEntry>, actual: Map<String, WinPeEntry>? = null) {
        require(expected.size == names.size && expected.map { it.name }.toSet() == names) { "Manifeste incomplet ou dupliqué." }
        expected.forEach { entry ->
            require(entry.bytes in 1..limit(entry.name) && entry.sha256.matches(Regex("[a-f0-9]{64}"))) { "Manifeste invalide." }
            val file = File(directory, entry.name)
            require(file.isFile && file.length() == entry.bytes) { "Taille incorrecte : ${entry.name}" }
            val hash = actual?.get(entry.name)?.sha256 ?: file.inputStream().use { stream ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(65536)
                while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            require(hash == entry.sha256) { "SHA-256 incorrect : ${entry.name}" }
        }
        // Structural checks supplement hashes; they do not authenticate a publisher.
        File(directory, "boot.wim").inputStream().use { stream ->
            val magic = ByteArray(8); require(stream.read(magic) == 8 && magic.contentEquals(byteArrayOf(77,83,87,73,77,0,0,0))) { "boot.wim invalide." }
        }
        for (name in listOf("bootmgfw.efi", "snponly.efi")) {
            java.io.RandomAccessFile(File(directory, name), "r").use { file ->
                require(file.length() >= 64 && file.read() == 77 && file.read() == 90) { "EFI invalide : $name" }
                file.seek(60)
                val offset = Integer.reverseBytes(file.readInt()).toLong() and 0xffffffffL
                require(offset >= 64 && offset + 94 <= file.length()) { "En-tête EFI invalide : $name" }
                file.seek(offset)
                require(file.readInt() == 0x50450000 && java.lang.Short.reverseBytes(file.readShort()).toInt() and 0xffff == 0x8664) { "EFI x64 requis : $name" }
                file.seek(offset + 24)
                require(java.lang.Short.reverseBytes(file.readShort()).toInt() and 0xffff == 0x20b) { "EFI PE32+ requis." }
                file.seek(offset + 24 + 68)
                require(java.lang.Short.reverseBytes(file.readShort()).toInt() and 0xffff == 10) { "Application EFI requise." }
            }
        }
    }

    fun script(base: String): ByteArray {
        val uri = URI(base)
        require(uri.scheme == "http" && uri.rawQuery == null && uri.rawFragment == null && uri.userInfo == null)
        val address = uri.host ?: error("Adresse absente")
        require(address.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")))
        val ipv4 = java.net.InetAddress.getByName(address)
        require(Ipv4Subnet.isPrivate(ipv4) || ipv4.isLoopbackAddress)
        require(uri.port in 1..65535 && uri.path.matches(Regex("/[a-f0-9]{32}")))
        return """#!ipxe
echo PocketInstall - chargement WinPE
set base $base
kernel ${'$'}{base}/winpe/wimboot || goto failed
initrd --name bootmgfw.efi ${'$'}{base}/winpe/bootmgfw.efi bootmgfw.efi || goto failed
initrd --name BCD ${'$'}{base}/winpe/BCD BCD || goto failed
initrd --name boot.sdi ${'$'}{base}/winpe/boot.sdi boot.sdi || goto failed
initrd --name boot.wim ${'$'}{base}/winpe/boot.wim boot.wim || goto failed
initrd --name pocketinstall.cmd ${'$'}{base}/winpe/pocketinstall.cmd pocketinstall.cmd || goto failed
initrd --name pocketinstall.ps1 ${'$'}{base}/winpe/pocketinstall.ps1 pocketinstall.ps1 || goto failed
initrd --name winpeshl.ini ${'$'}{base}/winpe/winpeshl.ini winpeshl.ini || goto failed
boot || goto failed
:failed
echo PocketInstall - echec du chargement WinPE. Verifier le telephone et le LAN.
shell
""".toByteArray(Charsets.US_ASCII)
    }
}
