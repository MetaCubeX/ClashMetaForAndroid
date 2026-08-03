package com.github.kr328.clash.agent.authorization

object AgentAuthorizationPolicy {
    fun decide(
        mode: AgentAuthorizationMode,
        risk: AgentOperationRisk,
    ): AgentAuthorizationDecision = when (mode) {
        AgentAuthorizationMode.CAUTIOUS -> when (risk) {
            AgentOperationRisk.READ_ONLY -> AgentAuthorizationDecision.ALLOW
            else -> AgentAuthorizationDecision.REQUIRE_APPROVAL
        }

        AgentAuthorizationMode.BALANCED -> when (risk) {
            AgentOperationRisk.READ_ONLY,
            AgentOperationRisk.LOW,
            AgentOperationRisk.MEDIUM
            -> AgentAuthorizationDecision.ALLOW

            AgentOperationRisk.HIGH,
            AgentOperationRisk.CRITICAL
            -> AgentAuthorizationDecision.REQUIRE_APPROVAL
        }

        AgentAuthorizationMode.FULL_AUTO -> AgentAuthorizationDecision.ALLOW
    }
}
