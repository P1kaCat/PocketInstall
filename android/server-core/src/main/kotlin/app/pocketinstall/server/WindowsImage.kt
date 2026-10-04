package app.pocketinstall.server

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

enum class WindowsVersion(val label: String) { WINDOWS_10("Windows 10"), WINDOWS_11("Windows 11") }
enum class WindowsEdition(val label: String, val editionId: String) { HOME("Home", "Core"), PRO("Pro", "Professional") }
enum class DebloatProfile(val label: String) { NONE("Aucun"), LIGHT("Léger"), CUSTOM("Personnalisé"), AUTO("Auto · selon le PC") }
data class WindowsSelection(
    val version: WindowsVersion = WindowsVersion.WINDOWS_11,
    val edition: WindowsEdition = WindowsEdition.HOME,
    val debloat: DebloatProfile = DebloatProfile.NONE,
    val removeClipchamp: Boolean = false,
    val removeSolitaire: Boolean = false,
    val removeNews: Boolean = false,
    val removeWeather: Boolean = false,
)
data class WindowsImageEntry(val index: Int, val name: String, val editionId: String, val build: Int, val architecture: Int) {
    fun matches(selection: WindowsSelection): Boolean = architecture == 9 && editionId == selection.edition.editionId &&
        if (selection.version == WindowsVersion.WINDOWS_11) build >= 22000 else build in 10240..21999
}
data class WindowsImageInfo(val bytes: Long, val sha256: String, val entries: List<WindowsImageEntry>) {
    fun selected(selection: WindowsSelection): WindowsImageEntry = entries.filter { it.matches(selection) }.singleOrNull()
        ?: error("Cette image ne contient pas exactement une édition ${selection.version.label} ${selection.edition.label} x64.")
}

/** Only bounded, uncompressed XML metadata is parsed. DISM revalidates the actual image in WinPE. */
object WindowsImage {
    const val MAX_BYTES = 16L * 1024 * 1024 * 1024
    fun inspect(file: File): List<WindowsImageEntry> = RandomAccessFile(file, "r").use { input ->
        require(input.length() in 208..MAX_BYTES) { "Image Windows vide ou trop volumineuse (16 Gio maximum)." }
        val header = ByteArray(208); input.readFully(header)
        val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        require(header.copyOfRange(0, 8).contentEquals(byteArrayOf(77,83,87,73,77,0,0,0))) { "Fichier WIM/ESD attendu." }
        require(h.getInt(8) == 208 && h.getInt(12) in setOf(0x10d00, 0xe00)) { "Format WIM non pris en charge." }
        require(h.getShort(40).toInt() == 1 && h.getShort(42).toInt() == 1 && h.getInt(16) and 0x78 == 0) { "Image séparée, incomplète ou en cours d'écriture." }
        val count = h.getInt(44); require(count in 1..64)
        val sizeFlags = h.getLong(72)
        val size = sizeFlags and 0x00ffffffffffffffL
        val flags = (sizeFlags ushr 56).toInt()
        val offset = h.getLong(80)
        require(flags and 0x14 == 0 && size in 2..1048576 && h.getLong(88) == size && offset >= 208 && offset <= input.length() - size) { "Métadonnées WIM invalides ou compressées." }
        val xmlBytes = ByteArray(size.toInt()); input.seek(offset); input.readFully(xmlBytes)
        val xml = xmlBytes.toString(Charsets.UTF_16LE).removePrefix("\uFEFF")
        require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { "Déclarations XML interdites." }
        val factory = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false; isXIncludeAware = false }
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
        require(document.documentElement.tagName == "WIM")
        fun Element.child(tag: String): Element? = (0 until childNodes.length).map { childNodes.item(it) }
            .filterIsInstance<Element>().singleOrNull { it.tagName == tag }
        fun Element.value(tag: String) = child(tag)?.textContent.orEmpty()
        val nodes = document.documentElement.getElementsByTagName("IMAGE")
        require(nodes.length == count)
        val entries = (0 until nodes.length).map { n ->
            val image = nodes.item(n) as Element
            val windows = image.child("WINDOWS") ?: error("Image sans métadonnées Windows.")
            val version = windows.child("VERSION") ?: error("Version Windows absente.")
            WindowsImageEntry(image.getAttribute("INDEX").toInt(), image.value("NAME").take(120),
                windows.value("EDITIONID"), version.value("BUILD").toInt(), windows.value("ARCH").toInt())
        }
        require(entries.map { it.index }.toSet() == (1..count).toSet()) { "Index Windows dupliqués ou incohérents." }
        require(entries.any { it.architecture == 9 && it.editionId in setOf("Core", "Professional") && it.build >= 10240 }) { "Aucune édition Home/Pro x64 installable dans cette image." }
        entries
    }
    fun hash(file: File): String = file.inputStream().use { stream ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(1048576)
        while (true) { if (Thread.currentThread().isInterrupted) error("Vérification interrompue."); val n = stream.read(buffer); if (n < 0) break; digest.update(buffer,0,n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
