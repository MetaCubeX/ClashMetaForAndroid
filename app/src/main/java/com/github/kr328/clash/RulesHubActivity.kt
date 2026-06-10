package com.github.kr328.clash

import android.os.Bundle
import androidx.core.view.WindowCompat
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProfileSnapshot
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.RuleEditSheet
import com.github.kr328.clash.design.RuleProviderEditSheet
import com.github.kr328.clash.design.RulesHubDesign
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleState
import com.github.kr328.clash.service.util.ProxyGroupsYamlPreview
import com.github.kr328.clash.service.util.RuleValidator
import com.github.kr328.clash.util.showRuleStatePreview
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

class RulesHubActivity : BaseActivity<RulesHubDesign>() {
    companion object {
        const val EXTRA_EXPAND_PROVIDERS = "expand_providers"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var uuid: UUID? = null
    private var profileName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override suspend fun main() {
        val design = RulesHubDesign(this)
        setContentDesign(design)

        val expandProviders = intent.getBooleanExtra(EXTRA_EXPAND_PROVIDERS, false)
        val profileUuid = intent.uuid ?: withProfile { queryActive()?.uuid }
        if (profileUuid == null) {
            withContext(Dispatchers.Main) { design.showNoProfile() }
        } else {
            uuid = profileUuid
            val profile = withProfile { queryByUUID(profileUuid) }
            profileName = profile?.name.orEmpty()
            loadInto(design, expandProviders)
        }

        while (isActive) {
            select<Unit> {
                design.requests.onReceive { req ->
                    val id = uuid ?: return@onReceive
                    when (req) {
                        RulesHubDesign.Request.Save -> launch { onSave(design, id) }
                        RulesHubDesign.Request.AddManual -> withContext(Dispatchers.Main) {
                            showRuleEditSheet(design, rule = null)
                        }
                        RulesHubDesign.Request.AddProvider -> withContext(Dispatchers.Main) {
                            showProviderEditSheet(design, provider = null)
                        }
                        is RulesHubDesign.Request.EditManual -> withContext(Dispatchers.Main) {
                            val rule = design.findRule(req.ruleId) ?: return@withContext
                            showRuleEditSheet(design, rule = rule)
                        }
                        is RulesHubDesign.Request.EditProvider -> withContext(Dispatchers.Main) {
                            val provider = design.findProvider(req.providerId) ?: return@withContext
                            showProviderEditSheet(design, provider = provider)
                        }
                        is RulesHubDesign.Request.ToggleRule -> withContext(Dispatchers.Main) {
                            design.mutateRule(req.ruleId) { it.copy(enabled = req.enabled) }
                        }
                        is RulesHubDesign.Request.ToggleProvider -> withContext(Dispatchers.Main) {
                            design.mutateProvider(req.providerId) { it.copy(enabled = req.enabled) }
                        }
                        is RulesHubDesign.Request.RestoreRule -> withContext(Dispatchers.Main) {
                            design.mutateRule(req.ruleId) { it.copy(deleted = false, enabled = true) }
                        }
                        is RulesHubDesign.Request.ReorderManual -> withContext(Dispatchers.Main) {
                            design.reorderManualById(req.fromId, req.toId)
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadInto(design: RulesHubDesign, expandProviders: Boolean) {
        val id = uuid ?: return
        val stateJson = withProfile { readRuleState(id) }
        val state = stateJson
            ?.let { runCatching { json.decodeFromString(RuleState.serializer(), it) }.getOrNull() }
            ?: RuleState()
        val policies = loadProxyOptions(id)
        withContext(Dispatchers.Main) {
            design.bind(profileName, state, policies, expandProviders)
        }
    }

    private suspend fun loadProxyOptions(id: UUID): List<String> {
        val yaml = withProfile { readImportedConfigYaml(id) }.orEmpty()
        if (yaml.isBlank()) return emptyList()
        val snapshot = runCatching { Clash.parseProfileSnapshotFromYaml(yaml) }.getOrNull()
            ?: return emptyList()
        return proxyOptionsFromSnapshot(snapshot)
    }

    private fun proxyOptionsFromSnapshot(snapshot: ProfileSnapshot): List<String> {
        val proxies = snapshot.proxies.mapNotNull { obj ->
            (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }
        val groups = ProxyGroupsYamlPreview.listProxyGroupNames(snapshot)
        return (proxies + groups).distinct().sorted()
    }

    private fun showRuleEditSheet(design: RulesHubDesign, rule: RuleItem?) {
        val sheet = RuleEditSheet(
            context = this,
            policyOptions = design.policyOptions(),
            knownPolicies = design.knownPolicies(),
            onConfirm = { result ->
                if (rule == null) {
                    design.addManualRule(
                        RuleEditSheet.newManualRule(
                            result = result,
                            order = 0,
                            id = UUID.randomUUID().toString(),
                        ),
                    )
                } else {
                    design.updateManualRule(rule.id, result)
                }
            },
            onDelete = rule?.let { existing -> { design.deleteManualRule(existing.id) } },
        )
        if (rule == null) sheet.showAdd() else sheet.showEdit(rule)
    }

    private fun showProviderEditSheet(
        design: RulesHubDesign,
        provider: com.github.kr328.clash.service.model.RuleProviderItem?,
    ) {
        val sheet = RuleProviderEditSheet(
            context = this,
            onConfirm = { result ->
                design.upsertProvider(result, provider?.id)
            },
            onDelete = provider?.let { existing -> { design.removeProvider(existing.id) } },
        )
        if (provider == null) sheet.showAdd() else sheet.showEdit(provider)
    }

    private suspend fun onSave(design: RulesHubDesign, id: UUID) {
        val state = withContext(Dispatchers.Main) { design.readState() }
        val proxyGroups = design.policyOptions().toSet()
        val validationError = runCatching {
            RuleValidator.validate(state, proxyGroups)
        }.exceptionOrNull()?.message
        if (validationError != null) {
            withContext(Dispatchers.Main) {
                design.showStatus(validationError, true)
            }
            return
        }

        withContext(Dispatchers.Main) { design.setSaveBusy(true) }
        try {
            val stateJson = json.encodeToString(RuleState.serializer(), state)
            val currentYaml = withProfile { readImportedConfigYaml(id) }.orEmpty()
            val proposedYaml = withProfile { previewRuleStateYaml(id, stateJson) }
            withContext(Dispatchers.Main) {
                showRuleStatePreview(id, stateJson, currentYaml, proposedYaml) {
                    withContext(Dispatchers.Main) {
                        design.showStatus(getString(R.string.rules_hub_saved), false)
                    }
                    loadInto(design, expandProviders = false)
                }
            }
        } finally {
            withContext(Dispatchers.Main) { design.setSaveBusy(false) }
        }
    }
}
