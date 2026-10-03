package app.pocketinstall.server

import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.CountDownLatch

/** Identical TFTP implementation as the APK, for isolated lab testing. */
fun pxeMain(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: --pxe <bootx64.efi> [bind IPv4] [prefix] [port]" }
    val file = File(args[0]).also { require(it.isFile) }
    val bind = InetAddress.getByName(args.getOrElse(1) { "127.0.0.1" }) as Inet4Address
    val stopped = CountDownLatch(1)
    val server = LocalTftpServer(bind, Ipv4Subnet(bind, args.getOrElse(2) { "8" }.toInt()),
        mapOf("bootx64.efi" to BootResource(file.length(), "application/efi") { file.inputStream() }),
        requestedPort = args.getOrElse(3) { "6969" }.toInt(), allowLoopbackForTests = bind.isLoopbackAddress,
        onEvent = { println("TFTP ${it.phase} ${it.resource} ${it.result} ${it.acknowledgedBytes}/${it.expectedBytes} ${it.peer}") },
        onStop = { println(it); stopped.countDown() })
    server.start()
    println("TFTP_PORT=${server.port}")
    println("BOOT_FILE=${server.bootFilename}")
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })
    stopped.await()
}
