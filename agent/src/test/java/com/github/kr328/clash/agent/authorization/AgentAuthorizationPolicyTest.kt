package com.github.kr328.clash.agent.authorization

import kotlin.test.Test
import kotlin.test.assertEquals

class AgentAuthorizationPolicyTest {
    @Test
    fun cautiousOnlyAutomaticallyAllowsReads() {
        AgentOperationRisk.entries.forEach { risk ->
            val expected = if (risk == AgentOperationRisk.READ_ONLY) {
                AgentAuthorizationDecision.ALLOW
            } else {
                AgentAuthorizationDecision.REQUIRE_APPROVAL
            }

            assertEquals(expected, AgentAuthorizationPolicy.decide(AgentAuthorizationMode.CAUTIOUS, risk))
        }
    }

    @Test
    fun balancedRequiresApprovalForHighImpactOperations() {
        assertEquals(
            AgentAuthorizationDecision.ALLOW,
            AgentAuthorizationPolicy.decide(AgentAuthorizationMode.BALANCED, AgentOperationRisk.MEDIUM),
        )
        assertEquals(
            AgentAuthorizationDecision.REQUIRE_APPROVAL,
            AgentAuthorizationPolicy.decide(AgentAuthorizationMode.BALANCED, AgentOperationRisk.HIGH),
        )
        assertEquals(
            AgentAuthorizationDecision.REQUIRE_APPROVAL,
            AgentAuthorizationPolicy.decide(AgentAuthorizationMode.BALANCED, AgentOperationRisk.CRITICAL),
        )
    }

    @Test
    fun fullAutoAllowsEveryExposedOperation() {
        AgentOperationRisk.entries.forEach { risk ->
            assertEquals(
                AgentAuthorizationDecision.ALLOW,
                AgentAuthorizationPolicy.decide(AgentAuthorizationMode.FULL_AUTO, risk),
            )
        }
    }
}
