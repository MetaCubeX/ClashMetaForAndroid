package com.github.kr328.clash.design

import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleProviderItem
import com.github.kr328.clash.service.model.RuleSource
import com.github.kr328.clash.service.model.RuleState

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

    fun buildRuleExpression(item: RuleItem): String {
        if (item.value.isBlank() && item.policy.isBlank() && item.raw.isNotBlank()) {
            return item.raw
        }
        return when {
            item.type.equals("MATCH", true) -> "MATCH"
            item.type == "LEGACY" -> item.value
            else -> "${item.type},${item.value}"
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

    fun reorderManualById(manual: List<RuleItem>, fromId: String, toId: String): List<RuleItem> {
        val from = manual.indexOfFirst { it.id == fromId }
        val to = manual.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0) return manual
        return reorderManual(manual, from, to)
    }

    fun buildSummaryLine(
        context: android.content.Context,
        manualShown: Int,
        manualTotal: Int,
        providerShown: Int,
        providerTotal: Int,
    ): String = context.getString(
        R.string.rules_hub_summary_accent_fmt,
        manualShown,
        manualTotal,
        providerShown,
        providerTotal,
    )
}

enum class RulesHubSection {
    MANUAL,
    SUBSCRIPTION,
    PROVIDER_DEFS,
}

sealed class RulesHubListItem(val stableId: String) {
    data class Header(val profileTitle: String, val summary: String) : RulesHubListItem("header")

    data class Section(
        val section: RulesHubSection,
        val title: String,
        val subtitle: String?,
        val collapsible: Boolean,
        val expanded: Boolean,
    ) : RulesHubListItem("section-${section.name}")

    data class ManualRule(
        val rule: RuleItem,
        val displayIndex: Int,
        val targetMissing: Boolean,
    ) : RulesHubListItem("manual-${rule.id}")

    data class ProviderRule(
        val rule: RuleItem,
        val displayIndex: Int,
        val meta: String,
    ) : RulesHubListItem("provider-rule-${rule.id}")

    data class ProviderDef(val provider: RuleProviderItem) : RulesHubListItem("provider-def-${provider.id}")

    data class AddAction(val label: String, val action: AddActionKind) : RulesHubListItem("add-${action.name}")

    object EmptyManual : RulesHubListItem("empty-manual")

    enum class AddActionKind { MANUAL, PROVIDER }
}

object RulesHubListBuilder {
    fun build(
        context: android.content.Context,
        state: RuleState,
        filter: RulesHubFilter,
        searchQuery: String,
        providerMap: Map<String, RuleProviderItem>,
        knownPolicies: Set<String>,
        profileName: String,
        subscriptionExpanded: Boolean,
        providerDefsExpanded: Boolean,
    ): List<RulesHubListItem> {
        val (allManual, allProvider) = RulesHubRowBuilder.partitionRules(state.rules)
        val manual = RulesHubRowBuilder.filterRules(allManual, filter, searchQuery, providerMap)
        val providerRules = RulesHubRowBuilder.filterRules(allProvider, filter, searchQuery, providerMap)
        val providers = state.providers

        val items = mutableListOf<RulesHubListItem>()
        val title = context.getString(R.string.rules_hub_title_profile_fmt, profileName)
        val summary = RulesHubRowBuilder.buildSummaryLine(
            context,
            manual.size,
            allManual.size,
            providerRules.size,
            allProvider.size,
        )
        items += RulesHubListItem.Header(title, summary)

        items += RulesHubListItem.Section(
            section = RulesHubSection.MANUAL,
            title = context.getString(
                R.string.rules_hub_section_title_count_fmt,
                context.getString(R.string.rules_hub_section_manual),
                allManual.size,
            ),
            subtitle = if (manual.size != allManual.size) {
                context.getString(R.string.rules_hub_section_filtered_fmt, manual.size)
            } else {
                null
            },
            collapsible = false,
            expanded = true,
        )
        if (allManual.isEmpty()) {
            items += RulesHubListItem.EmptyManual
        } else {
            manual.forEachIndexed { index, rule ->
                items += RulesHubListItem.ManualRule(
                    rule = rule,
                    displayIndex = index + 1,
                    targetMissing = RulesHubRowBuilder.isTargetMissing(rule, knownPolicies),
                )
            }
        }
        items += RulesHubListItem.AddAction(
            label = context.getString(R.string.rules_hub_add_rule),
            action = RulesHubListItem.AddActionKind.MANUAL,
        )

        items += RulesHubListItem.Section(
            section = RulesHubSection.SUBSCRIPTION,
            title = context.getString(
                R.string.rules_hub_section_title_count_fmt,
                context.getString(R.string.rules_hub_section_subscription),
                allProvider.size,
            ),
            subtitle = if (providerRules.size != allProvider.size) {
                context.getString(R.string.rules_hub_section_filtered_fmt, providerRules.size)
            } else {
                null
            },
            collapsible = true,
            expanded = subscriptionExpanded,
        )
        if (subscriptionExpanded) {
            providerRules.forEachIndexed { index, rule ->
                val providerLabel = if (rule.type.equals("RULE-SET", true)) {
                    providerMap[rule.value]?.name ?: rule.providerName ?: rule.value
                } else ""
                val meta = buildString {
                    append(context.getString(R.string.effective_rules_source_provider))
                    if (providerLabel.isNotBlank()) append(" · ").append(providerLabel)
                }
                items += RulesHubListItem.ProviderRule(rule, index + 1, meta)
            }

            items += RulesHubListItem.Section(
                section = RulesHubSection.PROVIDER_DEFS,
                title = context.getString(
                    R.string.rules_hub_section_title_count_fmt,
                    context.getString(R.string.rules_hub_section_provider_defs),
                    providers.size,
                ),
                subtitle = null,
                collapsible = true,
                expanded = providerDefsExpanded,
            )
            if (providerDefsExpanded) {
                providers.forEach { p ->
                    items += RulesHubListItem.ProviderDef(p)
                }
                items += RulesHubListItem.AddAction(
                    label = context.getString(R.string.rules_hub_add_provider),
                    action = RulesHubListItem.AddActionKind.PROVIDER,
                )
            }
        }
        return items
    }
}
