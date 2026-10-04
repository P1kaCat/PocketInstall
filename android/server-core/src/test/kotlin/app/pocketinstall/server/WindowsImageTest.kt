package app.pocketinstall.server

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class WindowsImageTest {
    private fun image(file: File, xml: String, offset: Long = 208) {
        val bytes = xml.toByteArray(Charsets.UTF_16LE)
        val header = ByteBuffer.allocate(208).order(ByteOrder.LITTLE_ENDIAN)
        header.put(byteArrayOf(77,83,87,73,77,0,0,0)); header.putInt(8,208); header.putInt(12,0x10d00)
        header.putShort(40,1); header.putShort(42,1); header.putInt(44,2)
        header.putLong(72,bytes.size.toLong()); header.putLong(80,offset); header.putLong(88,bytes.size.toLong())
        RandomAccessFile(file,"rw").use { it.write(header.array()); it.seek(offset); it.write(bytes) }
    }
    private val xml = "<WIM>" + listOf("Core","Professional").mapIndexed { n,edition ->
        "<IMAGE INDEX=\"${n+1}\"><NAME>Windows 11 $edition</NAME><WINDOWS><ARCH>9</ARCH><EDITIONID>$edition</EDITIONID><VERSION><BUILD>26100</BUILD></VERSION></WINDOWS></IMAGE>"
    }.joinToString("") + "</WIM>"
    @Test fun editionsUseMetadataNotFixedIndexesOrFileNames() {
        val file = Files.createTempFile("windows-image",".wim").toFile()
        try {
            image(file,"<?xml version=\"1.0\" encoding=\"UTF-16\"?>$xml")
            val info = WindowsImageInfo(file.length(),"a".repeat(64),WindowsImage.inspect(file))
            assertEquals(2,info.selected(WindowsSelection(edition=WindowsEdition.PRO)).index)
            assertThrows(IllegalStateException::class.java) { info.selected(WindowsSelection(version=WindowsVersion.WINDOWS_10)) }
            image(file,xml.replace("<ARCH>9</ARCH>","<ARCH>12</ARCH>"))
            assertThrows(IllegalArgumentException::class.java) { WindowsImage.inspect(file) }
        } finally { file.delete() }
    }
    @Test fun rejectsExternalEntitiesSplitImagesAndTruncatedMetadata() {
        val file = Files.createTempFile("windows-invalid",".wim").toFile()
        try {
            image(file,"<!DOCTYPE WIM [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>$xml")
            assertThrows(IllegalArgumentException::class.java) { WindowsImage.inspect(file) }
            image(file,xml)
            RandomAccessFile(file,"rw").use { it.seek(42); it.write(byteArrayOf(2,0)) }
            assertThrows(IllegalArgumentException::class.java) { WindowsImage.inspect(file) }
            image(file,xml)
            RandomAccessFile(file,"rw").use { it.setLength(220) }
            assertThrows(IllegalArgumentException::class.java) { WindowsImage.inspect(file) }
        } finally { file.delete() }
    }
    @Test fun metadataAndHttpRangesWorkBeyondFourGiBWithoutLoadingTheFileIntoRam() {
        val file = Files.createTempFile("windows-large",".wim").toFile()
        val address=InetAddress.getByName("127.0.0.1") as Inet4Address
        try {
            image(file,xml,5L * 1024 * 1024 * 1024)
            assertEquals(2,WindowsImage.inspect(file).size)
            LocalHttpServer(address,Ipv4Subnet(address,8),mapOf("install/image.wim" to BootResource(file.length(),"application/octet-stream") {file.inputStream()}),requestedPort=0,allowLoopbackForTests=true).use { server ->
                server.start(); WinPeHttp.checkImage(server,address,file)
            }
        } finally { file.delete() }
    }
    @Test fun reportsAreBoundedAndScopedToTheCurrentSessionAndRoute() {
        val address=InetAddress.getByName("127.0.0.1") as Inet4Address
        val received=CopyOnWriteArrayList<String>()
        LocalHttpServer(address,Ipv4Subnet(address,8),mapOf("bootx64.efi" to BootResource(1,"application/efi") {byteArrayOf(1).inputStream()}),
            requestedPort=0,allowLoopbackForTests=true,reportHandler={_,body -> received.add(body.toString(Charsets.UTF_8)); true }).use { server ->
            server.start()
            fun post(path: String, length: Int = 2, body: String = "{}", extra: String = ""): String = Socket(address,server.port).use { socket ->
                socket.soTimeout=3000
                socket.getOutputStream().write("POST $path HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: $length\r\n$extra\r\n$body".toByteArray())
                socket.getInputStream().readBytes().toString(Charsets.US_ASCII)
            }
            assertTrue(post("/${server.session}/install/report").startsWith("HTTP/1.1 200"))
            assertTrue(post("/expired/install/report").startsWith("HTTP/1.1 404"))
            assertTrue(post("/${server.session}/bootx64.efi").startsWith("HTTP/1.1 404"))
            assertTrue(post("/${server.session}/install/report",32769).startsWith("HTTP/1.1 400"))
            assertTrue(post("/${server.session}/install/report",extra="Transfer-Encoding: chunked\r\n").startsWith("HTTP/1.1 400"))
            assertEquals(listOf("{}"),received.toList())
        }
    }
}
