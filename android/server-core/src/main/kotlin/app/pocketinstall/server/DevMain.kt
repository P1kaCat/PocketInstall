package app.pocketinstall.server

import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.CountDownLatch

/** Runs the identical server used in the Android service, on JVM for the lab. */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: DevMain <bootx64.efi> [bind IPv4] [prefix] [port]" }
    val file = File(args[0]).also { require(it.isFile) }
    val bind = InetAddress.getByName(args.getOrElse(1) { "127.0.0.1" }) as Inet4Address
    val prefix = args.getOrElse(2) { "8" }.toInt()
    val port = args.getOrElse(3) { "8080" }.toInt()
    val stopped = CountDownLatch(1)
    val server = LocalHttpServer(bind, Ipv4Subnet(bind, prefix),
        mapOf("bootx64.efi" to BootResource(file.length(), "application/efi") { file.inputStream() }),
        requestedPort = port, allowLoopbackForTests = bind.isLoopbackAddress,
        onEvent = { println("${it.phase} ${it.method} ${it.resource} ${it.status} ${it.sentBytes}/${it.expectedBytes} ${it.peer}") },
        onStop = { println(it); stopped.countDown() })
    server.start()
    println("BOOT_URL=${server.bootUrl}")
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })
    stopped.await()
}
