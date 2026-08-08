package com.github.kr328.clash.agent.runtime

import com.github.kr328.clash.agent.model.AgentApiFormat
import com.github.kr328.clash.agent.model.AgentConversationMessage
import com.github.kr328.clash.agent.model.AgentMessageRole
import com.github.kr328.clash.agent.model.AgentProviderSettings
import com.github.kr328.clash.agent.model.AgentToolExecutionResult
import com.github.kr328.clash.agent.model.AgentTraceEntry
import com.github.kr328.clash.agent.model.AgentTraceStatus
import com.github.kr328.clash.agent.tools.AgentToolSpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The model used to see only the prose of earlier turns, so a turn that merely
 * claimed "配置已更新" became evidence that the write had happened. Every assistant
 * turn now replays with the app's record of what actually executed.
 */
class AgentEngineHistoryTest {
    private fun assistant(content: String, trace: List<AgentTraceEntry>) =
        AgentConversationMessage(
            id = "a1",
            role = AgentMessageRole.ASSISTANT,
            content = content,
            trace = trace,
        )

    @Test
    fun aTurnThatCalledNothingIsReplayedAsHavingChangedNothing() = runBlocking {
        val sent = capturePrompt(assistant("配置已更新并通过校验。", emptyList()))

        assertTrue(sent.contains(AgentEngine.EXECUTION_RECORD_PREFIX), sent)
        assertTrue(sent.contains("no tools were called"), sent)
        assertTrue(sent.contains("the claim was false"), sent)
    }

    @Test
    fun executedToolsAreReplayedWithTheirRealOutcome() = runBlocking {
        val sent = capturePrompt(
            assistant(
                "已经帮你改好了。",
                listOf(
                    AgentTraceEntry(
                        kind = "tool",
                        summary = "读取配置",
                        toolName = "profile_read_config",
                        status = AgentTraceStatus.SUCCESS,
                    ),
                    AgentTraceEntry(
                        kind = "tool",
                        summary = "修改并应用配置",
                        toolName = "profile_replace_config",
                        status = AgentTraceStatus.ERROR,
                    ),
                ),
            )
        )

        assertTrue(sent.contains("profile_read_config succeeded"), sent)
        assertTrue(sent.contains("profile_replace_config FAILED"), sent)
    }

    /** Runs one turn against a stub server and returns the request body it saw. */
    private suspend fun capturePrompt(vararg history: AgentConversationMessage): String {
        var captured = ""
        val response = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""

        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val worker = thread(name = "agent-history-test-server") {
            server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                var length = 0
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", true)) {
                        length = line.substringAfter(':').trim().toInt()
                    }
                }
                val body = CharArray(length)
                input.read(body, 0, length)
                captured = String(body)

                socket.getOutputStream().apply {
                    write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${response.toByteArray().size}\r\n\r\n$response")
                            .toByteArray()
                    )
                    flush()
                }
            }
        }

        try {
            AgentEngine().run(
                settings = AgentProviderSettings(
                    baseUrl = "http://127.0.0.1:${server.localPort}",
                    model = "test-model",
                    apiKey = "",
                    apiFormat = AgentApiFormat.CHAT_COMPLETIONS,
                ),
                history = history.toList(),
                prompt = "再确认一次",
                executor = object : AgentToolExecutor {
                    override val tools: List<AgentToolSpec> = emptyList()
                    override suspend fun execute(name: String, arguments: JsonObject) =
                        AgentToolExecutionResult(false, "", "")
                },
                approvalHandler = AgentApprovalHandler { _, _, _ -> false },
                emit = {},
            )
        } finally {
            worker.join(5_000)
            server.close()
        }

        return captured
    }
}
