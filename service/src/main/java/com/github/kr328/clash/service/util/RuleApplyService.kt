package com.github.kr328.clash.service.util

import android.content.Context
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleState
import com.github.kr328.clash.service.model.RuleSource
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.serialization.json.Json
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
    private val stateJsonForReconcile = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun readStateJson(uuid: UUID): String? {
        val config = configFile(uuid) ?: return null
        Log.d("Read rule state")
        return repository.readStateJson(uuid, config.parentFile!!)
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
        return safeDryRunState(config, state)
    }

    fun mergeProviderShortcut(uuid: UUID, providersYaml: String, prependRuleLine: String): Boolean {
        val config = configFile(uuid) ?: return false
        val current = repository.load(uuid, config.parentFile!!)
        val merged = mergeProviderShortcutState(current, providersYaml, prependRuleLine)
        return applyState(uuid, config, merged)
    }

    fun dryRunMergeProviderShortcut(uuid: UUID, providersYaml: String, prependRuleLine: String): RuleDryRun? {
        val config = configFile(uuid) ?: return null
        val current = repository.load(uuid, config.parentFile!!)
        return safeDryRunState(config, mergeProviderShortcutState(current, providersYaml, prependRuleLine))
    }

    private fun mergeProviderShortcutState(
        current: RuleState,
        providersYaml: String,
        prependRuleLine: String,
    ): RuleState {
        val incomingProviders = RuleMapper.parseProvidersYaml(providersYaml)
        val incomingRule = RuleMapper.parseRuleLine(prependRuleLine, current.rules.size)
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
        val current = repository.load(uuid, config.parentFile!!)
        return applyState(uuid, config, addRulesState(current, rawRules, addMode, insertMode))
    }

    fun dryRunAddRules(uuid: UUID, rawRules: List<String>, addMode: Boolean, insertMode: String): RuleDryRun? {
        val config = configFile(uuid) ?: return null
        val current = repository.load(uuid, config.parentFile!!)
        return safeDryRunState(config, addRulesState(current, rawRules, addMode, insertMode))
    }

    private fun addRulesState(
        current: RuleState,
        rawRules: List<String>,
        addMode: Boolean,
        insertMode: String,
    ): RuleState {
        val incoming = rawRules.mapIndexedNotNull { idx, line ->
            RuleMapper.parseRuleLine(line, idx)
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
        val state = repository.load(uuid, config.parentFile!!)
        return applyState(uuid, config, mutateRuleState(state, ruleId, action, enabled))
    }

    fun dryRunMutateRule(uuid: UUID, ruleId: String, action: String, enabled: Boolean? = null): RuleDryRun? {
        val config = configFile(uuid) ?: return null
        val state = repository.load(uuid, config.parentFile!!)
        return safeDryRunState(config, mutateRuleState(state, ruleId, action, enabled))
    }

    /**
     * dryRunState now throws when the engine rejects the merged YAML (Path B
     * Step 3 — see Clash.validateProfileBytes). Dry-run callers must surface
     * this as "preview unavailable" rather than crashing the UI, so they go
     * through this safe wrapper.
     */
    private fun safeDryRunState(config: File, state: RuleState): RuleDryRun? {
        return runCatching { dryRunState(config, state) }
            .onFailure { Log.w("Rule dry-run rejected by engine", it) }
            .getOrNull()
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
        val mergedSnapshot = Clash.parseProfileSnapshotFromYaml(mergedYaml)
        val proxyGroups = ProxyGroupsYamlPreview.listProxyGroupNames(mergedSnapshot).toSet()
        RuleValidator.validate(normalized, proxyGroups)
        validateMergedYaml(mergedYaml)
        // Engine veto: ask mihomo whether it would actually load this YAML.
        // Catches anything our structural checks miss (rule grammar shifts,
        // provider definition shape, regex compile errors, group ref cycles).
        Clash.validateProfileBytes(mergedYaml)?.let { engineError ->
            throw IllegalStateException("mihomo rejected merged config: $engineError")
        }
        return RuleDryRun(currentYaml, mergedYaml, normalized)
    }

    /**
     * Reconcile after a subscription refresh. Reads MANUAL rules from the saved state file
     * (anything the user added through the UI), reads PROVIDER rules from the freshly fetched
     * config, and re-applies them through the same merge code path used by applyState. This
     * is the structured complement to [SubscriptionUpdateMerge]'s string-based merge: if a
     * state file exists, MANUAL vs PROVIDER classification beats heuristic line matching.
     *
     * Files are passed in explicitly because the caller runs against `processingDir`, not the
     * usual `importedDir/<uuid>`.
     *
     * @return true if reconciliation rewrote the config; false on any failure (caller should
     *         leave whatever [SubscriptionUpdateMerge] produced alone in that case).
     */
    fun reconcileWithStoredState(configFile: File, stateFile: File): Boolean {
        if (!configFile.isFile) return false
        if (!stateFile.isFile) return false
        return runCatching {
            val configText = configFile.readText()
            val snapshot = Clash.parseProfileSnapshot(configFile.parentFile!!)
            val parsed = RuleMapper.parseStateFromSnapshot(snapshot)
            val stored = stateJsonForReconcile.decodeFromString(
                RuleState.serializer(),
                stateFile.readText(),
            )
            val merged = syncStoredWithParsed(stored, parsed)
            val normalized = merged.copy(rules = normalizeRuleOrder(merged.rules))
            val geoDataUrls = GeoDataSources.resolve(
                preset = serviceStore.geoDataSourcePreset,
                customGeoIp = serviceStore.geoDataCustomGeoIp,
                customGeoSite = serviceStore.geoDataCustomGeoSite,
                customMmdb = serviceStore.geoDataCustomMmdb,
                customAsn = serviceStore.geoDataCustomAsn,
            )
            val mergedYaml = RuleMapper.mergeStateIntoConfig(configText, normalized, geoDataUrls)
            validateMergedYaml(mergedYaml)
            configFile.writeText(mergedYaml)
            stateFile.writeText(stateJsonForReconcile.encodeToString(RuleState.serializer(), normalized))
            Log.d("Reconcile after subscription update applied ${normalized.rules.count { !it.deleted && it.enabled }} active rule(s)")
            true
        }.getOrElse {
            Log.w("Reconcile after subscription update failed; keeping mergeAfterFetch output", it)
            false
        }
    }

    // Mirrors RuleRepository.syncProviderRules so reconcile uses the same classification
    // logic as RuleRepository.load. Duplicated rather than reused to avoid making the
    // repository helper public and tying it to file paths that don't apply here.
    private fun syncStoredWithParsed(stored: RuleState, incoming: RuleState): RuleState {
        fun key(r: RuleItem) =
            "${r.type.uppercase()},${r.value.uppercase()},${r.policy.uppercase()}"

        val byKey = stored.rules.associateBy(::key)
        val mergedRules = incoming.rules.mapIndexed { index, rule ->
            val old = byKey[key(rule)]
            if (old != null) {
                rule.copy(
                    id = old.id,
                    enabled = old.enabled,
                    deleted = old.deleted,
                    isRestorable = old.isRestorable || rule.isRestorable,
                    order = index,
                )
            } else {
                rule.copy(order = index)
            }
        }.toMutableList()

        val incomingKeys = incoming.rules.map(::key).toSet()
        val retained = stored.rules.filter { rule ->
            key(rule) !in incomingKeys &&
                (rule.deleted || !rule.enabled || rule.source == RuleSource.MANUAL)
        }
        retained.forEach { mergedRules += it.copy(order = mergedRules.size) }
        return stored.copy(providers = incoming.providers, rules = mergedRules)
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

    private fun ruleLineKey(rule: RuleItem): String {
        return if (rule.type.equals("MATCH", true)) {
            "MATCH,${rule.policy}".uppercase()
        } else {
            "${rule.type},${rule.value},${rule.policy}".uppercase()
        }
    }

    private fun validateMergedYaml(yaml: String) = YamlPreviewSupport.validateConfigYaml(yaml)
}
