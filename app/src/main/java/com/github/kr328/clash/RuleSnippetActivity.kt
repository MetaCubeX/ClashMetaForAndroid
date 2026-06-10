package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.RuleSnippetDesign
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.RuleProviderItem
import com.github.kr328.clash.service.model.RuleState
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class RuleSnippetActivity : BaseActivity<RuleSnippetDesign>() {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun main() {
        if (redirectToRulesHub()) return

        val design = RuleSnippetDesign(this)
        setContentDesign(design)

        refreshExistingProviders(design)

        while (isActive) {
            select<Unit> {
                design.requests.onReceive {
                    when (it) {
                        RuleSnippetDesign.Request.UpdateAllRuleSets -> launch { updateRuleProviders(design, null) }
                        is RuleSnippetDesign.Request.UpdateProvider -> launch { updateRuleProviders(design, it.name) }
                    }
                }
            }
        }
    }

    private suspend fun refreshExistingProviders(design: RuleSnippetDesign) {
        val uuid = withProfile { queryActive()?.uuid } ?: return
        val state = withProfile { readRuleState(uuid) }
            ?.let { runCatching { json.decodeFromString(RuleState.serializer(), it) }.getOrNull() }
            ?: return
        val counts = withContext(Dispatchers.IO) {
            state.providers.mapNotNull { provider ->
                countProviderEntries(uuid, provider)?.let { provider.name to it }
            }.toMap()
        }
        design.patchExistingProviders(state.providers, counts)
    }

    /**
     * Counts `- entry` lines under the `payload:` block of a downloaded provider file.
     * Returns null when the file does not exist yet (provider was never fetched) or is not
     * in a readable YAML rule-set format.
     */
    private fun countProviderEntries(profileUuid: UUID, provider: RuleProviderItem): Int? {
        val rel = provider.path.removePrefix("./").ifBlank { "ruleset/${provider.name}.yaml" }
        val file = File(this.importedDir, "$profileUuid/$rel")
        if (!file.isFile || file.length() == 0L) return null
        return runCatching {
            var inPayload = false
            var count = 0
            file.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val leading = raw.takeWhile { it == ' ' || it == '\t' }.length
                    val trimmed = raw.substring(leading)
                    if (!inPayload) {
                        if (trimmed.startsWith("payload:")) inPayload = true
                    } else {
                        if (trimmed.startsWith("- ")) count++
                        else if (trimmed.isNotEmpty() && leading == 0) break
                    }
                }
            }
            count.takeIf { it > 0 }
        }.getOrNull()
    }

    private suspend fun redirectToRulesHub(): Boolean {
        val id = intent.uuid ?: withProfile { queryActive()?.takeIf { it.imported }?.uuid }
            ?: return false
        startActivity(
            RulesHubActivity::class.intent
                .setUUID(id)
                .putExtra(RulesHubActivity.EXTRA_EXPAND_PROVIDERS, true),
        )
        finish()
        return true
    }

    private suspend fun updateRuleProviders(design: RuleSnippetDesign, name: String?) {
        if (!clashRunning) {
            design.showToast(DesignR.string.rule_rule_sets_need_vpn, ToastDuration.Long)
            return
        }
        try {
            withClash {
                val ruleProviders = queryProviders()
                    .filter { it.type == Provider.Type.Rule }
                    .filter { name == null || it.name == name }
                if (ruleProviders.isEmpty()) {
                    design.showToast(DesignR.string.rule_rule_sets_none_loaded, ToastDuration.Short)
                    return@withClash
                }
                for (p in ruleProviders) {
                    updateProvider(p.type, p.name)
                }
            }
            design.showToast(DesignR.string.rule_rule_sets_update_all_ok, ToastDuration.Long)
        } catch (e: Exception) {
            design.showExceptionToast(e)
        }
    }
}
