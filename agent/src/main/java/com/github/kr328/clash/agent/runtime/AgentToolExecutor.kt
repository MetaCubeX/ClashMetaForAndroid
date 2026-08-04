package com.github.kr328.clash.agent.runtime

import com.github.kr328.clash.agent.model.AgentToolExecutionResult
import com.github.kr328.clash.agent.tools.AgentToolSpec
import kotlinx.serialization.json.JsonObject

interface AgentToolExecutor {
    val tools: List<AgentToolSpec>

    suspend fun execute(name: String, arguments: JsonObject): AgentToolExecutionResult
}

fun interface AgentApprovalHandler {
    suspend fun approve(tool: AgentToolSpec, arguments: JsonObject, summary: String): Boolean
}
