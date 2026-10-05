package app.pocketinstall.server

import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

/** CI-only diskless installer lab, using the production downloader and route assembly. */
object LinuxLab {
@JvmStatic fun main(args: Array<String>) {
    require(args.size==1)
    val directory=File(args[0]).apply {mkdirs()}
    LinuxInstaller.download(directory,{false},{},{_,_,_->})
    LinuxProfile.entries.forEach { LinuxHttp.preflight(directory,it) }
    val address=InetAddress.getByName("127.0.0.1") as Inet4Address
    LocalHttpServer(address,Ipv4Subnet(address,8),emptyMap(),resourceFactory={ base ->
        LinuxHttp.resources(directory,base.replace("127.0.0.1","10.0.2.2"),LinuxProfile.SERVER)
    },publicAliases=LinuxHttp.aliases,requestedPort=0,allowLoopbackForTests=true,onEvent={event ->
        if(event.resource=="linux/started" && event.method=="GET" && event.phase==RequestPhase.FINISHED && event.status==200)
            File(directory,"installer-started").writeText("Actual Debian installer runtime callback\n")
    }).use { server ->
        server.start()
        File(directory,"endpoint").writeText(server.baseUrl.replace("127.0.0.1","10.0.2.2")+"/linux/preseed.cfg")
        Thread.sleep(240000)
    }
}

}
