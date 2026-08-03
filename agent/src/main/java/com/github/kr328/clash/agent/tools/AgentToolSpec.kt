package com.github.kr328.clash.agent.tools

import com.github.kr328.clash.agent.authorization.AgentOperationRisk
import kotlinx.serialization.json.JsonObject

data class AgentToolSpec(
    val name: String,
    val description: String,
    val risk: AgentOperationRisk,
    val parameters: JsonObject,
)
