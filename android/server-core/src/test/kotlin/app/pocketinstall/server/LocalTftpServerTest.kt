package app.pocketinstall.server

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test

class LocalTftpServerTest {
    private val bind = InetAddress.getByName("127.0.0.1") as Inet4Address
    private val session = "0123456789abcdef0123456789abcdef"
    private fun server(bytes: ByteArray, events: MutableList<TftpEvent> = CopyOnWriteArrayList(),
        lifetime: Long = 10000, clock: () -> Long = { System.nanoTime() / 1_000_000 },
        onStop: (String) -> Unit = {}): LocalTftpServer =
        LocalTftpServer(bind, Ipv4Subnet(bind, 8),
            mapOf("bootx64.efi" to BootResource(bytes.size.toLong(), "application/efi") { ByteArrayInputStream(bytes) }),
            requestedPort = 0, session = session, allowLoopbackForTests = true,
            lifetimeMillis = lifetime, retryTimeoutMillis = 100, maxAttempts = 2,
            monotonicMillis = clock,
            onEvent = { events.add(it) }, onStop = onStop).apply { start() }
    private fun rrq(name: String = "$session/bootx64.efi", options: String = "", mode: String = "octet") =
        byteArrayOf(0, 1) + "$name\u0000$mode\u0000$options".toByteArray(Charsets.US_ASCII)
    private fun send(socket: DatagramSocket, bytes: ByteArray, peer: InetSocketAddress) =
        socket.send(DatagramPacket(bytes, bytes.size, peer))
    private fun receive(socket: DatagramSocket): DatagramPacket =
        DatagramPacket(ByteArray(2048), 2048).also { socket.receive(it) }
    private fun data(packet: DatagramPacket) = packet.data.copyOf(packet.length)
    private fun ack(socket: DatagramSocket, packet: DatagramPacket, block: Int) =
        send(socket, byteArrayOf(0, 4, (block ushr 8).toByte(), block.toByte()), packet.socketAddress as InetSocketAddress)
    private fun socket() = DatagramSocket(InetSocketAddress(bind, 0)).apply { soTimeout = 1000 }

    @Test fun ordinaryTransferUsesNewTidAndMatchesBytes() {
        val bytes = ByteArray(1500) { (it % 251).toByte() }
        server(bytes).use { server -> socket().use { client ->
            send(client, rrq(), InetSocketAddress(bind, server.port))
            val output = ByteArrayOutputStream()
            for (block in 1..3) {
                val packet = receive(client)
                assertNotEquals(server.port, packet.port)
                assertEquals(3, packet.data[1].toInt())
                assertEquals(block, packet.data[3].toInt() and 255)
                output.write(packet.data, 4, packet.length - 4)
                ack(client, packet, block)
            }
            assertArrayEquals(bytes, output.toByteArray())
        } }
    }

    @Test fun exactBlockMultipleHasEmptyFinalData() {
        server(ByteArray(1024)).use { server -> socket().use { client ->
            send(client, rrq(), InetSocketAddress(bind, server.port))
            for (block in 1..3) {
                val packet = receive(client)
                assertEquals(if (block == 3) 4 else 516, packet.length)
                ack(client, packet, block)
            }
        } }
    }

    @Test fun emptyFileStillSendsDataBlockOne() {
        server(byteArrayOf()).use { server -> socket().use { client ->
            send(client, rrq(), InetSocketAddress(bind, server.port))
            val packet = receive(client)
            assertArrayEquals(byteArrayOf(0, 3, 0, 1), data(packet))
            ack(client, packet, 1)
        } }
    }

    @Test fun negotiatesSizeBlockAndTimeoutBeforeSendingData() {
        val bytes = ByteArray(1700) { it.toByte() }
        server(bytes).use { server -> socket().use { client ->
            send(client, rrq(options = "blksize\u00008192\u0000tsize\u00000\u0000timeout\u00001\u0000windowsize\u00008\u0000"),
                InetSocketAddress(bind, server.port))
            val first = receive(client)
            assertEquals(6, first.data[1].toInt())
            val options = String(first.data, 2, first.length - 2, Charsets.US_ASCII)
            assertTrue(options.contains("blksize\u00001428\u0000"))
            assertTrue(options.contains("tsize\u00001700\u0000"))
            assertTrue(options.contains("timeout\u00001\u0000"))
            assertFalse(options.contains("windowsize"))
            ack(client, first, 0)
            val output = ByteArrayOutputStream()
            for (block in 1..2) {
                val packet = receive(client)
                assertEquals(if (block == 1) 1432 else 276, packet.length)
                output.write(packet.data, 4, packet.length - 4); ack(client, packet, block)
            }
            assertArrayEquals(bytes, output.toByteArray())
        } }
    }

    @Test fun unsupportedOptionsAreIgnoredWithoutOack() {
        server(byteArrayOf(1)).use { server -> socket().use { client ->
            send(client, rrq(options = "windowsize\u00004\u0000timeout\u0000255\u0000"), InetSocketAddress(bind, server.port))
            val packet = receive(client)
            assertEquals(3, packet.data[1].toInt()); ack(client, packet, 1)
        } }
    }

