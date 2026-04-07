package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
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
                        is EffectiveRulesDesign.Request.ToggleRule -> launch {
                            val p = withProfile { queryActive() } ?: return@launch
                            val ok = withProfile { mutateRule(p.uuid, it.ruleId, "toggle", it.enabled) }
                            if (!ok) {
                                design.showToast(R.string.rule_snippet_apply_failed, ToastDuration.Long)
                                return@launch
                            }
                            reloadRules(design)
                        }
                        is EffectiveRulesDesign.Request.DeleteRule -> launch {
                            val p = withProfile { queryActive() } ?: return@launch
                            val ok = withProfile { mutateRule(p.uuid, it.ruleId, "delete", false) }
                            if (!ok) {
                                design.showToast(R.string.rule_snippet_apply_failed, ToastDuration.Long)
                                return@launch
                            }
                            reloadRules(design)
                        }
                        is EffectiveRulesDesign.Request.RestoreRule -> launch {
                            val p = withProfile { queryActive() } ?: return@launch
                            val ok = withProfile { mutateRule(p.uuid, it.ruleId, "restore", true) }
                            if (!ok) {
                                design.showToast(R.string.rule_snippet_apply_failed, ToastDuration.Long)
                                return@launch
                            }
                            reloadRules(design)
                        }
                    }
                }
            }
        }
    }

    private suspend fun reloadRules(design: EffectiveRulesDesign) {
        val active = withProfile { queryActive() } ?: return
        val stateJson = withProfile { readRuleState(active.uuid) } ?: return
        val state = runCatching { json.decodeFromString(RuleState.serializer(), stateJson) }.getOrNull() ?: return
        val providers = state.providers.associateBy(RuleProviderItem::name)
        design.patchRules(state.rules.sortedBy { it.order }, providers)
    }
}
