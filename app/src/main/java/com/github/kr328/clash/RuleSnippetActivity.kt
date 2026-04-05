package com.github.kr328.clash

import android.content.ClipData
import android.content.ClipboardManager
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.RuleSnippetDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import androidx.core.content.getSystemService
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class RuleSnippetActivity : BaseActivity<RuleSnippetDesign>() {
    override suspend fun main() {
        val design = RuleSnippetDesign(this)
        setContentDesign(design)

        val groups = try {
            withClash { queryProxyGroupNames(uiStore.proxyExcludeNotSelectable) }
        } catch (_: Exception) {
            emptyList()
        }
        design.setPolicyOptions(groups)

        while (isActive) {
            select<Unit> {
                design.requests.onReceive {
                    when (it) {
                        RuleSnippetDesign.Request.CopyYaml -> {
                            val yaml = design.generatedYaml
                            getSystemService<ClipboardManager>()?.setPrimaryClip(
                                ClipData.newPlainText("clash-rules", yaml)
                            )
                            launch {
                                design.showToast(R.string.copied, ToastDuration.Short)
                            }
                        }

                        RuleSnippetDesign.Request.OpenEngineOverrides ->
                            startActivity(OverrideSettingsActivity::class.intent)

                        RuleSnippetDesign.Request.OpenRuleProvidersEditor -> launch {
                            val uuid = withProfile { queryActive()?.uuid }
                            if (uuid == null) {
                                design.showToast(R.string.no_profile_selected, ToastDuration.Long)
                            } else {
                                startActivity(RuleProvidersEditorActivity::class.intent.setUUID(uuid))
                            }
                        }

                        RuleSnippetDesign.Request.ApplyRuleProvider -> launch {
                            val yaml = design.ruleProvidersYamlForMerge()
                            val prepend = design.prependRuleLineForMerge()
                            if (design.applyToAllImportedProfiles()) {
                                val imported = withProfile {
                                    queryAll().filter { p -> p.imported }
                                }
                                if (imported.isEmpty()) {
                                    design.showToast(R.string.no_profile_selected, ToastDuration.Long)
                                    return@launch
                                }
                                var ok = 0
                                var fail = 0
                                for (p in imported) {
                                    val r = withProfile {
                                        mergeRuleProviderYaml(p.uuid, yaml, prepend)
                                    }
                                    if (r) ok++ else fail++
                                }
                                design.showToast(
                                    getString(R.string.rule_apply_partial, ok, fail),
                                    ToastDuration.Long,
                                )
                            } else {
                                val uuid = withProfile { queryActive()?.uuid }
                                if (uuid == null) {
                                    design.showToast(R.string.no_profile_selected, ToastDuration.Long)
                                    return@launch
                                }
                                val ok = withProfile {
                                    mergeRuleProviderYaml(uuid, yaml, prepend)
                                }
                                if (ok) {
                                    design.showToast(R.string.rule_snippet_apply_ok, ToastDuration.Long)
                                } else {
                                    design.showToast(R.string.rule_snippet_apply_failed, ToastDuration.Long)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
