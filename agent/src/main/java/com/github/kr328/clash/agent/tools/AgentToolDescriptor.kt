package com.github.kr328.clash.agent.tools

import com.github.kr328.clash.agent.authorization.AgentOperationRisk

data class AgentToolDescriptor(
    val name: String,
    val description: String,
    val risk: AgentOperationRisk,
)
