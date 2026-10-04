package app.pocketinstall.server

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class WindowsIsoTest {
    private fun record(name: String, sector: Int, size: Int, flags: Int): ByteArray {
        val text=name.toByteArray(Charsets.US_ASCII); val length=(33+text.size+1) and -2
        val b=ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        b.put(0,length.toByte()); b.putInt(2,sector); b.putInt(10,size); b.put(25,flags.toByte()); b.put(32,text.size.toByte())
        text.copyInto(b.array(),33); return b.array()
    }
    private fun iso(file: File, broken: Boolean) {
        val bytes=ByteArray(40*2048); val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val d=16*2048; b.put(d,1); "CD001".toByteArray().copyInto(bytes,d+1); b.putShort(d+128,2048)
        b.putInt(d+158,if(broken) Int.MAX_VALUE else 20); b.putInt(d+166,2048)
        record("SOURCES",21,2048,2).copyInto(bytes,20*2048)
        val first=record("INSTALL.WIM;1",22,300,128); first.copyInto(bytes,21*2048)
        record("INSTALL.WIM;1",23,300,0).copyInto(bytes,21*2048+first.size)
        ByteArray(300){1}.copyInto(bytes,22*2048); ByteArray(300){2}.copyInto(bytes,23*2048)
        file.writeBytes(bytes)
    }
    @Test fun readsMultiExtentImageInOrderAndRejectsOutOfBoundsDirectories() {
        val dir=Files.createTempDirectory("windows-iso").toFile()
        try {
            val input=File(dir,"windows.iso"); val output=File(dir,"install.wim"); iso(input,false)
            WindowsIso.extract(input,output)
            assertArrayEquals(ByteArray(300){1}+ByteArray(300){2},output.readBytes())
            output.delete(); iso(input,true)
            assertThrows(IllegalArgumentException::class.java){WindowsIso.extract(input,output)}
            assertFalse(output.exists())
        } finally { dir.deleteRecursively() }
    }
}