    @Test fun rejectsWriteTraversalAndExpiredToken() {
        val requests = listOf(rrq(name = "bootx64.efi"), rrq(name = "$session/../bootx64.efi"),
            rrq(name = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bootx64.efi"),
            rrq().apply { this[1] = 2 }, rrq(mode = "netascii"), rrq().dropLast(1).toByteArray())
        for (request in requests) server(byteArrayOf(1)).use { server -> socket().use { client ->
            send(client, request, InetSocketAddress(bind, server.port))
            val packet = receive(client)
            assertEquals(5, packet.data[1].toInt())
            assertTrue(packet.length < 40)
        } }
    }

    @Test fun malformedNegotiationIsRejected() {
        for (options in listOf("blksize\u00007\u0000", "tsize\u00001\u0000", "timeout\u00000\u0000",
            "blksize\u0000512\u0000BLKSIZE\u00001024\u0000")) {
            server(byteArrayOf(1)).use { server -> socket().use { client ->
                send(client, rrq(options = options), InetSocketAddress(bind, server.port))
                assertArrayEquals(byteArrayOf(0, 5, 0, 8), data(receive(client)).copyOf(4))
            } }
        }
    }

    @Test fun retriesMissingAckAndDoesNotReportSuccess() {
        val events = CopyOnWriteArrayList<TftpEvent>()
        server(byteArrayOf(4, 5), events).use { server -> socket().use { client ->
            send(client, rrq(), InetSocketAddress(bind, server.port))
            val first = receive(client)
            assertArrayEquals(data(first), data(receive(client)))
            client.soTimeout = 350
            assertThrows(SocketTimeoutException::class.java) { receive(client) }
            val finished = events.last { it.phase == RequestPhase.FINISHED }
            assertEquals("TIMED_OUT", finished.result)
            assertEquals(0L, finished.acknowledgedBytes)
        } }
    }

    @Test fun ackFromWrongClientDoesNotAdvanceTransfer() {
        server(ByteArray(700)).use { server -> socket().use { client -> socket().use { wrong ->
            send(client, rrq(), InetSocketAddress(bind, server.port))
            val first = receive(client)
            ack(wrong, first, 1)
            val repeated = receive(client)
            assertArrayEquals(data(first), data(repeated))
            ack(client, repeated, 1)
            val next = receive(client)
            assertEquals(2, next.data[3].toInt()); ack(client, next, 2)
        } } }
    }

    @Test fun expiryClosesSocketAndNotifiesOwner() {
        val expired = CountDownLatch(1)
        server(byteArrayOf(1), lifetime = 100, onStop = { expired.countDown() }).use { server ->
            assertTrue(expired.await(1, TimeUnit.SECONDS))
            assertFalse(server.isRunning)
            assertThrows(IllegalStateException::class.java) { server.start() }
        }
    }

    @Test fun refusesPublicBindAndPublicSubnet() {
        val resource = mapOf("bootx64.efi" to BootResource(0, "application/efi") { ByteArrayInputStream(byteArrayOf()) })
        assertThrows(IllegalArgumentException::class.java) {
            LocalTftpServer(InetAddress.getByName("8.8.8.8") as Inet4Address, Ipv4Subnet(bind, 0), resource)
        }
        assertThrows(IllegalArgumentException::class.java) {
            val privateAddress = InetAddress.getByName("192.168.1.1") as Inet4Address
            LocalTftpServer(privateAddress, Ipv4Subnet(privateAddress, 8), resource)
        }
    }

    @Test fun elapsedExpiryIncludingSuspendDoesNotServeANewRequest() {
        val clock = AtomicLong(0)
        val stopped = CountDownLatch(1)
        server(byteArrayOf(1), clock = { clock.get() }, onStop = { stopped.countDown() }).use { server ->
            socket().use { client ->
                val peer = InetSocketAddress(bind, server.port)
                clock.set(20000) // Model Android elapsedRealtime advancing during device sleep.
                send(client, rrq(), peer)
                client.soTimeout = 250
                assertThrows(SocketTimeoutException::class.java) { receive(client) }
                assertTrue(stopped.await(1500, TimeUnit.MILLISECONDS))
                assertFalse(server.isRunning)
            }
        }
    }

    @Test fun occupiedPortFailsWithoutStoppingHttpSession() {
        DatagramSocket(InetSocketAddress(bind, 0)).use { occupied ->
            var stopped = false
            val server = LocalTftpServer(bind, Ipv4Subnet(bind, 8),
                mapOf("bootx64.efi" to BootResource(0, "application/efi") { ByteArrayInputStream(byteArrayOf()) }),
                requestedPort = occupied.localPort, allowLoopbackForTests = true, onStop = { stopped = true })
            server.use {
                assertThrows(java.net.SocketException::class.java) { it.start() }
                assertFalse(stopped)
            }
        }
    }
}
