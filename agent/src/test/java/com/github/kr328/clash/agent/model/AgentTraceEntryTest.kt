package com.github.kr328.clash.agent.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentTraceEntryTest {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Conversations persisted before `status` existed must keep loading, or an
     * upgrade would wipe the user's chat history.
     */
    @Test
    fun legacyTraceEntriesWithoutStatusStillDeserialize() {
        val stored = """
            [{"id":"1","role":"ASSISTANT","content":"done","createdAt":0,
              "trace":[{"kind":"tool_start","summary":"读取配置"}]}]
        """.trimIndent()

        val messages = json.decodeFromString(
            ListSerializer(AgentConversationMessage.serializer()),
            stored,
        )

        val entry = messages.single().trace.single()
        assertEquals("读取配置", entry.summary)
        assertEquals(AgentTraceStatus.INFO, entry.status)
    }

    @Test
    fun statusSurvivesARoundTrip() {
        val original = AgentTraceEntry(
            kind = "tool",
            summary = "切换代理节点",
            toolName = "proxy_select",
            status = AgentTraceStatus.ERROR,
        )

        val restored = json.decodeFromString(
            AgentTraceEntry.serializer(),
            json.encodeToString(AgentTraceEntry.serializer(), original),
        )

        assertEquals(original, restored)
    }
}
