package com.github.kr328.clash.agent.settings

import android.content.Context
import com.github.kr328.clash.agent.model.AgentConversationMessage
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AgentConversationStore(context: Context) {
    private val preferences = context.getSharedPreferences("agent_conversation", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<AgentConversationMessage> = runCatching {
        json.decodeFromString(
            ListSerializer(AgentConversationMessage.serializer()),
            preferences.getString(KEY_MESSAGES, "[]").orEmpty(),
        )
    }.getOrDefault(emptyList())

    fun save(messages: List<AgentConversationMessage>) {
        val bounded = messages.takeLast(MAX_MESSAGES)
        preferences.edit().putString(
            KEY_MESSAGES,
            json.encodeToString(ListSerializer(AgentConversationMessage.serializer()), bounded),
        ).apply()
    }

    fun clear() = preferences.edit().remove(KEY_MESSAGES).apply()

    companion object {
        private const val KEY_MESSAGES = "messages"
        private const val MAX_MESSAGES = 120
    }
}
