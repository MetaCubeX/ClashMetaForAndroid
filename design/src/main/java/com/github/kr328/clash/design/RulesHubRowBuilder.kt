package com.github.kr328.clash.design

import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleProviderItem
import com.github.kr328.clash.service.model.RuleSource

enum class RulesHubFilter {
    ALL,
    MINE,
    SUBSCRIPTION,
    DISABLED,
}

object RulesHubRowBuilder {
    val builtInPolicies = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE", "GLOBAL")

    fun isTargetMissing(rule: RuleItem, knownPolicies: Set<String>): Boolean {
        if (rule.source != RuleSource.MANUAL || rule.deleted) return false
        val policy = rule.policy.trim()
        if (policy.isBlank()) return false
        return knownPolicies.none { it.equals(policy, ignoreCase = true) }
    }

    fun knownPolicies(proxyOptions: List<String>): Set<String> =
        (builtInPolicies + proxyOptions.map { it.trim() }.filter { it.isNotEmpty() })
            .map { it.uppercase() }
            .toSet()

    fun partitionRules(rules: List<RuleItem>): Pair<List<RuleItem>, List<RuleItem>> {
        val manual = rules
            .filter { it.source == RuleSource.MANUAL }
            .sortedBy { it.order }
        val provider = rules
            .filter { it.source == RuleSource.PROVIDER }
            .sortedBy { it.order }
        return manual to provider
    }

    fun filterRules(
        rules: List<RuleItem>,
        filter: RulesHubFilter,
        query: String,
        providers: Map<String, RuleProviderItem>,
    ): List<RuleItem> {
        val q = query.trim().lowercase()
        return rules.filter { item ->
            val passesFilter = when (filter) {
                RulesHubFilter.ALL -> true
                RulesHubFilter.MINE -> item.source == RuleSource.MANUAL
                RulesHubFilter.SUBSCRIPTION -> item.source == RuleSource.PROVIDER
                RulesHubFilter.DISABLED -> !item.enabled && !item.deleted
            }
            passesFilter && (q.isBlank() || matchesQuery(item, q, providers))
        }
    }

    fun matchesQuery(
        item: RuleItem,
        query: String,
        providers: Map<String, RuleProviderItem>,
    ): Boolean {
        val provider = item.providerName ?: providers[item.value]?.name.orEmpty()
        return sequenceOf(item.raw, item.type, item.value, item.policy, provider)
            .any { it.lowercase().contains(query) }
    }

    fun buildRuleLine(item: RuleItem): String {
        if (item.value.isBlank() && item.policy.isBlank() && item.raw.isNotBlank()) {
            return item.raw
        }
        return when {
            item.type.equals("MATCH", true) -> "MATCH,${item.policy}"
            item.type == "LEGACY" -> item.value
            else -> "${item.type},${item.value},${item.policy}"
        }
    }

    fun mergeOrderedRules(manual: List<RuleItem>, provider: List<RuleItem>): List<RuleItem> {
        return (manual + provider).mapIndexed { index, rule -> rule.copy(order = index) }
    }

    fun reorderManual(manual: List<RuleItem>, from: Int, to: Int): List<RuleItem> {
        if (from !in manual.indices || to !in manual.indices || from == to) return manual
        val mutable = manual.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        return mutable
    }
}
