package com.github.kr328.clash.service.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        remotePath = "/clash-meta",
        clientId = "abcd1234",
    )

    @Test
    fun updateUsesOneConditionalPutOnTheDestination() {
        val calls = mutableListOf<HttpRequest>()
        val client = client { request ->
            calls += request
            HttpResponse(204, "No Content", "", mapOf("ETag" to "\"def\""))
        }
        val written = client.put(config, "_sync.json", "{}", ifMatch = "\"abc\"")
        assertEquals("\"def\"", written.etag)
        val request = calls.single()
        assertEquals("PUT", request.method)
        assertTrue(request.url.endsWith("/_sync.json"))
        assertEquals("\"abc\"", request.headers["If-Match"])
        assertEquals("{}", request.body)
        assertFalse(request.headers.containsKey("If-None-Match"))
    }

    @Test
    fun conflictNeverRetriesWithoutItsCondition() {
        val calls = mutableListOf<HttpRequest>()
        val client = client { request ->
            calls += request
            HttpResponse(412, "Precondition Failed", "", emptyMap())
        }
        try {
            client.put(config, "_sync.json", "{}", ifMatch = "\"abc\"")
            throw AssertionError("conflict")
        } catch (err: WebdavConflictException) {
            assertEquals(412, err.status)
        }
        assertEquals("PUT", calls.single().method)
        assertEquals("\"abc\"", calls.single().headers["If-Match"])
    }

    @Test
    fun createUsesIfNoneMatch() {
        val calls = mutableListOf<HttpRequest>()
        val client = client { request ->
            calls += request
            HttpResponse(201, "Created", "", emptyMap())
        }
        client.put(config, "profiles/new.json", "{}", createOnly = true)
        assertEquals("PUT", calls.single().method)
        assertEquals("*", calls.single().headers["If-None-Match"])
        assertFalse(calls.single().headers.containsKey("If-Match"))
    }

    @Test
    fun unsafeEtagsRejectUpdatesAndDeletesBeforeSending() {
        val calls = mutableListOf<HttpRequest>()
        val client = client { request ->
            calls += request
            throw AssertionError("unsafe request ${request.method}")
        }
        for (etag in listOf(null, "", "abc", "*", "W/\"abc\"", "\"abc\", \"other\"", "\"bad\nvalue\"")) {
            assertWebdavError("unsafe_etag") { client.put(config, "profiles/id.json", "{}", ifMatch = etag) }
            assertWebdavError("unsafe_etag") { client.delete(config, "profiles/id.json", ifMatch = etag) }
        }
        assertTrue(calls.isEmpty())
    }

    @Test
    fun lockedDestinationIsNotUnlockedOrRetried() {
        val calls = mutableListOf<HttpRequest>()
        val client = client { request ->
            calls += request
            HttpResponse(423, "Locked", "", emptyMap())
        }
        assertWebdavError("locked") { client.put(config, "_sync.json", "{}", ifMatch = "\"abc\"") }
        assertEquals("PUT", calls.single().method)
    }

    @Test
    fun retryAfterStopsSubsequentRequestsAcrossClientInstances() {
        val limited = config.copy(remotePath = "/retry-after-test")
        val calls = mutableListOf<HttpRequest>()
        val handler: (HttpRequest) -> HttpResponse = { request ->
            calls += request
            HttpResponse(429, "Too Many Requests", "", mapOf("Retry-After" to "120"))
        }
        assertWebdavError("http") { client(handler).get(limited, WEBDAV_META_FILE) }
        assertWebdavError("http") { client(handler).get(limited, WEBDAV_META_FILE) }
        assertEquals(1, calls.size)
    }

    @Test
    fun retryAfterParsesSecondsAndHttpDate() {
        assertEquals(125_000L, webdavRetryAfterDeadline("120", 5_000))
        assertEquals(784_111_777_000L, webdavRetryAfterDeadline("Sun, 06 Nov 1994 08:49:37 GMT", 5_000))
        assertNull(webdavRetryAfterDeadline("-1", 5_000))
        assertNull(webdavRetryAfterDeadline("invalid", 5_000))
        assertNull(webdavRetryAfterDeadline("Sun, 06 Nov 1994 08:49:37 GMT", 784_111_777_001L))
    }

    @Test
    fun snapshotOnlyReadsBothCollectionsAndMeta() {
        val calls = mutableListOf<HttpRequest>()
        val client = client { request ->
            calls += request
            if (request.method == "GET") HttpResponse(404, "Not Found", "", emptyMap())
            else HttpResponse(207, "Multi-Status", listing(request.url), emptyMap())
        }
        val snapshot = client.readSnapshot(config)
        assertEquals(listOf("PROPFIND", "PROPFIND", "GET"), calls.map { it.method })
        assertTrue(snapshot.root.isEmpty())
        assertTrue(snapshot.profiles.isEmpty())
        assertFalse(snapshot.meta.found)
    }

    @Test
    fun missingCollectionsAreReadWithoutCreatingThem() {
        val calls = mutableListOf<HttpRequest>()
        val client = client { request ->
            calls += request
            HttpResponse(404, "Not Found", "", emptyMap())
        }
        val snapshot = client.readSnapshot(config)
        assertEquals(listOf("PROPFIND", "PROPFIND", "GET"), calls.map { it.method })
        assertTrue(snapshot.profiles.isEmpty())
        assertFalse(snapshot.meta.found)
    }

    @Test
    fun invalidListingStopsSnapshotBeforeAnyWrites() {
        for (body in listOf(
            "<html/>",
            """<D:multistatus xmlns:D="DAV:"/>""",
            """<D:multistatus xmlns:D="DAV:"><D:response><D:href>/dav/""",
            """<D:multistatus xmlns:D="DAV:"><D:response/></D:multistatus>""",
            """<D:multistatus xmlns:D="DAV:"><D:response><D:href>/dav/file.json</D:href><D:status>HTTP/1.1 403 Forbidden</D:status></D:response></D:multistatus>""",
            """<!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><D:multistatus xmlns:D="DAV:"/>""",
        )) {
            val calls = mutableListOf<HttpRequest>()
            val client = client { request ->
                calls += request
                HttpResponse(207, "Multi-Status", body, emptyMap())
            }
            assertWebdavError("broken_list") { client.readSnapshot(config) }
            assertEquals(listOf("PROPFIND"), calls.map { it.method })
        }
    }

    @Test
    fun successfulNonDavResponseIsNotAnEmptyListing() {
        val client = client { HttpResponse(200, "OK", "<html/>", emptyMap()) }
        assertWebdavError("http") { client.list(config) }
    }

    @Test
    fun conditionProbeVerifiesAllConditionsAndCleansItsOwnObject() {
        val server = ConditionalServer()
        WebdavClient(server, server).verifyConditionalRequests(config)
        assertTrue(server.objects.isEmpty())
        assertEquals(listOf("PUT", "GET", "PUT", "PUT", "DELETE", "GET", "DELETE"), server.calls.map { it.method })
        assertTrue(server.calls.all { it.url.endsWith(".probe.tmp") })
    }

    @Test
    fun ignoredConditionsOnlyAffectTheProbeAndBlockRealPublication() {
        for (ignored in listOf("put", "create", "delete")) {
            val server = ConditionalServer(ignored)
            val client = WebdavClient(server, server)
            assertWebdavError("conditional_unsupported") {
                client.verifyConditionalRequests(config)
                client.put(config, "profiles/real.json", "user data", createOnly = true)
            }
            assertTrue(server.calls.all { it.url.endsWith(".probe.tmp") })
            assertTrue(server.objects.isEmpty())
        }
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

    private fun listing(url: String): String = """<D:multistatus xmlns:D="DAV:"><D:response><D:href>$url</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response></D:multistatus>"""

    private fun assertWebdavError(code: String, block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected WebdavException($code)")
        } catch (err: WebdavException) {
            assertEquals(code, err.code)
        }
    }

    private class ConditionalServer(private val ignored: String = "") : HttpTransport {
        val calls = mutableListOf<HttpRequest>()
        val objects = mutableMapOf<String, Pair<String, String>>()
        private var revision = 0

        override fun execute(request: HttpRequest): HttpResponse {
            calls.add(request)
            val previous = objects[request.url]
            return when (request.method) {
                "GET" -> if (previous == null) response(404) else response(200, previous.first, previous.second)
                "PUT" -> {
                    val mustCreate = request.headers["If-None-Match"] == "*"
                    val condition = request.headers["If-Match"]
                    when {
                        mustCreate && previous != null && ignored != "create" -> response(412)
                        condition != null && condition != previous?.second && ignored != "put" -> response(412)
                        else -> {
                            val etag = "\"${++revision}\""
                            objects[request.url] = request.body.orEmpty() to etag
                            response(201, etag = etag)
                        }
                    }
                }
                "DELETE" -> when {
                    previous == null -> response(404)
                    request.headers["If-Match"] != previous.second && ignored != "delete" -> response(412)
                    else -> {
                        objects.remove(request.url)
                        response(204)
                    }
                }
                else -> throw AssertionError("unexpected ${request.method}")
            }
        }

        private fun response(status: Int, body: String = "", etag: String? = null) = HttpResponse(
            status, "test", body, etag?.let { mapOf("ETag" to it) }.orEmpty(),
        )
    }
}
