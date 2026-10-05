package app.pocketinstall.server

import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

object LinuxHttp {
    val aliases=mapOf("/boot.ipxe" to "linux/boot.ipxe")
    fun resources(directory: File, base: String, profile: LinuxProfile): Map<String,BootResource> {
        val result=LinuxInstaller.names.associate { name ->
            val file=File(directory,name)
            require(file.isFile && file.length()>0) { "Fichier absent : $name" }
            "linux/$name" to BootResource(file.length(),"application/octet-stream") { file.inputStream() }
        }.toMutableMap()
        fun text(name: String,bytes: ByteArray) { result["linux/$name"]=BootResource(bytes.size.toLong(),"text/plain") { bytes.inputStream() } }
        text("boot.ipxe",LinuxInstaller.script(base)); text("preseed.cfg",LinuxInstaller.preseed(base,profile))
        text("started","Installer startup signal.\n".toByteArray()); text("installed","Installer completion signal.\n".toByteArray())
        return result
    }
    fun check(server: LocalHttpServer, address: Inet4Address, directory: File, profile: LinuxProfile) {
        check(WinPeHttp.get(address,server.port,"/boot.ipxe").contentEquals(LinuxInstaller.script(server.baseUrl)))
        check(WinPeHttp.get(address,server.port,"/${server.session}/linux/boot.ipxe").contentEquals(LinuxInstaller.script(server.baseUrl)))
        check(WinPeHttp.get(address,server.port,"/${server.session}/linux/preseed.cfg").contentEquals(LinuxInstaller.preseed(server.baseUrl,profile)))
        LinuxInstaller.names.forEach { name ->
            val file=File(directory,name)
            for(offset in setOf(0L,file.length()-1)) {
                val actual=WinPeHttp.get(address,server.port,"/${server.session}/linux/$name","bytes=$offset-$offset",file.length())
                java.io.RandomAccessFile(file,"r").use { input -> input.seek(offset); check(actual.size==1 && actual[0].toInt() and 255==input.read()) }
            }
        }
    }
    fun preflight(directory: File, profile: LinuxProfile) {
        val address=InetAddress.getByName("127.0.0.1") as Inet4Address
        LocalHttpServer(address,Ipv4Subnet(address,8),emptyMap(),resourceFactory={resources(directory,it,profile)},
            publicAliases=aliases,requestedPort=0,allowLoopbackForTests=true).use { it.start(); check(it,address,directory,profile) }
    }
}
