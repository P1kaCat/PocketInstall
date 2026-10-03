package app.pocketinstall.server

import java.io.BufferedInputStream
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class TftpEvent(
    val id: Long, val peer: String, val resource: String, val phase: RequestPhase,
    val result: String, val expectedBytes: Long = 0, val acknowledgedBytes: Long = 0,
)

/** Read-only RFC 1350 TFTP, with RFC 2347/2348/2349 options.
 * No DHCP, disk paths, uploads or executable input. A high port is useful only
 * behind a separately configured PXE relay: firmware normally uses UDP 69.
 */
class LocalTftpServer(
    private val bind: Inet4Address,
    private val subnet: Ipv4Subnet,
    private val resources: Map<String, BootResource>,
    private val requestedPort: Int = 69,
    val session: String = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) },
    private val lifetimeMillis: Long = 30 * 60 * 1000L,
    private val allowLoopbackForTests: Boolean = false,
    private val retryTimeoutMillis: Int = 1000,
    private val maxAttempts: Int = 5,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val onEvent: (TftpEvent) -> Unit = {},
    private val onStop: (String) -> Unit = {},
) : Closeable {
    init {
        require(requestedPort in 0..65535 && lifetimeMillis in 1..30 * 60 * 1000L)
        require(retryTimeoutMillis in 20..5000 && maxAttempts in 1..5)
        require(subnet.contains(bind))
        require((Ipv4Subnet.isPrivate(bind) && subnet.isPrivate) ||
            (allowLoopbackForTests && bind.isLoopbackAddress && subnet.prefix >= 8))
        require(session.matches(Regex("[0-9a-f]{32}")))
        require(resources.isNotEmpty() && resources.size <= 8)
        resources.forEach { (name, resource) ->
            require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) && ".." !in name)
            require(resource.length in 0..16 * 1024 * 1024L)
        }
    }

    private val listener = DatagramSocket(null)
    private val running = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val sequence = AtomicLong()
    private val transfers = ConcurrentHashMap<InetSocketAddress, DatagramSocket>()
    private val pending = ConcurrentHashMap.newKeySet<InetSocketAddress>()
    private val workers = ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
        SynchronousQueue(), { task -> Thread(task, "PocketInstall-TFTP").apply { isDaemon = true } })
    private val expiry = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "PocketInstall-TFTP-expiry").apply { isDaemon = true }
    }
    @Volatile private var deadline = Long.MAX_VALUE
    @Volatile private var expiresAt = Long.MAX_VALUE
    private var rejectionAfter = 0L
    val port: Int get() = listener.localPort
    val bootFilename: String get() = "$session/bootx64.efi"
    val isRunning: Boolean get() = running.get() && monotonicMillis() < expiresAt

    fun start() {
        check(started.compareAndSet(false, true)) { "Cannot restart a TFTP server instance" }
        try {
            listener.reuseAddress = false
            listener.bind(InetSocketAddress(bind, requestedPort))
            listener.soTimeout = 1000
            deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(lifetimeMillis)
            expiresAt = monotonicMillis() + lifetimeMillis
            running.set(true)
            expiry.schedule({ stop("Session TFTP expirée.") }, lifetimeMillis, TimeUnit.MILLISECONDS)
            Thread({ listen() }, "PocketInstall-TFTP-listener").apply { isDaemon = true; start() }
        } catch (e: Exception) {
            // Do not call onStop: the caller may fall back from denied port 69
            // to a high port without closing its HTTP session.
            close()
            throw e
        }
    }

    private data class ReadRequest(val name: String, val blockSize: Int,
        val timeout: Int, val options: LinkedHashMap<String, String>)
    private class Invalid(val code: Int) : Exception()
    private fun parse(bytes: ByteArray): ReadRequest {
        if (bytes.size !in 4..1024 || word(bytes, 0) != 1 || bytes.last() != 0.toByte()) throw Invalid(4)
        if (bytes.drop(2).any { it != 0.toByte() && (it.toInt() and 255) !in 32..126 }) throw Invalid(4)
        val fields = String(bytes, 2, bytes.size - 3, StandardCharsets.US_ASCII).split('\u0000')
        if (fields.size < 2 || fields.size > 18 || fields.size % 2 != 0 || fields.any { it.isEmpty() }) throw Invalid(4)
        if (fields[1].lowercase() != "octet") throw Invalid(4)
        val name = fields[0]
        if (!name.startsWith("$session/") || name.removePrefix("$session/") !in resources) throw Invalid(1)
        val resource = resources.getValue(name.removePrefix("$session/"))
        val accepted = linkedMapOf<String, String>()
        val seen = hashSetOf<String>()
        var blockSize = 512
        var timeout = retryTimeoutMillis
        for (i in 2 until fields.size step 2) {
            val key = fields[i].lowercase()
            val value = fields[i + 1]
            if (!seen.add(key)) throw Invalid(8)
            when (key) {
                "blksize" -> {
                    val size = value.toIntOrNull()?.takeIf { it in 8..65464 } ?: throw Invalid(8)
                    blockSize = minOf(size, 1428) // Avoid ordinary Ethernet fragmentation.
                    accepted[key] = blockSize.toString()
                }
                "timeout" -> {
                    val seconds = value.toIntOrNull()?.takeIf { it in 1..255 } ?: throw Invalid(8)
                    // RFC 2349 requires the exact requested value if accepted.
                    // Ignore longer timeouts rather than allocating long-lived workers.
                    if (seconds <= 5) { timeout = seconds * 1000; accepted[key] = value }
                }
                "tsize" -> {
                    if (value != "0") throw Invalid(8)
                    accepted[key] = resource.length.toString()
                }
                // windowsize and unknown options are ignored, as RFC 2347 permits.
            }
        }
        return ReadRequest(name.removePrefix("$session/"), blockSize, timeout, accepted)
    }

    private fun emit(event: TftpEvent) { runCatching { onEvent(event) } }
    private fun listen() {
        while (running.get()) {
            try {
                if (!isRunning) { stop("Session TFTP expirée."); break }
                val incoming = DatagramPacket(ByteArray(1025), 1025)
                listener.receive(incoming)
                val peer = incoming.socketAddress as InetSocketAddress
                if (!subnet.contains(peer.address) || !(Ipv4Subnet.isPrivate(peer.address) ||
                    allowLoopbackForTests && peer.address.isLoopbackAddress)) continue
                val id = sequence.incrementAndGet()
                val request = try { parse(incoming.data.copyOf(incoming.length)) }
                    catch (e: Invalid) {
                        // Bound rejection responses and log entries even during a LAN flood.
                        val now = System.nanoTime()
                        if (now >= rejectionAfter) {
                            rejectionAfter = now + TimeUnit.MILLISECONDS.toNanos(100)
                            val bytes = error(e.code)
                            listener.send(DatagramPacket(bytes, bytes.size, peer))
                            emit(TftpEvent(id, peer.address.hostAddress ?: "?", "rejected",
                                RequestPhase.FINISHED, "REJECTED ${e.code}"))
                        }
                        continue
                    }
                if (!pending.add(peer)) continue // A repeated RRQ must not create another transfer.
                try { workers.execute { try { transfer(id, peer, request) } finally { pending.remove(peer) } } }
                catch (_: RejectedExecutionException) {
                    pending.remove(peer) // Silently drop; client can retry after the four workers free up.
                }
            } catch (_: SocketTimeoutException) {
                // Periodically check the lifetime.
            } catch (_: Exception) {
                if (running.get()) stop("Le serveur TFTP a été interrompu.")
            }
        }
    }

    private fun transfer(id: Long, peer: InetSocketAddress, request: ReadRequest) {
        val resource = resources.getValue(request.name)
        val address = peer.address.hostAddress ?: "?"
        var acknowledged = 0L
        var result = "ABORTED"
        val socket = DatagramSocket(null)
        try {
            socket.bind(InetSocketAddress(bind, 0))
            socket.connect(peer) // ACKs from another IP/TID are discarded by the OS.
            transfers[peer] = socket
            if (!isRunning) return
            resource.open().use { raw ->
                val stream = BufferedInputStream(raw)
                emit(TftpEvent(id, address, request.name, RequestPhase.STARTED, "TRANSFER", resource.length))
                if (request.options.isNotEmpty()) {
                    val oack = byteArrayOf(0, 6) + request.options.entries.joinToString("") {
                        "${it.key}\u0000${it.value}\u0000"
                    }.toByteArray(StandardCharsets.US_ASCII)
                    if (!sendAndAwait(socket, oack, 0, request.timeout)) { result = "TIMED_OUT"; return@use }
                }
                var block = 1
                var remaining = resource.length
                do {
                    if (!isRunning) return@use
                    val count = minOf(remaining, request.blockSize.toLong()).toInt()
                    val bytes = ByteArray(4 + count)
                    bytes[1] = 3
                    bytes[2] = (block ushr 8).toByte(); bytes[3] = block.toByte()
                    var read = 0
                    while (read < count) {
                        val n = stream.read(bytes, 4 + read, count - read)
                        check(n > 0) { "Truncated boot resource" }
                        read += n
                    }
                    if (!sendAndAwait(socket, bytes, block, request.timeout)) { result = "TIMED_OUT"; return@use }
                    acknowledged += count
                    remaining -= count
                    block = (block + 1) and 65535
                    // A zero-length final DATA is required when length is a multiple of blksize.
                    if (count < request.blockSize) { result = "COMPLETE"; break }
                } while (true)
            }
        } catch (_: Exception) {
            result = "ABORTED"
        } finally {
            socket.close(); transfers.remove(peer)
            emit(TftpEvent(id, address, request.name, RequestPhase.FINISHED,
                result, resource.length, acknowledged))
        }
    }

    private fun sendAndAwait(socket: DatagramSocket, bytes: ByteArray, block: Int, timeout: Int): Boolean {
        repeat(maxAttempts) {
            if (!isRunning) return false
            socket.send(DatagramPacket(bytes, bytes.size))
            val until = minOf(deadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout.toLong()))
            while (isRunning && System.nanoTime() < until) {
                socket.soTimeout = TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime()).coerceIn(1, timeout.toLong()).toInt()
                val incoming = DatagramPacket(ByteArray(1025), 1025)
                try { socket.receive(incoming) } catch (_: SocketTimeoutException) { break }
                if (incoming.length == 4 && word(incoming.data, 0) == 4 && word(incoming.data, 2) == block) return true
                if (incoming.length >= 4 && word(incoming.data, 0) == 5) return false
                // Duplicated/out-of-order ACKs must not reset the absolute retry deadline.
            }
        }
        return false
    }

    private fun stop(reason: String) {
        if (closed.get()) return
        close()
        runCatching { onStop(reason) }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        running.set(false)
        listener.close()
        transfers.values.forEach { it.close() }
        workers.shutdownNow(); expiry.shutdownNow()
    }
    private fun error(code: Int): ByteArray = byteArrayOf(0, 5, 0, code.toByte()) +
        (if (code == 1) "Not found" else "Rejected").toByteArray(StandardCharsets.US_ASCII) + byteArrayOf(0)
    private fun word(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 255) shl 8) or (data[offset + 1].toInt() and 255)
}
