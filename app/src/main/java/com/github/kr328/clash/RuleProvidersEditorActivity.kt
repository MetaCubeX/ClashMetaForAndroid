package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.design.RuleProvidersEditorDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.showYamlPreview
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class RuleProvidersEditorActivity : BaseActivity<RuleProvidersEditorDesign>() {
    override suspend fun main() {
        val uuid = intent.uuid
        if (uuid != null) {
            startActivity(
                RulesHubActivity::class.intent
                    .setUUID(uuid)
                    .putExtra(RulesHubActivity.EXTRA_EXPAND_PROVIDERS, true),
            )
            finish()
            return
        }

        val legacyUuid = intent.uuid ?: return finish()
        val design = RuleProvidersEditorDesign(this)
        setContentDesign(design)

        val initial = withProfile { readRuleProvidersYaml(legacyUuid) }
            .orEmpty()
        if (initial.isEmpty()) {
            design.setYaml(
                "rule-providers:\n  MyRules:\n    type: http\n    behavior: classical\n" +
                    "    url: \"https://example.com/rules.yaml\"\n    path: ./ruleset/MyRules.yaml\n    interval: 86400\n",
            )
        } else {
            design.setYaml(initial)
        }

        while (isActive) {
            select<Unit> {
                design.requests.onReceive { req ->
                    when (req) {
                        RuleProvidersEditorDesign.Request.Save -> launch {
                            val preview = withProfile {
                                previewReplaceRuleProvidersYaml(legacyUuid, design.getYaml())
                            }
                            showYamlPreview(preview) {
                                design.showToast(R.string.rule_snippet_apply_ok, ToastDuration.Long)
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }
}
