package universe.agentic.device.clash

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Locale

class McpHttpServer(
    private val port: Int,
    private val token: String,
    private val handle: (String) -> McpProtocol.Reply,
) {
    private val server = ServerSocket()
    @Volatile private var running = false

    val boundPort: Int
        get() = server.localPort

    fun start() {
        server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
        running = true
        Thread({
            while (running) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                client.soTimeout = 15_000
                Thread {
                    try {
                        serve(client)
                    } finally {
                        runCatching { client.close() }
                    }
                }.apply { isDaemon = true }.start()
            }
        }, "mcp-accept").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        runCatching { server.close() }
    }

    private fun serve(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val head = readHeaders(input) ?: return
        val request = head.lineSequence().firstOrNull()?.split(" ") ?: return
        if (request.size < 2) return
        val method = request[0]
        val path = request[1].substringBefore("?")
        val headers = parseHeaders(head)
        if (path != McpProtocol.PATH) {
            write(socket, 404, "not found")
            return
        }
        if (!method.equals("POST", ignoreCase = true)) {
            write(socket, 405, "method not allowed", extra = listOf("Allow: POST"))
            return
        }
        val origin = headers["origin"]
        if (origin != null && !localOrigin(origin)) {
            write(socket, 403, "origin rejected")
            return
        }
        if (!bearerMatches(headers["authorization"], token)) {
            write(socket, 401, "unauthorized")
            return
        }
        val length = headers["content-length"]?.toIntOrNull()
        if (length == null || length < 0) {
            write(socket, 411, "length required")
            return
        }
        if (length > MAX_BODY) {
            write(socket, 413, "body too large")
            return
        }
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        if (read != length) {
            write(socket, 400, "truncated body")
            return
        }
        val reply = handle(body.toString(Charsets.UTF_8))
        if (reply.body == null) {
            write(socket, reply.status, "")
        } else {
            write(socket, reply.status, reply.body, json = true)
        }
    }

    private fun readHeaders(input: BufferedInputStream): String? {
        val buffer = StringBuilder()
        while (buffer.length < 16 * 1024) {
            val value = input.read()
            if (value < 0) return null
            buffer.append(value.toChar())
            if (buffer.endsWith("\r\n\r\n")) return buffer.toString()
        }
        return null
    }

    private fun parseHeaders(head: String): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        head.lineSequence().drop(1).forEach { line ->
            val index = line.indexOf(':')
            if (index <= 0) return@forEach
            headers[line.substring(0, index).trim().lowercase(Locale.US)] = line.substring(index + 1).trim()
        }
        return headers
    }

    private fun localOrigin(origin: String): Boolean {
        val authority = origin.substringAfter("://", "").substringBefore("/").trim()
        if (authority.isEmpty()) return false
        val host = if (authority.startsWith("[")) {
            val inside = authority.substringBefore("]")
            if (inside.length == authority.length) "" else "$inside]"
        } else {
            authority.substringBefore(":")
        }
        return host == "127.0.0.1" || host.equals("localhost", ignoreCase = true) || host == "[::1]"
    }

    private fun write(
        socket: Socket,
        status: Int,
        body: String,
        json: Boolean = false,
        extra: List<String> = emptyList(),
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            202 -> "Accepted"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            411 -> "Length Required"
            413 -> "Payload Too Large"
            else -> "Error"
        }
        val type = if (json) "application/json; charset=utf-8" else "text/plain; charset=utf-8"
        val output = BufferedOutputStream(socket.getOutputStream())
        val text = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $type\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            extra.forEach { append(it).append("\r\n") }
            append("\r\n")
        }
        output.write(text.toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    companion object {
        private const val MAX_BODY = 256 * 1024

        fun bearerMatches(header: String?, token: String): Boolean {
            if (token.isEmpty() || header.isNullOrBlank()) return false
            val presented = header.trim().let { value ->
                if (value.startsWith("Bearer ", ignoreCase = true)) value.substring(7).trim() else ""
            }
            val left = presented.toByteArray(Charsets.UTF_8)
            val right = token.toByteArray(Charsets.UTF_8)
            return MessageDigest.isEqual(left, right)
        }
    }
}
