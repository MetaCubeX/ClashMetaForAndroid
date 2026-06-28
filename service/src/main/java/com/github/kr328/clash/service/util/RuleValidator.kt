package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.RuleState

object RuleValidator {
    private val builtInPolicies = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS")

    /**
     * @param availablePolicies proxy-group names **and** proxy (node) names a rule may target —
     *        in mihomo a rule policy can be a single proxy, not only a group.
     * @param allowUnknownPolicy when the config pulls proxies from proxy-providers their names are
     *        not statically known, so an unknown policy can't be confidently rejected; the engine
     *        gate is the real check. Set true in that case to avoid false rejections.
     */
    fun validate(
        state: RuleState,
        availablePolicies: Set<String> = emptySet(),
        allowUnknownPolicy: Boolean = false,
    ) {
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
                availablePolicies.any { g -> g.equals(policy, true) }
            require(allowUnknownPolicy || knownPolicy) { "Unknown rule policy/group: $policy" }
        }
    }
}
