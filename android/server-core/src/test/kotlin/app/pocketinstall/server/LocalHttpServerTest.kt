package app.pocketinstall.server

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LocalHttpServerTest {
    private val loopback = InetAddress.getByName("127.0.0.1") as Inet4Address
    private val bytes = "PocketInstall real socket transfer".toByteArray()
    private fun server(lifetime: Long = 60000, prefix: Int = 8,
        resource: BootResource = BootResource(bytes.size.toLong(), "application/efi") { ByteArrayInputStream(bytes) },
        events: MutableList<HttpEvent> = CopyOnWriteArrayList(), onStop: (String) -> Unit = {}): LocalHttpServer =
        LocalHttpServer(loopback, Ipv4Subnet(loopback, prefix), mapOf("bootx64.efi" to resource),
            requestedPort = 0, allowLoopbackForTests = true, lifetimeMillis = lifetime,
            onEvent = { events.add(it) }, onStop = onStop).also { it.start() }

    private fun exchange(server: LocalHttpServer, method: String = "GET", path: String = "/${server.session}/bootx64.efi",
        extra: String = "", source: String? = null): ByteArray = Socket().use { socket ->
        socket.soTimeout = 3000
        if (source != null) socket.bind(java.net.InetSocketAddress(InetAddress.getByName(source), 0))
        socket.connect(java.net.InetSocketAddress(loopback, server.port))
        socket.getOutputStream().write("$method $path HTTP/1.1\r\nHost: 127.0.0.1\r\n$extra\r\n".toByteArray())
        socket.getInputStream().readBytes()
    }
    private fun header(data: ByteArray) = data.toString(Charsets.ISO_8859_1).substringBefore("\r\n\r\n")
    private fun body(data: ByteArray): ByteArray {
        val index = data.toString(Charsets.ISO_8859_1).indexOf("\r\n\r\n")
        return data.copyOfRange(index + 4, data.size)
    }

    @Test fun getDeliversExactBytesAndHeadOnlyAdvertisesLength() = server().use { s ->
        val get = exchange(s)
        assertTrue(header(get).startsWith("HTTP/1.1 200"))
        assertTrue(header(get).contains("Content-Type: application/efi"))
        assertArrayEquals(bytes, body(get))
        val head = exchange(s, "HEAD")
        assertTrue(header(head).contains("Content-Length: ${bytes.size}"))
        assertEquals(0, body(head).size)
    }
    @Test fun rangesSupportResumeAndSuffix() = server().use { s ->
        val part = exchange(s, extra = "Range: bytes=7-12\r\n")
        assertTrue(header(part).startsWith("HTTP/1.1 206"))
        assertArrayEquals(bytes.copyOfRange(7, 13), body(part))
        val resumed = exchange(s, extra = "Range: bytes=7-\r\n")
        assertArrayEquals(bytes.copyOfRange(7, bytes.size), body(resumed))
        val suffix = exchange(s, extra = "Range: bytes=-4\r\n")
        assertArrayEquals(bytes.takeLast(4).toByteArray(), body(suffix))
    }
    @Test fun invalidAndMultipleRangesDoNotLeakData() = server().use { s ->
        for (range in listOf("bytes=999-", "bytes=0-1,4-5", "bytes=-0", "bytes=999999999999999999999-")) {
            val reply = exchange(s, extra = "Range: $range\r\n")
            assertTrue(header(reply).startsWith("HTTP/1.1 416"))
            assertEquals(0, body(reply).size)
        }
        assertTrue(header(exchange(s, extra = "Range: bytes=0-1\r\nRange: bytes=2-3\r\n")).startsWith("HTTP/1.1 400"))
    }
    @Test fun largeResourcesUseLongAndCanFetchPastFourGiB() {
        val size = 5L * 1024 * 1024 * 1024
        val synthetic = BootResource(size, "application/octet-stream") {
            object : InputStream() {
                var position = 0L
                override fun read(): Int = if (position++ < size) 0x5a else -1
                override fun skip(n: Long): Long = minOf(n, size - position).also { position += it }
            }
        }
        server(resource = synthetic).use { s ->
            assertTrue(header(exchange(s, "HEAD")).contains("Content-Length: $size"))
            val start = 4L * 1024 * 1024 * 1024 + 31
            val reply = exchange(s, extra = "Range: bytes=$start-${start + 3}\r\n")
            assertTrue(header(reply).contains("Content-Range: bytes $start-${start + 3}/$size"))
            assertArrayEquals(byteArrayOf(90, 90, 90, 90), body(reply))
        }
    }
    @Test fun pathsAndMethodsAreReadOnlyAndAllowlisted() = server().use { s ->
        for (path in listOf("/${s.session}/../secret", "/${s.session}/%2e%2e/secret", "/${s.session}/bootx64.efi?x=1"))
            assertTrue(header(exchange(s, path = path)).startsWith("HTTP/1.1 400"))
        assertTrue(header(exchange(s, path = "/${s.session}/other.efi")).startsWith("HTTP/1.1 404"))
        assertTrue(header(exchange(s, path = "/wrong/bootx64.efi")).startsWith("HTTP/1.1 404"))
        for (method in listOf("POST", "PUT", "DELETE", "CONNECT")) {
            val reply = exchange(s, method)
            assertTrue(header(reply).startsWith("HTTP/1.1 405"))
            assertEquals(0, body(reply).size)
        }
    }
    @Test fun parserRejectsOversizedHeadersAndBodies() = server().use { s ->
        assertTrue(header(exchange(s, extra = "X-Test: ${"a".repeat(9000)}\r\n")).startsWith("HTTP/1.1 431"))
        assertTrue(header(exchange(s, extra = "Content-Length: 1\r\n")).startsWith("HTTP/1.1 400"))
        assertTrue(header(exchange(s, extra = "Transfer-Encoding: chunked\r\n")).startsWith("HTTP/1.1 400"))
    }
    @Test fun sourceSubnetIsEnforcedAtTheSocket() = server(prefix = 32).use { s ->
        assertTrue(header(exchange(s, source = "127.0.0.2")).startsWith("HTTP/1.1 403"))
        assertTrue(header(exchange(s)).startsWith("HTTP/1.1 200"))
    }
    @Test fun slowDripDoesNotKeepAHeaderWorkerAlive() {
        LocalHttpServer(loopback, Ipv4Subnet(loopback, 8),
            mapOf("bootx64.efi" to BootResource(bytes.size.toLong(), "application/efi") { ByteArrayInputStream(bytes) }),
            requestedPort = 0, allowLoopbackForTests = true, readTimeoutMillis = 200).use { s ->
            s.start()
            Socket(loopback, s.port).use { socket ->
                socket.soTimeout = 1500
                val ended = AtomicBoolean(false)
                val writer = Thread {
                    while (!ended.get()) {
                        try {
                            socket.getOutputStream().write('G'.code)
                            socket.getOutputStream().flush()
                            Thread.sleep(20)
                        } catch (_: Exception) { break }
                    }
                }.apply { isDaemon = true; start() }
                try {
                    // Bytes keep arriving below the inactivity timeout. A bounded
                    // total header budget must still reject this connection.
                    assertTrue(socket.getInputStream().bufferedReader().readLine().startsWith("HTTP/1.1 408"))
                } finally { ended.set(true); writer.join(500) }
            }
        }
    }
    @Test fun publicAndWildcardBindsAreRejected() {
        for (address in listOf("0.0.0.0", "8.8.8.8", "127.0.0.1")) {
            val ip = InetAddress.getByName(address) as Inet4Address
            assertThrows(IllegalArgumentException::class.java) {
                LocalHttpServer(ip, Ipv4Subnet(ip, 24), mapOf("bootx64.efi" to BootResource(0, "application/efi") { ByteArrayInputStream(byteArrayOf()) }))
            }
        }
    }
    @Test fun expiryClosesThePortAndRestartHasANewToken() {
        val ended = CountDownLatch(1)
        val first = server(lifetime = 200, onStop = { ended.countDown() })
        val token = first.session
        assertTrue(ended.await(2, TimeUnit.SECONDS))
        assertFalse(first.isRunning)
        assertThrows(Exception::class.java) { exchange(first) }
        first.close()
        server().use { second ->
            assertNotEquals(token, second.session)
            assertTrue(header(exchange(second, path = "/$token/bootx64.efi")).startsWith("HTTP/1.1 404"))
        }
    }
    @Test fun eventsContainOnlyResourceNamesAndNeverTokens() {
        val events = CopyOnWriteArrayList<HttpEvent>()
        server(events = events).use { s ->
            exchange(s)
            exchange(s, path = "/${s.session}/not-allowed")
            assertFalse(events.joinToString().contains(s.session))
            assertTrue(events.any { it.resource == "bootx64.efi" && it.phase == RequestPhase.FINISHED && it.sentBytes == bytes.size.toLong() })
            assertTrue(events.any { it.status == 404 })
        }
    }
    @Test fun rfc1918PolicyDoesNotMistakeLinkLocalForPrivateLan() {
        assertTrue(Ipv4Subnet.isPrivate(InetAddress.getByName("172.16.1.4")))
        assertTrue(Ipv4Subnet.isPrivate(InetAddress.getByName("192.168.4.4")))
        assertFalse(Ipv4Subnet.isPrivate(InetAddress.getByName("169.254.1.1")))
        assertFalse(Ipv4Subnet.isPrivate(InetAddress.getByName("172.32.1.4")))
        assertFalse(Ipv4Subnet(InetAddress.getByName("10.0.0.1") as Inet4Address, 0).isPrivate)
    }
}
