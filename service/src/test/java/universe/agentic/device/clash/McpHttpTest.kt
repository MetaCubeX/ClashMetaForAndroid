package universe.agentic.device.clash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket

class McpHttpTest {
    @Test
    fun postRequiresBearerAndRejectsOtherMethods() {
        val server = McpHttpServer(0, "secret") { body ->
            McpProtocol.handle(body) { name, _ -> """{"tool":"$name"}""" }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.boundPort}/mcp"
            assertEquals(401, call(base, "POST", """{"jsonrpc":"2.0","id":1,"method":"ping"}"""))
            assertEquals(405, call(base, "GET", ""))
            val ping = call(
                base,
                "POST",
                """{"jsonrpc":"2.0","id":1,"method":"ping"}""",
                token = "secret",
            )
            assertEquals(200, ping)
            assertEquals(403, raw(server.boundPort, "https://example.com"))
            assertEquals(200, raw(server.boundPort, "http://[::1]:9"))
            val tool = text(
                base,
                """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"status"}}""",
            )
            assertTrue(tool.contains("status"))
            assertTrue(tool.contains("\"isError\":false"))
        } finally {
            server.stop()
        }
    }

    private fun text(url: String, body: String): String {
        val connection = open(url, "POST", body, "secret")
        return connection.inputStream.bufferedReader().readText()
    }

    private fun call(
        url: String,
        method: String,
        body: String,
        token: String? = null,
    ): Int {
        val connection = open(url, method, body, token)
        return connection.responseCode
    }

    private fun open(
        url: String,
        method: String,
        body: String,
        token: String?,
    ): java.net.HttpURLConnection {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = method
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
        if (method == "POST") {
            val bytes = body.toByteArray()
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
        }
        return connection
    }

    private fun raw(port: Int, origin: String): Int {
        val body = """{"jsonrpc":"2.0","id":1,"method":"ping"}"""
        val request = "POST /mcp HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nAuthorization: Bearer secret\r\nOrigin: $origin\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 3000
            socket.getOutputStream().apply {
                write(request.toByteArray())
                flush()
            }
            val status = socket.getInputStream().bufferedReader().readLine()
            return status.substringAfter(" ").substringBefore(" ").toInt()
        }
    }
}
