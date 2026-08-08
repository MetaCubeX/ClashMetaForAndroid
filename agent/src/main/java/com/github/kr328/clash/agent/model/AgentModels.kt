package com.github.kr328.clash.agent.model

import com.github.kr328.clash.agent.authorization.AgentAuthorizationMode
import kotlinx.serialization.Serializable

@Serializable
data class AgentProviderSettings(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "",
    val apiKey: String = "",
    val apiFormat: AgentApiFormat = AgentApiFormat.CHAT_COMPLETIONS,
    val authorizationMode: AgentAuthorizationMode = AgentAuthorizationMode.BALANCED,
    val maxToolRounds: Int = 12,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()
}

@Serializable
enum class AgentApiFormat {
    CHAT_COMPLETIONS,
    RESPONSES,
}

@Serializable
enum class AgentMessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
}

@Serializable
data class AgentConversationMessage(
    val id: String,
    val role: AgentMessageRole,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val toolName: String? = null,
    val isError: Boolean = false,
    val trace: List<AgentTraceEntry> = emptyList(),
    val running: Boolean = false,
)

/**
 * Lifecycle of a single step in a run.
 *
 * A tool call is one step that moves RUNNING -> SUCCESS/ERROR, rather than a
 * "started" line followed by a separate "finished" line, so the log reads as a
 * checklist instead of repeating every operation twice.
 */
@Serializable
enum class AgentTraceStatus {
    RUNNING,
    SUCCESS,
    WARNING,
    ERROR,
    INFO,
}

@Serializable
data class AgentTraceEntry(
    val kind: String,
    val summary: String,
    val detail: String = "",
    val toolName: String? = null,
    val status: AgentTraceStatus = AgentTraceStatus.INFO,
)

data class AgentToolExecutionResult(
    val success: Boolean,
    val content: String,
    val userSummary: String = content,
)

sealed interface AgentRunEvent {
    data class Thinking(val round: Int) : AgentRunEvent
    data class Streaming(val text: String) : AgentRunEvent
    data class ToolStarted(val name: String, val summary: String) : AgentRunEvent
    data class ToolFinished(val name: String, val success: Boolean, val summary: String) : AgentRunEvent
    data class Completed(val text: String) : AgentRunEvent
    data class Failed(val message: String, val recoverable: Boolean = true) : AgentRunEvent
}
