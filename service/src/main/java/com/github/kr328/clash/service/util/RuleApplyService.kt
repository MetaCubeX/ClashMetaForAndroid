package com.github.kr328.clash.service.util

import android.content.Context
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleEditorBundle
import com.github.kr328.clash.service.model.RuleState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
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

    /** Parse a rule-state JSON into the normalized [RuleState] (for mirroring into the user layer). */
    fun parseState(json: String): RuleState = repository.parseStateJson(json)

    /**
     * Dry-run an arbitrary candidate state JSON for the rule editor's preview.
     * Returns the proposed merged config (and normalized state), or null when the
     * engine rejects it. Does not write anything.
     */
    fun dryRunStateJson(uuid: UUID, stateJson: String): RuleDryRun? {
        val config = configFile(uuid) ?: return null
        val state = repository.parseStateJson(stateJson)
        return safeDryRunState(config, state)
    }

    /**
     * Everything the rule editor needs to open — rule state + policy options
     * (proxy/group names) — from a SINGLE snapshot parse (the hub used to parse
     * twice: once for the state, once for the policy picker).
     */
    fun readEditorBundle(uuid: UUID): String? {
        val config = configFile(uuid) ?: return null
        val dir = config.parentFile ?: return null
        val snapshot = runCatching { Clash.parseProfileSnapshot(dir) }.getOrNull() ?: return null
        val state = repository.load(uuid, snapshot)
        val policies = buildList {
            snapshot.proxies.forEach { obj ->
                (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            }
            addAll(ProxyGroupsYamlPreview.listProxyGroupNames(snapshot))
        }.distinct()
        return stateJsonForReconcile.encodeToString(RuleEditorBundle.serializer(), RuleEditorBundle(state, policies))
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
        // In mihomo a rule policy can be a single proxy (node), not only a group — accept both.
        val proxyNames = mergedSnapshot.proxies.mapNotNull {
            it["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { n -> n.isNotEmpty() }
        }.toSet()
        RuleValidator.validate(
            normalized,
            availablePolicies = proxyGroups + proxyNames,
            // Proxies pulled from proxy-providers aren't statically listed; don't reject a policy we
            // can't see (the engine gate is the real check).
            allowUnknownPolicy = mergedSnapshot.proxyProviders.isNotEmpty(),
        )
        // Soft engine check: ask mihomo whether it would accept the merged
        // YAML and surface its verdict in the log. We do NOT block the write
        // on failure - ParseRawConfig also loads provider files, which means
        // a broken provider .mrs/.yaml on disk would block legitimate rule
        // edits (toggle/delete) that have nothing to do with the provider.
        // Use the log to spot issues; the runtime apply will hard-fail later
        // if mihomo really can't load, and at that point the user fixes the
        // provider, not their edits.
        Clash.validateProfileBytes(mergedYaml)?.let { engineError ->
            Log.w("Engine flagged merged config (continuing anyway): $engineError")
        }
        return RuleDryRun(currentYaml, mergedYaml, normalized)
    }

    private fun configFile(uuid: UUID): File? {
        val file = File(context.importedDir, "$uuid/config.yaml")
        return file.takeIf { it.isFile }
    }

}

/**
 * Stable rule ordering: rules keep their `order` (the user's manual drag order +
 * the subscription's authored order); we only compact the indices. We do NOT move
 * disabled rules to the tail and do NOT re-sort by rule type. So toggling a rule
 * off then on returns it to exactly its place, and the subscription author's rule
 * order is preserved. `mergeStateIntoConfig` emits enabled rules by `order`,
 * skipping disabled ones — so a disabled rule simply holds its slot.
 *
 * Pure (no Context/engine) → unit-testable; `applyState`/`dryRunState`/reconcile
 * delegate here.
 */
internal fun normalizeRuleOrder(rules: List<RuleItem>): List<RuleItem> =
    rules.sortedBy { it.order }.mapIndexed { idx, r -> r.copy(order = idx) }
