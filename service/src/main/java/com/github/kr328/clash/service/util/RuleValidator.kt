package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.RuleState

object RuleValidator {
    private val builtInPolicies = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS")

    fun validate(state: RuleState, availableProxyGroups: Set<String> = emptySet()) {
        val duplicateProvider = state.providers
            .map { it.name.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
        require(duplicateProvider == null) { "Duplicate provider name: ${duplicateProvider!!.key}" }

        state.providers.forEach {
            require(it.name.isNotBlank()) { "Provider name is empty" }
            require(it.url.isNotBlank()) { "Provider URL is empty for ${it.name}" }
        }

        state.rules.filter { it.enabled && !it.deleted }.forEach {
            require(it.type.isNotBlank()) { "Rule type is empty" }
            // Opaque/logical types (AND/OR/NOT/SUB-RULE/SCRIPT) carry their whole
            // payload in `raw`; value AND policy are legitimately empty for them.
            // Validating those fields would reject a perfectly valid subscription
            // rule and fail the entire save. Only their raw line must be present.
            if (RuleMapper.isOpaqueType(it.type)) {
                require(it.raw.isNotBlank()) { "Rule line is empty for type ${it.type}" }
                return@forEach
            }
            if (!it.type.equals("MATCH", true)) {
                require(it.value.isNotBlank()) { "Rule value is empty for type ${it.type}" }
            }
            require(it.policy.isNotBlank()) { "Rule policy is empty" }
            val policy = it.policy.trim()
            val knownPolicy = builtInPolicies.any { b -> b.equals(policy, true) } ||
                availableProxyGroups.any { g -> g.equals(policy, true) }
            require(knownPolicy) { "Unknown rule policy/group: $policy" }
        }
    }
}
