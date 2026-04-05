package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.EffectiveRulesDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.util.EffectiveRulesParser
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class EffectiveRulesActivity : BaseActivity<EffectiveRulesDesign>() {
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
            val yaml = withProfile { readImportedConfigYaml(active.uuid) }.orEmpty()
            val lines = EffectiveRulesParser.parseRuleItems(yaml)
            design.patchRules(lines)
        }

        while (isActive) {
            select<Unit> {
                design.requests.onReceive {
                    when (it) {
                        EffectiveRulesDesign.Request.OpenLogcat ->
                            startActivity(LogcatActivity::class.intent)
                    }
                }
            }
        }
    }
}
