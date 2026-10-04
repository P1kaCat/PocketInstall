package app.pocketinstall.server

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Read-only ECMA-167 UDF physical partitions, as used by Microsoft installation DVDs.
 * No VAT, sparing, fragmented allocation-descriptor continuations or writes to the ISO.
 */
object WindowsUdf {
    private class Reader(val input: RandomAccessFile) {
        private var partitionStart = 0L
        private var partitionBlocks = 0L
        private fun u16(b: ByteArray, n: Int) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getShort(n).toInt() and 65535
        private fun u32(b: ByteArray, n: Int) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(n).toLong() and 0xffffffffL
        private fun u64(b: ByteArray, n: Int) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getLong(n).also { require(it >= 0) }
        private fun bytes(offset: Long, count: Int): ByteArray {
            require(offset >= 0 && count >= 0 && offset <= input.length() - count)
            return ByteArray(count).also { input.seek(offset); input.readFully(it) }
        }
        private fun tag(b: ByteArray): Int {
            require(b.size >= 16)
            require((b.take(16).mapIndexed { n, value -> if(n == 4) 0 else value.toInt() and 255 }.sum() and 255) == (b[4].toInt() and 255)) { "Descripteur UDF corrompu." }
            return u16(b,0)
        }
        private fun physical(block: Long, length: Long): Long {
            require(block >= 0 && length >= 0 && block < partitionBlocks && length <= (partitionBlocks-block)*2048)
            val offset = (partitionStart+block)*2048
            require(offset <= input.length()-length)
            return offset
        }
        private fun longAd(b: ByteArray, at: Int): Long {
            require(u16(b,at+8) == 0 && u32(b,at) ushr 30 == 0L)
            val block = u32(b,at+4); physical(block,u32(b,at)); return block
        }
        private data class FileData(val directory: Boolean, val length: Long, val extents: List<Pair<Long,Long>>, val inline: ByteArray?)
        private fun file(block: Long): FileData {
            val b = bytes(physical(block,2048),2048)
            val kind = tag(b); require(kind in setOf(261,266)) { "Entrée UDF non prise en charge." }
            val type = b[27].toInt() and 255; require(type in setOf(4,5))
            val length = u64(b,56); require(length in 0..WindowsImage.MAX_BYTES)
            val eaAt = if(kind == 261) 168 else 208
            val adAt = eaAt+8+u32(b,eaAt).toInt()
            val adLength = u32(b,eaAt+4).toInt()
            require(adAt >= eaAt+8 && adLength >= 0 && adAt <= b.size-adLength)
            val allocation = u16(b,34) and 7
            if(allocation == 3) {
                require(length <= adLength)
                return FileData(type==4,length,emptyList(),b.copyOfRange(adAt,adAt+length.toInt()))
            }
            require(allocation in setOf(0,1)) { "Allocations UDF non prises en charge." }
            val stride = if(allocation==0) 8 else 16
            require(adLength % stride == 0)
            val extents = (adAt until adAt+adLength step stride).map { at ->
                val flagsLength = u32(b,at); require(flagsLength ushr 30 == 0L) { "Image UDF fragmentée ou creuse non prise en charge." }
                if(allocation==1) require(u16(b,at+8)==0)
                val size = flagsLength and 0x3fffffff; physical(u32(b,at+4),size) to size
            }
            require(extents.sumOf { it.second } >= length)
            return FileData(type==4,length,extents,null)
        }
        private fun content(file: FileData): ByteArray {
            require(file.length <= 1048576)
            file.inline?.let { return it }
            val output = java.io.ByteArrayOutputStream(); var remaining=file.length
            file.extents.forEach { (offset,size) -> val count=minOf(size,remaining).toInt(); output.write(bytes(offset,count)); remaining-=count }
            require(remaining == 0L); return output.toByteArray()
        }
        private fun children(directory: FileData): Map<String,Long> {
            require(directory.directory)
            val b=content(directory); var at=0; val result=mutableMapOf<String,Long>()
            while(at < b.size) {
                require(at <= b.size-38 && u16(b,at)==257)
                val characteristics=b[at+18].toInt() and 255
                val nameLength=b[at+19].toInt() and 255
                val nameAt=at+38+u16(b,at+36)
                require(nameAt >= at+38 && nameAt <= b.size-nameLength)
                if(nameLength > 0 && characteristics and 12 == 0) {
                    val compression=b[nameAt].toInt() and 255
                    val name = when(compression) {
                        8 -> String(b,nameAt+1,nameLength-1,Charsets.ISO_8859_1)
                        16 -> { require((nameLength-1)%2==0); String(b,nameAt+1,nameLength-1,Charsets.UTF_16BE) }
                        else -> error("Nom UDF non pris en charge.")
                    }.uppercase()
                    require(name !in result) { "Entrée UDF dupliquée." }
                    result[name]=longAd(b,at+20)
                }
                at=(nameAt+nameLength+3) and -4
            }
            return result
        }
        fun extract(destination: File) {
            val anchor=bytes(256L*2048,2048); require(tag(anchor)==2) { "ISO9660/UDF standard attendu." }
            val sequenceLength=u32(anchor,16); val sequenceBlock=u32(anchor,20)
            require(sequenceLength in 2048..1048576 && sequenceLength%2048==0L)
            var partitionNumber: Int?=null; var logical: ByteArray?=null
            for(n in 0 until sequenceLength/2048) {
                val b=bytes((sequenceBlock+n)*2048,2048)
                when(tag(b)) {
                    5 -> { require(partitionNumber==null); partitionNumber=u16(b,22); partitionStart=u32(b,188); partitionBlocks=u32(b,192) }
                    6 -> { require(logical==null); logical=b }
                    8 -> break
                }
            }
            val l=logical ?: error("Volume UDF absent.")
            require(partitionNumber!=null && u32(l,212)==2048L && u32(l,264)==6L && u32(l,268)==1L && l[440].toInt()==1 && l[441].toInt()==6 && u16(l,444)==partitionNumber) { "Carte de partitions UDF non prise en charge." }
            val set=bytes(physical(longAd(l,248),2048),2048); require(tag(set)==256)
            val root=file(longAd(set,400))
            val sources=children(root)["SOURCES"] ?: error("Dossier sources absent de l'ISO.")
            val entries=children(file(sources))
            val selected=entries["INSTALL.WIM"] ?: entries["INSTALL.ESD"] ?: error("install.wim/install.esd absent de l'ISO.")
            val image=file(selected); require(!image.directory && image.length >= 208)
            destination.outputStream().use { output ->
                if(image.inline!=null) output.write(image.inline) else {
                    val buffer=ByteArray(1048576); var remaining=image.length
                    for((offset,size) in image.extents) {
                        input.seek(offset); var left=minOf(size,remaining)
                        while(left > 0) {
                            if(Thread.currentThread().isInterrupted) error("Import interrompu.")
                            val count=minOf(left,buffer.size.toLong()).toInt(); input.readFully(buffer,0,count); output.write(buffer,0,count); left-=count; remaining-=count
                        }
                    }
                    require(remaining==0L)
                }
            }
        }
    }
    fun extract(iso: File, destination: File) {
        require(!destination.exists())
        try { RandomAccessFile(iso,"r").use { require(it.length() in 257L*2048..WindowsImage.MAX_BYTES); Reader(it).extract(destination) } }
        catch(e: Exception) { destination.delete(); throw e }
    }
}
