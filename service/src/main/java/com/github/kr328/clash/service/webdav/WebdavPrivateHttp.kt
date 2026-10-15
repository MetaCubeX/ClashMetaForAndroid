package com.github.kr328.clash.service.webdav

import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Public https stays on OkHttp, which follows the network security config.
 * Private http uses a direct socket so the rest of the app can keep cleartext blocked.
 */
internal fun webdavHttpTransport(
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    callTimeoutMs: Int,
): HttpTransport {
    val secure = OkHttpTransport(
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout(callTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build(),
    )
    return RoutingHttpTransport(secure, PrivateHttpTransport(connectTimeoutMs, readTimeoutMs))
}

internal class RoutingHttpTransport(
    private val secure: HttpTransport,
    private val cleartext: HttpTransport,
) : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse {
        val uri = try {
            URI(request.url)
        } catch (_: Exception) {
            return secure.execute(request)
        }
        val host = uri.host.orEmpty()
        val scheme = uri.scheme?.lowercase()
        return if (scheme == "http" && isPrivateWebdavHost(host)) {
            cleartext.execute(request)
        } else {
            secure.execute(request)
        }
    }
}

internal class PrivateHttpTransport(
    private val connectTimeoutMs: Int,
    private val readTimeoutMs: Int,
) : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse {
        val uri = try {
            URI(request.url)
        } catch (_: Exception) {
            throw WebdavException("bad_url")
        }
        val host = uri.host?.trim().orEmpty().removePrefix("[").removeSuffix("]")
        if (uri.scheme?.lowercase() != "http" || !isPrivateWebdavHost(host)) {
            throw WebdavException("lan_http")
        }
        if (!request.method.matches(Regex("[A-Z]+"))) throw WebdavException("bad_url")
        val port = if (uri.port == -1) 80 else uri.port
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = readTimeoutMs
            val output = socket.getOutputStream()
            output.write(encodeRequest(request, uri, host, port))
            output.flush()
            return readResponse(socket.getInputStream())
        } catch (err: WebdavException) {
            throw err
        } catch (err: SocketTimeoutException) {
            throw IOException("timeout", err)
        } finally {
            socket.close()
        }
    }

    private fun encodeRequest(request: HttpRequest, uri: URI, host: String, port: Int): ByteArray {
        val payload = request.body?.toByteArray(Charsets.UTF_8)
        val target = buildString {
            append(uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/")
            if (!uri.rawQuery.isNullOrEmpty()) {
                append('?')
                append(uri.rawQuery)
            }
        }
        val hostHeader = if (host.contains(':')) "[$host]:$port" else "$host:$port"
        val text = buildString {
            append(request.method).append(' ').append(target).append(" HTTP/1.1\r\n")
            append("Host: ").append(headerValue(hostHeader)).append("\r\n")
            append("Connection: close\r\n")
            for ((name, value) in request.headers) {
                if (name.equals("Host", true) || name.equals("Content-Length", true) || name.equals("Connection", true)) {
                    continue
                }
                append(headerValue(name)).append(": ").append(headerValue(value)).append("\r\n")
            }
            if (payload != null) {
                append("Content-Length: ").append(payload.size).append("\r\n")
            }
            append("\r\n")
        }
        val head = text.toByteArray(Charsets.ISO_8859_1)
        if (payload == null) return head
        return head + payload
    }

    private fun headerValue(value: String): String {
        if (value.any { it == '\r' || it == '\n' || it == '\u0000' }) throw WebdavException("bad_url")
        return value
    }

    private fun readResponse(input: InputStream): HttpResponse {
        var statusLine = readLine(input)
        var hops = 0
        while (hops < 3 && statusCode(statusLine) == 100) {
            while (readLine(input).isNotEmpty()) Unit
            statusLine = readLine(input)
            hops += 1
        }
        val status = statusCode(statusLine)
        val statusText = statusLine.substringAfter(status.toString(), "").trim()
        val headers = linkedMapOf<String, String>()
        var count = 0
        while (true) {
            val line = readLine(input)
            if (line.isEmpty()) break
            count += 1
            if (count > 64) throw IOException("too many headers")
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim()
            val value = line.substring(colon + 1).trim()
            if (!headers.keys.any { it.equals(name, true) }) headers[name] = value
        }
        val body = readBody(input, headers)
        return HttpResponse(status, statusText, body.toString(Charsets.UTF_8), headers)
    }

    private fun statusCode(line: String): Int {
        val parts = line.split(' ')
        if (parts.size < 2 || !parts[0].startsWith("HTTP/")) throw IOException("bad status")
        return parts[1].toIntOrNull() ?: throw IOException("bad status")
    }

    private fun readBody(input: InputStream, headers: Map<String, String>): ByteArray {
        val encoding = header(headers, "Transfer-Encoding").orEmpty()
        if (encoding.contains("chunked", true)) return readChunked(input)
        val lengthHeader = header(headers, "Content-Length")
        if (lengthHeader != null) {
            val length = lengthHeader.trim().toLongOrNull() ?: throw IOException("bad length")
            if (length < 0 || length > WEBDAV_MAX_PROFILE_BYTES) throw WebdavException("too_large")
            if (length == 0L) return ByteArray(0)
            val buf = ByteArray(length.toInt())
            readFully(input, buf)
            return buf
        }
        return readUntilEof(input)
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val size = readLine(input).substringBefore(';').trim().toIntOrNull(16)
                ?: throw IOException("bad chunk")
            if (size < 0) throw IOException("bad chunk")
            if (size == 0) {
                while (readLine(input).isNotEmpty()) Unit
                break
            }
            if (out.size() + size > WEBDAV_MAX_PROFILE_BYTES) throw WebdavException("too_large")
            val buf = ByteArray(size)
            readFully(input, buf)
            out.write(buf)
            val end = readLine(input)
            if (end.isNotEmpty()) throw IOException("bad chunk")
        }
        return out.toByteArray()
    }

    private fun readUntilEof(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > WEBDAV_MAX_PROFILE_BYTES) throw WebdavException("too_large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw IOException("closed")
            off += n
        }
    }

    private fun readLine(input: InputStream): String {
        val out = ByteArrayOutputStream()
        while (out.size() <= 8192) {
            val b = input.read()
            if (b < 0) break
            if (b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
        }
        if (out.size() > 8192) throw IOException("header too long")
        return out.toString(Charsets.ISO_8859_1)
    }

    private fun header(headers: Map<String, String>, name: String): String? {
        return headers.entries.firstOrNull { it.key.equals(name, true) }?.value
    }
}
