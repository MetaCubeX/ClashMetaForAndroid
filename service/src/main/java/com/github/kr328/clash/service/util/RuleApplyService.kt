package com.github.kr328.clash.service.util

import android.content.Context
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleState
import com.github.kr328.clash.service.model.RuleSource
import com.github.kr328.clash.service.store.ServiceStore
import java.io.File
import java.util.UUID

class RuleApplyService(
    private val context: Context,
    private val repository: RuleRepository = RuleRepository(context),
) {
    data class RuleDryRun(
        val currentYaml: String,
        val proposedYaml: String,
        val normalizedState: RuleState,
    )

    private val serviceStore = ServiceStore(context)

    fun readStateJson(uuid: UUID): String? {
        val config = configFile(uuid) ?: return null
        Log.d("Read rule state")
        return repository.readStateJson(uuid, config.readText())
    }

    fun applyStateJson(uuid: UUID, stateJson: String): Boolean {
        val config = configFile(uuid) ?: return false
        val state = repository.parseStateJson(stateJson)
        Log.d("Apply structured rule state rules=${state.rules.size}, providers=${state.providers.size}")
        return applyState(uuid, config, state)
    }

    fun saveStateJson(uuid: UUID, stateJson: String) {
        repository.save(uuid, repository.parseStateJson(stateJson))
    }

    fun dryRunStateJson(uuid: UUID, stateJson: String): RuleDryRun? {
        val config = configFile(uuid) ?: return null
        val state = repository.parseStateJson(stateJson)
        return dryRunState(config, state)
    }

    fun mergeProviderShortcut(uuid: UUID, providersYaml: String, prependRuleLine: String): Boolean {
        val config = configFile(uuid) ?: return false
        val currentText = config.readText()
        val current = repository.load(uuid, currentText)
        val merged = mergeProviderShortcutState(current, providersYaml, prependRuleLine)
        return applyState(uuid, config, merged)
    }

    fun dryRunMergeProviderShortcut(uuid: UUID, providersYaml: String, prependRuleLine: String): RuleDryRun? {
        val config = configFile(uuid) ?: return null
        val currentText = config.readText()
        val current = repository.load(uuid, currentText)
        return dryRunState(config, mergeProviderShortcutState(current, providersYaml, prependRuleLine))
    }

    private fun mergeProviderShortcutState(
        current: RuleState,
        providersYaml: String,
        prependRuleLine: String,
    ): RuleState {
        val incomingProviders = RuleMapper.parseProvidersYaml(providersYaml)
        val incomingRule = parseRuleLine(prependRuleLine, current.rules.size)
        Log.d("Merge provider shortcut incomingProviders=${incomingProviders.size} incomingRule=${incomingRule != null}")

        val mergedProviders = (current.providers + incomingProviders)
            .groupBy { it.name }
            .map { (_, list) -> list.last().copy(enabled = true, source = RuleSource.PROVIDER) }

        val mergedRules = buildList {
            addAll(current.rules.filterNot { old ->
                incomingRule != null &&
                    old.type.equals("RULE-SET", true) &&
                    old.value.equals(incomingRule.value, true)
            })
            if (incomingRule != null) add(0, incomingRule)
        }.mapIndexed { i, item -> item.copy(order = i) }

        return current.copy(providers = mergedProviders, rules = mergedRules)
    }

    fun addRules(uuid: UUID, rawRules: List<String>, addMode: Boolean, insertMode: String): Boolean {
        val config = configFile(uuid) ?: return false
        val current = repository.load(uuid, config.readText())
        return applyState(uuid, config, addRulesState(current, rawRules, addMode, insertMode))
    }

    fun dryRunAddRules(uuid: UUID, rawRules: List<String>, addMode: Boolean, insertMode: String): RuleDryRun? {
        val config = configFile(uuid) ?: return null
        val current = repository.load(uuid, config.readText())
        return dryRunState(config, addRulesState(current, rawRules, addMode, insertMode))
    }

    private fun addRulesState(
        current: RuleState,
        rawRules: List<String>,
        addMode: Boolean,
        insertMode: String,
    ): RuleState {
        val incoming = rawRules.mapIndexedNotNull { idx, line ->
            parseRuleLine(line, idx)
        }
        if (incoming.isEmpty()) return current

        return if (addMode) {
            val existing = current.rules.filterNot { it.deleted }.map { ruleLineKey(it) }.toMutableSet()
            val added = incoming.filter { existing.add(ruleLineKey(it)) }
            val base = current.rules.toMutableList()
            when {
                insertMode.equals("prepend", true) -> base.addAll(0, added)
                insertMode.startsWith("index:", true) -> {
                    val idx = insertMode.substringAfter(":").toIntOrNull() ?: base.size
                    val target = idx.coerceIn(0, base.size)
                    base.addAll(target, added)
                }
                else -> base.addAll(added)
            }
            val rules = base.mapIndexed { i, r -> r.copy(order = i) }
            current.copy(rules = rules)
        } else {
            val rules = incoming.mapIndexed { i, r -> r.copy(order = i) }
            current.copy(rules = rules)
        }
    }

    fun mutateRule(uuid: UUID, ruleId: String, action: String, enabled: Boolean? = null): Boolean {
        val config = configFile(uuid) ?: return false
        val state = repository.load(uuid, config.readText())
        return applyState(uuid, config, mutateRuleState(state, ruleId, action, enabled))
    }

    fun dryRunMutateRule(uuid: UUID, ruleId: String, action: String, enabled: Boolean? = null): RuleDryRun? {
        val config = configFile(uuid) ?: return null
        val state = repository.load(uuid, config.readText())
        return dryRunState(config, mutateRuleState(state, ruleId, action, enabled))
    }

    private fun mutateRuleState(
        state: RuleState,
        ruleId: String,
        action: String,
        enabled: Boolean?,
    ): RuleState {
        val updatedRules = state.rules.mapNotNull { r ->
            if (r.id != ruleId) return@mapNotNull r
            when (action) {
                "toggle" -> r.copy(enabled = enabled ?: r.enabled)
                "delete" -> {
                    if (r.source == RuleSource.PROVIDER) r.copy(deleted = true, enabled = false, isRestorable = true)
                    else null
                }
                "restore" -> r.copy(deleted = false, enabled = true)
                else -> r
            }
        }.mapIndexed { i, r -> r.copy(order = i) }
        return state.copy(rules = updatedRules)
    }

    private fun applyState(uuid: UUID, config: File, state: RuleState): Boolean {
        return runCatching {
            val dryRun = dryRunState(config, state) ?: return false
            val normalized = dryRun.normalizedState
            val mergedYaml = dryRun.proposedYaml
            repository.save(uuid, normalized)
            config.writeText(mergedYaml)
            context.sendProfileChanged(uuid)
            Log.d("Rules applied mergedRules=${normalized.rules.count { it.enabled }} mergedProviders=${normalized.providers.count { it.enabled }}")
            true
        }.onFailure {
            Log.e("Apply rules failed", it)
        }.getOrElse { false }
    }

    private fun dryRunState(config: File, state: RuleState): RuleDryRun {
        val currentYaml = config.readText()
        val normalized = state.copy(rules = normalizeRuleOrder(state.rules))
        val geoDataUrls = GeoDataSources.resolve(
            preset = serviceStore.geoDataSourcePreset,
            customGeoIp = serviceStore.geoDataCustomGeoIp,
            customGeoSite = serviceStore.geoDataCustomGeoSite,
            customMmdb = serviceStore.geoDataCustomMmdb,
            customAsn = serviceStore.geoDataCustomAsn,
        )
        val mergedYaml = RuleMapper.mergeStateIntoConfig(currentYaml, normalized, geoDataUrls)
        val proxyGroups = ProxyGroupsYamlPreview.listProxyGroupNames(mergedYaml).toSet()
        RuleValidator.validate(normalized, proxyGroups)
        validateMergedYaml(mergedYaml)
        return RuleDryRun(currentYaml, mergedYaml, normalized)
    }

    private fun normalizeRuleOrder(rules: List<RuleItem>): List<RuleItem> {
        data class Indexed(val i: Int, val r: RuleItem)
        val enabled = rules.mapIndexed { i, r -> Indexed(i, r) }.filter { it.r.enabled && !it.r.deleted }
        val inactive = rules.mapIndexed { i, r -> Indexed(i, r) }.filterNot { it.r.enabled && !it.r.deleted }
        val sortedEnabled = enabled.sortedWith(compareBy<Indexed> { priority(it.r) }.thenBy { it.i })
        return (sortedEnabled + inactive).mapIndexed { idx, ir -> ir.r.copy(order = idx) }
    }

    private fun priority(rule: RuleItem): Int {
        val reject = rule.policy.equals("REJECT", true) || rule.policy.equals("REJECT-DROP", true)
        if (reject) return 0
        if (rule.type.equals("GEOSITE", true)) return 1
        if (rule.type.equals("RULE-SET", true)) return 2
        return 3
    }

    private fun configFile(uuid: UUID): File? {
        val file = File(context.importedDir, "$uuid/config.yaml")
        return file.takeIf { it.isFile }
    }

    private fun parseRuleLine(line: String, order: Int): RuleItem? {
        val t = line.trim().removePrefix("-").trim()
        if (t.isBlank()) return null
        val parts = t.split(",").map { it.trim() }
        val type = parts.firstOrNull().orEmpty()
        if (type.isBlank()) return null
        return if (type.equals("MATCH", true)) {
            RuleItem(
                id = UUID.randomUUID().toString(),
                raw = t,
                type = "MATCH",
                value = "",
                policy = parts.getOrElse(1) { "DIRECT" },
                enabled = true,
                deleted = false,
                source = RuleSource.MANUAL,
                order = order,
            )
        } else {
            RuleItem(
                id = UUID.randomUUID().toString(),
                raw = t,
                type = type.uppercase(),
                value = parts.getOrElse(1) { "" },
                policy = parts.getOrElse(2) { "DIRECT" },
                enabled = true,
                deleted = false,
                source = if (type.equals("RULE-SET", true)) RuleSource.PROVIDER else RuleSource.MANUAL,
                providerName = if (type.equals("RULE-SET", true)) parts.getOrElse(1) { "" }.ifBlank { null } else null,
                isRestorable = type.equals("RULE-SET", true),
                order = order,
            )
        }
    }

    private fun ruleLineKey(rule: RuleItem): String {
        return if (rule.type.equals("MATCH", true)) {
            "MATCH,${rule.policy}".uppercase()
        } else {
            "${rule.type},${rule.value},${rule.policy}".uppercase()
        }
    }

    private fun validateMergedYaml(yaml: String) = YamlPreviewSupport.validateConfigYaml(yaml)
}
