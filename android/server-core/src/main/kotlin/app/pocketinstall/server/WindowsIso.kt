package app.pocketinstall.server

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** ISO9660 (including level-3 multi-extent files). UDF-only media fail explicitly. */
object WindowsIso {
    private data class Extent(val offset: Long, val size: Long)
    private data class Entry(val name: String, val directory: Boolean, val more: Boolean, val extent: Extent)
    private fun records(input: RandomAccessFile, extent: Extent): List<Entry> {
        require(extent.size in 1..1048576 && extent.offset >= 0 && extent.offset <= input.length() - extent.size)
        val bytes = ByteArray(extent.size.toInt()); input.seek(extent.offset); input.readFully(bytes)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val entries = mutableListOf<Entry>(); var at = 0
        while (at < bytes.size) {
            val len = bytes[at].toInt() and 255
            if (len == 0) { at = ((at / 2048) + 1) * 2048; continue }
            require(len >= 34 && at + len <= bytes.size)
            val nameLen = bytes[at + 32].toInt() and 255; require(33 + nameLen <= len)
            val sector = b.getInt(at + 2).toLong() and 0xffffffffL
            val size = b.getInt(at + 10).toLong() and 0xffffffffL
            val offset = sector * 2048
            require(offset <= input.length() - size && bytes[at + 1].toInt() == 0 && bytes[at + 26].toInt() == 0 && bytes[at + 27].toInt() == 0)
            val name = String(bytes,at+33,nameLen,Charsets.US_ASCII).substringBefore(';').uppercase()
            val flags = bytes[at + 25].toInt() and 255
            entries += Entry(name, flags and 2 != 0, flags and 128 != 0, Extent(offset,size)); at += len
        }
        return entries
    }
    fun extract(iso: File, destination: File) {
        try { extractIso9660(iso,destination) }
        catch(isoError: Exception) {
            if(Thread.currentThread().isInterrupted) throw isoError
            try { WindowsUdf.extract(iso,destination) }
            catch(udfError: Exception) { throw IllegalArgumentException("ISO non prise en charge ou incomplète. Extrais sources/install.wim ou install.esd puis importe ce fichier. ${udfError.message}",udfError) }
        }
    }
    private fun extractIso9660(iso: File, destination: File) {
        require(!destination.exists())
        try { RandomAccessFile(iso,"r").use { input ->
            require(input.length() <= WindowsImage.MAX_BYTES && input.length() >= 17 * 2048)
            var root: Extent? = null
            for (sector in 16..63) {
                val descriptor = ByteArray(2048); input.seek(sector * 2048L); input.readFully(descriptor)
                if (String(descriptor,1,5,Charsets.US_ASCII) != "CD001") break
                if (descriptor[0].toInt() == 1) {
                    val b = ByteBuffer.wrap(descriptor).order(ByteOrder.LITTLE_ENDIAN)
                    require(b.getShort(128).toInt() == 2048)
                    root = Extent((b.getInt(158).toLong() and 0xffffffffL) * 2048, b.getInt(166).toLong() and 0xffffffffL); break
                }
                if (descriptor[0].toInt() and 255 == 255) break
            }
            val rootDir = root ?: error("ISO UDF seule non prise en charge. Extrais sources/install.wim ou install.esd sur le téléphone, puis importe ce fichier.")
            val source = records(input,rootDir).singleOrNull { it.name == "SOURCES" && it.directory } ?: error("Dossier sources absent de l'ISO.")
            val entries = records(input,source.extent)
            val name = listOf("INSTALL.WIM","INSTALL.ESD").firstOrNull { candidate -> entries.any { it.name == candidate && !it.directory } }
                ?: error("install.wim/install.esd absent. Les fichiers SWM séparés ne sont pas pris en charge.")
            val first = entries.indexOfFirst { it.name == name }; val extents = mutableListOf<Extent>(); var n = first
            do { require(n < entries.size && entries[n].name == name && !entries[n].directory); extents += entries[n].extent } while(entries[n++].more)
            require(extents.sumOf { it.size } in 208..WindowsImage.MAX_BYTES)
            destination.outputStream().use { output ->
                val buffer = ByteArray(1048576)
                for (extent in extents) {
                    input.seek(extent.offset); var left = extent.size
                    while(left > 0) { if(Thread.currentThread().isInterrupted) error("Import interrompu."); val count = minOf(left,buffer.size.toLong()).toInt(); input.readFully(buffer,0,count); output.write(buffer,0,count); left -= count }
                }
            }
        } } catch(e: Exception) { destination.delete(); throw e }
    }
}
