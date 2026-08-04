package com.github.kr328.clash.agent.runtime

import com.github.kr328.clash.agent.authorization.AgentAuthorizationMode
import com.github.kr328.clash.agent.model.AgentProviderSettings
import com.github.kr328.clash.agent.model.AgentRunEvent
import com.github.kr328.clash.agent.model.AgentToolExecutionResult
import com.github.kr328.clash.agent.tools.AgentExecutableTools
import com.github.kr328.clash.agent.tools.AgentToolSpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentEngineTest {
    @Test
    fun executesToolLoopAndFinishesWithoutApprovalInFullAuto() = runBlocking {
        val responses = listOf(
            """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"runtime_set_mode","arguments":"{\"mode\":\"rule\"}"}}]}}]}""",
            """{"choices":[{"message":{"role":"assistant","content":"模式已经切换完成。"}}]}""",
        )
        withServer(responses) { baseUrl, requests ->
            var executed: JsonObject? = null
            var approvalCalled = false
            val events = mutableListOf<AgentRunEvent>()
            val tool = AgentExecutableTools.all.single { it.name == "runtime_set_mode" }
            val executor = object : AgentToolExecutor {
                override val tools: List<AgentToolSpec> = listOf(tool)
                override suspend fun execute(name: String, arguments: JsonObject): AgentToolExecutionResult {
                    assertEquals("runtime_set_mode", name)
                    executed = arguments
                    return AgentToolExecutionResult(true, "{\"mode\":\"rule\"}", "已切换模式")
                }
            }
            val result = AgentEngine().run(
                AgentProviderSettings(baseUrl, "test-model", "", AgentAuthorizationMode.FULL_AUTO),
                emptyList(),
                "切换到规则模式",
                executor,
                AgentApprovalHandler { _, _, _ -> approvalCalled = true; true },
            ) { events += it }

            assertEquals("模式已经切换完成。", result)
            assertEquals("rule", executed?.get("mode")?.toString()?.trim('"'))
            assertFalse(approvalCalled)
            assertEquals(2, requests().size)
            assertTrue(requests()[1].contains("\"role\":\"tool\""))
            assertTrue(events.any { it is AgentRunEvent.ToolFinished && it.success })
            assertTrue(events.last() is AgentRunEvent.Completed)
        }
    }

    private suspend fun withServer(
        responses: List<String>,
        block: suspend (String, () -> List<String>) -> Unit,
    ) {
        val requests = mutableListOf<String>()
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val worker = thread(name = "agent-engine-test-server") {
            responses.forEach { response ->
                server.accept().use { socket ->
                    val input = socket.getInputStream().buffered()
                    val headers = linkedMapOf<String, String>()
                    input.readHttpLine()
                    while (true) {
                        val line = input.readHttpLine() ?: break
                        if (line.isEmpty()) break
                        val separator = line.indexOf(':')
                        if (separator > 0) headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
                    }
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = input.readNBytes(length).toString(Charsets.UTF_8)
                    synchronized(requests) { requests += body }
                    val bytes = response.toByteArray()
                    socket.getOutputStream().buffered().use { output ->
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(bytes)
                    }
                }
            }
        }
        try {
            block("http://127.0.0.1:${server.localPort}/v1") { synchronized(requests) { requests.toList() } }
        } finally {
            server.close()
            worker.join(2_000)
        }
    }

    private fun java.io.BufferedInputStream.readHttpLine(): String? {
        val bytes = ArrayList<Byte>()
        while (true) {
            val value = read()
            if (value < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(Charsets.US_ASCII)
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes += value.toByte()
        }
        return bytes.toByteArray().toString(Charsets.US_ASCII)
    }
}
