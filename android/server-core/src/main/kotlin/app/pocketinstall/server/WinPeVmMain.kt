package app.pocketinstall.server

import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.CountDownLatch

/** Diskless VM harness only; Android never calls this entry point. */
fun main(args: Array<String>) {
    require(args.size == 2 || args.size == 3)
    val directory = File(args[0]); val output = File(args[1]).apply { mkdirs() }
    val address = InetAddress.getByName("127.0.0.1") as Inet4Address
    val image = args.getOrNull(2)?.let { File(it) }
    val info = image?.let { WindowsImageInfo(it.length(),WindowsImage.hash(it),WindowsImage.inspect(it)) }
    val entry = info?.selected(WindowsSelection(edition=WindowsEdition.PRO))
    val plan = if(info == null || entry == null) "{\"enabled\":false}" else
        """{"schema":1,"enabled":true,"version":"WINDOWS_11","editionId":"Professional","index":${entry.index},"bytes":${info.bytes},"sha256":"${info.sha256}","debloat":"LIGHT","removeClipchamp":false,"removeSolitaire":false,"removeNews":false,"removeWeather":false}"""
    var progress = WinPeProgress()
    val done = CountDownLatch(1)
    val log = File(output, "http.log")
    LocalHttpServer(address, Ipv4Subnet(address, 8), emptyMap(),
        resourceFactory = { WinPeHttp.resources(directory, it.replace("127.0.0.1", "10.0.2.2"),plan.toByteArray()) +
            if(image != null) mapOf("install/image.wim" to BootResource(image.length(),"application/octet-stream") { image.inputStream() }) else emptyMap() },
        reportHandler = { _,body ->
            val text = body.toString(Charsets.UTF_8)
            val stage = Regex(""""stage"\s*:\s*"([a-z-]+)"""").find(text)?.groupValues?.get(1)
            if(stage != null && progress.stage == WinPeStage.STARTED) {
                File(output,"report-$stage").writeText(text)
                true
            } else false
        },
        publicAliases = WinPeHttp.aliases, requestedPort = 8080, allowLoopbackForTests = true,
        onEvent = { event -> synchronized(log) {
            log.appendText("${event.method} ${event.resource} ${event.status} ${event.phase} ${event.sentBytes}/${event.expectedBytes}\n")
            progress = progress.accept(event)
            if (progress.stage == WinPeStage.STARTED) File(output, "winpe-started").writeText("WinPE runtime callback after complete transfers\n")
        } }).use { server ->
        server.start()
        File(output, "server-ready").writeText("ready\n")
        Runtime.getRuntime().addShutdownHook(Thread { server.close(); done.countDown() })
        done.await()
    }
}

