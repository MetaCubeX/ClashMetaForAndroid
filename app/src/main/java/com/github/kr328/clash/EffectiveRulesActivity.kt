package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.design.EffectiveRulesDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.RuleProviderItem
import com.github.kr328.clash.service.model.RuleState
import com.github.kr328.clash.util.withProfile
import kotlinx.serialization.json.Json
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class EffectiveRulesActivity : BaseActivity<EffectiveRulesDesign>() {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun main() {
        if (redirectToRulesHub()) return

        val design = EffectiveRulesDesign(this)
        setContentDesign(design)

        val active = withProfile { queryActive() }
        if (active == null || !active.imported) {
            design.patchRules(emptyList())
            launch {
                design.showToast(R.string.effective_rules_no_profile, ToastDuration.Long)
            }
        } else {
            val stateJson = withProfile { readRuleState(active.uuid) }
            val state = stateJson?.let { runCatching { json.decodeFromString(RuleState.serializer(), it) }.getOrNull() }
                ?: RuleState()
            val providers = state.providers.associateBy(RuleProviderItem::name)
            design.patchRules(state.rules.sortedBy { it.order }, providers)
        }

        while (isActive) {
            select<Unit> {
                design.requests.onReceive {
                    when (it) {
                        is EffectiveRulesDesign.Request.OpenLogcat ->
                            startActivity(LogcatActivity::class.intent)
                    }
                }
            }
        }
    }

    private suspend fun redirectToRulesHub(): Boolean {
        val id = intent.uuid ?: withProfile { queryActive()?.takeIf { it.imported }?.uuid }
            ?: return false
        startActivity(
            RulesHubActivity::class.intent
                .setUUID(id)
                .putExtra(RulesHubActivity.EXTRA_EXPAND_PROVIDERS, false),
        )
        finish()
        return true
    }
}
