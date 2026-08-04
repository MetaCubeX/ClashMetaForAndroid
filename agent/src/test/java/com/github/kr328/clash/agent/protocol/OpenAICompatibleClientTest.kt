package com.github.kr328.clash.agent.protocol

import com.github.kr328.clash.agent.model.AgentProviderSettings
import com.github.kr328.clash.agent.tools.AgentExecutableTools
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAICompatibleClientTest {
    @Test
    fun parsesStreamingTextAndFragmentedToolCalls() = runBlocking {
        val response = """
            data: {"choices":[{"delta":{"content":"好的"}}]}

            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"runtime_","arguments":"{\"mo"}}]}}]}

            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"set_mode","arguments":"de\":\"rule\"}"}}]}}]}

            data: [DONE]
        """.trimIndent()
        withServer("text/event-stream", response) { baseUrl, captured ->
            var streamed = ""
            val completion = OpenAICompatibleClient().complete(
                AgentProviderSettings(baseUrl = baseUrl, model = "test", apiKey = "secret"),
                buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", "test") }) },
                AgentExecutableTools.all,
            ) { streamed = it }
            assertEquals("好的", completion.content)
            assertEquals("好的", streamed)
            assertEquals("runtime_set_mode", completion.toolCalls.single().name)
            assertEquals("{\"mode\":\"rule\"}", completion.toolCalls.single().arguments)
            assertTrue(captured().contains("\"tools\""))
            assertTrue(captured().contains("Bearer secret"))
        }
    }

    @Test
    fun parsesNonStreamingFallback() = runBlocking {
        val response = """{"choices":[{"message":{"role":"assistant","content":"完成"}}]}"""
        withServer("application/json", response) { baseUrl, _ ->
            val completion = OpenAICompatibleClient().complete(
                AgentProviderSettings(baseUrl = baseUrl, model = "test", apiKey = ""),
                buildJsonArray {},
                emptyList(),
            ) {}
            assertEquals("完成", completion.content)
            assertTrue(completion.toolCalls.isEmpty())
        }
    }

    private suspend fun withServer(
        contentType: String,
        response: String,
        block: suspend (String, () -> String) -> Unit,
    ) {
        var request = ""
        var authorization = ""
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = thread(name = "agent-test-server") {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                val headers = linkedMapOf<String, String>()
                reader.readLine()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
                }
                authorization = headers["authorization"].orEmpty()
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                request = CharArray(length).also { chars ->
                    var offset = 0
                    while (offset < chars.size) {
                        val read = reader.read(chars, offset, chars.size - offset)
                        if (read < 0) break
                        offset += read
                    }
                }.concatToString()
                val bytes = response.toByteArray()
                socket.getOutputStream().buffered().use { output ->
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(bytes)
                }
            }
        }
        try {
            block("http://127.0.0.1:${server.localPort}/v1") { "$authorization\n$request" }
        } finally {
            server.close()
            worker.join(2_000)
        }
    }
}
