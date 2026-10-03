package app.pocketinstall.server

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
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

data class BootResource(val length: Long, val contentType: String, val open: () -> InputStream)
enum class RequestPhase { STARTED, FINISHED }
data class HttpEvent(
    val id: Long, val atMillis: Long, val peer: String, val method: String,
    val resource: String, val status: Int, val phase: RequestPhase,
    val expectedBytes: Long = 0, val sentBytes: Long = 0,
)

/** A deliberately small, read-only HTTP/1.x subset for firmware file delivery.
 * No path resolution, redirects, compression, uploads, shell or remote control.
 */
class LocalHttpServer(
    private val bind: Inet4Address,
    private val subnet: Ipv4Subnet,
    private val resources: Map<String, BootResource>,
    private val requestedPort: Int = 8080,
    private val lifetimeMillis: Long = 30 * 60 * 1000L,
    private val allowLoopbackForTests: Boolean = false,
    private val readTimeoutMillis: Int = 5000,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val onEvent: (HttpEvent) -> Unit = {},
    private val onStop: (String) -> Unit = {},
) : Closeable {
    init {
        require(requestedPort in 0..65535 && lifetimeMillis in 1..30 * 60 * 1000L)
        require(subnet.contains(bind))
        require((Ipv4Subnet.isPrivate(bind) && subnet.isPrivate) ||
            (allowLoopbackForTests && bind.isLoopbackAddress && subnet.prefix >= 8))
        require(resources.isNotEmpty())
        resources.forEach { (name, r) ->
            require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,160}")) &&
                !name.contains("..") && !name.endsWith('/'))
            require(r.length >= 0 && r.contentType.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")))
        }
    }

    val session: String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val running = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    private val sequence = AtomicLong()
    @Volatile private var deadlineMillis = Long.MAX_VALUE
    private val listener = ServerSocket()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
        SynchronousQueue(), { task -> Thread(task, "PocketInstall-HTTP").apply { isDaemon = true } })
    private val expiry = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "PocketInstall-expiry").apply { isDaemon = true }
    }
    val port: Int get() = listener.localPort
    val bootUrl: String get() = "http://${bind.hostAddress}:$port/$session/bootx64.efi"
    val isRunning: Boolean get() = running.get() && monotonicMillis() < deadlineMillis

    fun start() {
        check(started.compareAndSet(false, true)) { "A server instance cannot be restarted" }
        try {
            listener.reuseAddress = false
            listener.bind(InetSocketAddress(bind, requestedPort), 8)
            listener.soTimeout = 1000
            deadlineMillis = monotonicMillis() + lifetimeMillis
            running.set(true)
            expiry.schedule({ stop("Session expirée (30 minutes maximum).") }, lifetimeMillis, TimeUnit.MILLISECONDS)
            Thread({ acceptLoop() }, "PocketInstall-listener").apply { isDaemon = true; start() }
        } catch (e: Exception) {
            stop("Impossible d'ouvrir le serveur HTTP.")
            throw e
        }
    }

    private fun acceptLoop() {
        while (running.get()) {
            try {
                if (!isRunning) { stop("Session expirée."); break }
                val socket = listener.accept()
                socket.soTimeout = readTimeoutMillis
                sockets.add(socket)
                try { workers.execute {
                    try { socket.use { serve(it) } }
                    finally { sockets.remove(socket) }
                } }
                catch (_: RejectedExecutionException) { sockets.remove(socket); socket.close() }
            } catch (_: SocketTimeoutException) {
                // Periodically check shutdown; never block the service indefinitely.
            } catch (_: Exception) {
                if (running.get()) stop("Le serveur réseau a été interrompu.")
            }
        }
    }

    private class Rejected(val status: Int) : Exception()
    private data class Request(val method: String, val path: String, val headers: Map<String, String>)

    private fun readRequest(input: InputStream): Request {
        var total = 0
        val headerDeadline = monotonicMillis() + readTimeoutMillis
        fun line(limit: Int): String {
            val bytes = ArrayList<Byte>()
            while (true) {
                if (monotonicMillis() >= headerDeadline) throw Rejected(408)
                val n = input.read()
                if (n < 0) throw Rejected(400)
                total++
                if (total > 16384 || bytes.size >= limit) throw Rejected(431)
                if (n == 10) {
                    if (bytes.lastOrNull() != 13.toByte()) throw Rejected(400)
                    bytes.removeAt(bytes.lastIndex)
                    return String(bytes.toByteArray(), StandardCharsets.US_ASCII)
                }
                if (n < 32 && n != 13 || n > 126) throw Rejected(400)
                bytes.add(n.toByte())
            }
        }
        val first = line(2048).split(' ')
        if (first.size != 3 || first[2] !in listOf("HTTP/1.0", "HTTP/1.1")) throw Rejected(400)
        if (first[0] !in listOf("GET", "HEAD")) throw Rejected(405)
        val headers = linkedMapOf<String, String>()
        while (true) {
            val header = line(8192)
            if (header.isEmpty()) break
            val colon = header.indexOf(':')
            if (colon < 1) throw Rejected(400)
            val key = header.substring(0, colon).lowercase()
            if (!key.matches(Regex("[a-z0-9-]+")) || key in headers) throw Rejected(400)
            headers[key] = header.substring(colon + 1).trim()
        }
        if (first[2] == "HTTP/1.1" && headers["host"].isNullOrBlank()) throw Rejected(400)
        if ("transfer-encoding" in headers || headers["content-length"]?.let { it.toLongOrNull() != 0L } == true)
            throw Rejected(400)
        return Request(first[0], first[1], headers)
    }

    private data class Range(val start: Long, val length: Long, val partial: Boolean)
    private fun range(value: String?, length: Long): Range {
        if (value == null) return Range(0, length, false)
        val parts = Regex("bytes=([0-9]*)-([0-9]*)").matchEntire(value)?.groupValues ?: throw Rejected(416)
        if (length == 0L || parts[1].isEmpty() && parts[2].isEmpty()) throw Rejected(416)
        if (parts[1].isEmpty()) {
            val suffix = parts[2].toLongOrNull()?.takeIf { it > 0 } ?: throw Rejected(416)
            val count = minOf(suffix, length)
            return Range(length - count, count, true)
        }
        val start = parts[1].toLongOrNull() ?: throw Rejected(416)
        val end = if (parts[2].isEmpty()) length - 1 else
            minOf(parts[2].toLongOrNull() ?: throw Rejected(416), length - 1)
        if (start >= length || end < start) throw Rejected(416)
        return Range(start, end - start + 1, true)
    }

    private fun serve(socket: Socket) {
        val id = sequence.incrementAndGet()
        val peer = socket.inetAddress.hostAddress ?: "unknown"
        var method = "REJECTED"
        var name = "rejected"
        var status = 400
        var expected = 0L
        var sent = 0L
        var responded = false
        fun event(phase: RequestPhase) {
            runCatching { onEvent(HttpEvent(id, System.currentTimeMillis(), peer, method,
                name, status, phase, expected, sent)) }
        }
        val output = BufferedOutputStream(socket.getOutputStream())
        fun header(code: Int, length: Long = 0, type: String = "application/octet-stream", extra: String = "") {
            status = code
            expected = length
            val reason = mapOf(200 to "OK", 206 to "Partial Content", 400 to "Bad Request",
                403 to "Forbidden", 404 to "Not Found", 405 to "Method Not Allowed",
                408 to "Request Timeout", 416 to "Range Not Satisfiable", 431 to "Request Header Fields Too Large")
            val text = "HTTP/1.1 $code ${reason[code] ?: "Error"}\r\n" +
                "Content-Length: $length\r\nContent-Type: $type\r\nConnection: close\r\n" +
                "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n" +
                (if (code == 405) "Allow: GET, HEAD\r\n" else "") + extra + "\r\n"
            responded = true
            event(RequestPhase.STARTED)
            output.write(text.toByteArray(StandardCharsets.US_ASCII))
            output.flush()
        }
        try {
            if (!isRunning) { stop("Session expirée."); return }
            if (!subnet.contains(socket.inetAddress) ||
                !(Ipv4Subnet.isPrivate(socket.inetAddress) || allowLoopbackForTests && socket.inetAddress.isLoopbackAddress))
                throw Rejected(403)
            val request = readRequest(BufferedInputStream(socket.getInputStream()))
            method = request.method
            val prefix = "/$session/"
            if (!request.path.startsWith('/') || request.path.any { it in "%?#\\" } || ".." in request.path)
                throw Rejected(400)
            if (!request.path.startsWith(prefix)) throw Rejected(404)
            val key = request.path.removePrefix(prefix)
            val resource = resources[key] ?: throw Rejected(404)
            name = key
            val range = try { range(request.headers["range"], resource.length) }
                catch (e: Rejected) {
                    header(e.status, extra = "Content-Range: bytes */${resource.length}\r\n")
                    return
                }
            // Open before sending 200, so a missing backing resource is not reported as success.
            resource.open().use { stream ->
                var skip = range.start
                while (skip > 0) {
                    val advanced = stream.skip(skip)
                    if (advanced > 0) skip -= advanced
                    else if (stream.read() >= 0) skip--
                    else throw Rejected(404)
                }
                val extra = "Accept-Ranges: bytes\r\n" + if (range.partial)
                    "Content-Range: bytes ${range.start}-${range.start + range.length - 1}/${resource.length}\r\n" else ""
                header(if (range.partial) 206 else 200, range.length, resource.contentType, extra)
                if (method == "GET") {
                    val buffer = ByteArray(65536)
                    var left = range.length
                    while (left > 0 && isRunning) {
                        val n = stream.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        left -= n
                        sent += n
                    }
                    output.flush()
                }
            }
        } catch (e: Rejected) {
            if (!responded) runCatching { header(e.status) }
        } catch (_: SocketTimeoutException) {
            if (!responded) runCatching { header(408) }
        } catch (_: Exception) {
            if (!responded) runCatching { header(404) }
        } finally {
            event(RequestPhase.FINISHED)
        }
    }

    private fun stop(reason: String) {
        if (!stopped.compareAndSet(false, true)) return
        running.set(false)
        runCatching { listener.close() }
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
        workers.shutdownNow()
        expiry.shutdownNow()
        runCatching { onStop(reason) }
    }
    override fun close() = stop("Serveur arrêté.")
}
