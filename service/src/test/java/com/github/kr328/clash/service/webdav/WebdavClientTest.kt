package com.github.kr328.clash.service.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

class WebdavClientTest {
    private val config = WebdavConfig(
        url = "https://example.com/dav",
        username = "user",
        password = "secret",
        remotePath = "/sync/Configs/clash-meta",
        clientId = "abcd1234",
    )

    @Test
    fun unchangedDestinationSurvivesMoveIfMatchOnTheTempFile() {
        val calls = mutableListOf<HttpRequest>()
        val client = client { request ->
            calls += request
            when (calls.size) {
                1 -> HttpResponse(201, "Created", "", emptyMap())
                2 -> {
                    assertEquals("MOVE", request.method)
                    assertEquals("\"abc\"", request.headers["If-Match"])
                    HttpResponse(412, "Precondition Failed", "", emptyMap())
                }
                3 -> HttpResponse(207, "Multi-Status", """<D:getetag>"abc"</D:getetag>""", emptyMap())
                4 -> {
                    assertEquals("MOVE", request.method)
                    assertFalse(request.headers.keys.any { it.equals("If-Match", ignoreCase = true) })
                    HttpResponse(204, "No Content", "", mapOf("ETag" to "\"def\""))
                }
                else -> throw AssertionError("unexpected ${request.method}")
            }
        }
        val written = client.put(config, "_sync.json", "{}", ifMatch = "\"abc\"")
        assertEquals("\"def\"", written.etag)
        assertEquals(4, calls.size)
    }

    @Test
    fun changedDestinationIsAConflict() {
        val calls = mutableListOf<HttpRequest>()
        val client = client { request ->
            calls += request
            when (calls.size) {
                1 -> HttpResponse(201, "Created", "", emptyMap())
                2 -> HttpResponse(412, "Precondition Failed", "", emptyMap())
                3 -> HttpResponse(207, "Multi-Status", """<D:getetag>"other"</D:getetag>""", emptyMap())
                4 -> {
                    assertEquals("DELETE", request.method)
                    assertTrue(request.url.endsWith(".tmp"))
                    HttpResponse(204, "No Content", "", emptyMap())
                }
                else -> throw AssertionError("unexpected ${request.method}")
            }
        }
        try {
            client.put(config, "_sync.json", "{}", ifMatch = "\"abc\"")
            throw AssertionError("conflict")
        } catch (err: WebdavConflictException) {
            assertEquals(412, err.status)
        }
        assertEquals("DELETE", calls.last().method)
    }

    @Test
    fun deleteSendsIfMatchAndTreats412AsConflict() {
        val calls = mutableListOf<HttpRequest>()
        val client = client { request ->
            calls += request
            assertEquals("\"abc\"", request.headers["If-Match"])
            HttpResponse(412, "Precondition Failed", "", emptyMap())
        }
        try {
            client.delete(config, "profiles/id.json", ifMatch = "\"abc\"")
            throw AssertionError("conflict")
        } catch (_: WebdavConflictException) {
        }
        assertEquals("DELETE", calls.single().method)
        val gone = client { HttpResponse(404, "Not Found", "", emptyMap()) }
        gone.delete(config, "profiles/id.json", ifMatch = "\"abc\"")
    }

    @Test
    fun probeAcceptsWebdavResponsesOnly() {
        assertTrue(isWebdavEndpointResponse(HttpResponse(207, "Multi-Status", "", emptyMap())))
        assertTrue(isWebdavEndpointResponse(HttpResponse(200, "OK", "", mapOf("DAV" to "1, 2"))))
        assertTrue(
            isWebdavEndpointResponse(
                HttpResponse(401, "Unauthorized", "", mapOf("WWW-Authenticate" to "Basic realm=\"alist\"")),
            ),
        )
        assertFalse(isWebdavEndpointResponse(HttpResponse(401, "Unauthorized", "", emptyMap())))
        assertFalse(isWebdavEndpointResponse(HttpResponse(200, "OK", "hello", emptyMap())))
        assertFalse(isWebdavEndpointResponse(HttpResponse(404, "Not Found", "", emptyMap())))
    }

    @Test
    fun lanProbeDoesNotSendCredentials() {
        val seen = AtomicReference("")
        val url = serve(
            "HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"alist\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
            seen,
        )
        assertTrue(WebdavClient().reachable("$url/dav/"))
        assertFalse(seen.get().contains("Authorization"))
        assertTrue(seen.get().contains("PROPFIND /dav/"))
    }

    @Test
    fun plainHttpIsNotALanWebdavServer() {
        val seen = AtomicReference("")
        val url = serve(
            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok",
            seen,
        )
        assertFalse(WebdavClient().reachable("$url/"))
        assertTrue(seen.get().contains("PROPFIND /"))
        assertFalse(seen.get().contains("Authorization"))
    }

    @Test
    fun privateHttpReadsAChunkedWebdavBody() {
        val url = serve(
            "HTTP/1.1 207 Multi-Status\r\nDAV: 1, 2\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n5\r\nhello\r\n0\r\n\r\n",
            AtomicReference(""),
        )
        val response = PrivateHttpTransport(2_000, 2_000).execute(
            HttpRequest("PROPFIND", "$url/dav/", emptyMap(), "<a/>"),
        )
        assertEquals(207, response.status)
        assertEquals("Multi-Status", response.statusText)
        assertEquals("hello", response.body)
        assertEquals("1, 2", response.header("DAV"))
    }

    @Test
    fun privateHttpRejectsAPublicHost() {
        try {
            PrivateHttpTransport(500, 500).execute(HttpRequest("GET", "http://example.com/dav", emptyMap()))
            throw AssertionError("public http")
        } catch (err: WebdavException) {
            assertEquals("lan_http", err.code)
        }
    }

    private fun serve(response: String, seen: AtomicReference<String>): String {
        val server = ServerSocket(0)
        server.soTimeout = 5_000
        val url = "http://127.0.0.1:${server.localPort}"
        val thread = Thread {
            try {
                server.accept().use { socket ->
                    seen.set(readHttpRequest(socket.getInputStream()))
                    socket.getOutputStream().write(response.toByteArray(Charsets.ISO_8859_1))
                    socket.getOutputStream().flush()
                }
            } finally {
                server.close()
            }
        }
        thread.isDaemon = true
        thread.start()
        return url
    }

    private fun readHttpRequest(input: InputStream): String {
        val text = StringBuilder()
        while (true) {
            val line = readTestLine(input)
            text.append(line).append('\n')
            if (line.isEmpty()) break
        }
        val length = Regex("(?i)content-length: (\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (length > 0) {
            val body = ByteArray(length)
            var off = 0
            while (off < length) {
                val n = input.read(body, off, length - off)
                if (n < 0) break
                off += n
            }
            text.append(body.toString(Charsets.UTF_8))
        }
        return text.toString()
    }

    private fun readTestLine(input: InputStream): String {
        val out = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0 || b == '\n'.code) break
            if (b != '\r'.code) out.append(b.toChar())
        }
        return out.toString()
    }

    private fun client(handler: (HttpRequest) -> HttpResponse): WebdavClient {
        val transport = object : HttpTransport {
            override fun execute(request: HttpRequest): HttpResponse = handler(request)
        }
        return WebdavClient(transport, transport)
    }
}
