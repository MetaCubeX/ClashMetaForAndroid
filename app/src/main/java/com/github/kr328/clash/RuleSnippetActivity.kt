package com.github.kr328.clash

import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.RuleSnippetDesign
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleProviderItem
import com.github.kr328.clash.service.model.RuleState
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.service.util.ProxyGroupsYamlPreview
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class RuleSnippetActivity : BaseActivity<RuleSnippetDesign>() {
    private val json = Json { ignoreUnknownKeys = true }
    private var proxyGroups: List<String> = emptyList()

    override suspend fun main() {
        val design = RuleSnippetDesign(this)
        setContentDesign(design)

        refreshExistingProviders(design)

        while (isActive) {
            select<Unit> {
                design.requests.onReceive {
                    when (it) {
                        RuleSnippetDesign.Request.OpenCreateSheet -> launch { showCreateSheet(design) }
                        RuleSnippetDesign.Request.OpenManualRules ->
                            startActivity(EffectiveRulesActivity::class.intent)
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
        design.patchExistingProviders(state.providers)
    }

    private suspend fun showCreateSheet(design: RuleSnippetDesign) {
        val uuid = withProfile { queryActive()?.uuid }
        if (uuid == null) {
            design.showToast(DesignR.string.no_profile_selected, ToastDuration.Long)
            return
        }
        proxyGroups = loadAvailableProxyGroups(uuid)
        val state = withProfile { readRuleState(uuid) }
            ?.let { runCatching { json.decodeFromString(RuleState.serializer(), it) }.getOrNull() }
            ?: RuleState()

        val dialog = AppBottomSheetDialog(this, fitContentHeight = true)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_rules_create, null)
        dialog.setContentView(view)

        val title = view.findViewById<TextView>(R.id.tv_sheet_title)
        val typeSpinner = view.findViewById<AutoCompleteTextView>(R.id.spinner_create_type)
        val presetGroup = view.findViewById<LinearLayout>(R.id.group_preset)
        val providerGroup = view.findViewById<LinearLayout>(R.id.group_provider)
        val manualGroup = view.findViewById<LinearLayout>(R.id.group_manual)
        val preview = view.findViewById<TextView>(R.id.tv_preview)
        val applyButton = view.findViewById<View>(R.id.btn_apply_sheet)

        val typeOptions = listOf(
            getString(DesignR.string.rule_create_preset),
            getString(DesignR.string.rule_create_online),
            getString(DesignR.string.rule_create_manual),
        )
        typeSpinner.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, typeOptions))

        val presetSpinner = view.findViewById<AutoCompleteTextView>(R.id.spinner_preset)
        presetSpinner.setAdapter(ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            listOf(
                getString(DesignR.string.rule_quick_openai),
                getString(DesignR.string.rule_quick_telegram),
                getString(DesignR.string.rule_quick_discord),
                getString(DesignR.string.rule_quick_ai),
                getString(DesignR.string.rule_quick_facebook),
                getString(DesignR.string.rule_quick_instagram),
                getString(DesignR.string.rule_quick_twitter),
                getString(DesignR.string.rule_quick_youtube),
                getString(DesignR.string.rule_quick_tiktok),
                getString(DesignR.string.rule_quick_netflix),
                getString(DesignR.string.rule_quick_whatsapp),
                getString(DesignR.string.rule_quick_messengers),
            )
        ))
        val insertModes = listOf(
            getString(DesignR.string.rule_insert_append),
            getString(DesignR.string.rule_insert_prepend),
            getString(DesignR.string.rule_insert_top),
        )
        view.findViewById<AutoCompleteTextView>(R.id.spinner_manual_insert_mode).setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, insertModes))

        val policyOptions = buildList {
            add("DIRECT")
            add("REJECT")
            add("REJECT-DROP")
            add("PASS")
            addAll(proxyGroups.distinct())
        }
        val validPolicy = preferredPolicy(policyOptions)
        val presetPolicyOptions = listOf(getString(DesignR.string.rule_policy_auto)) + policyOptions
        view.findViewById<AutoCompleteTextView>(R.id.spinner_preset_policy).apply {
            setAdapter(ArrayAdapter(this@RuleSnippetActivity, android.R.layout.simple_dropdown_item_1line, presetPolicyOptions))
            setText(adapter.getItem(0).toString(), false)
            listSelection = 0
        }
        view.findViewById<AutoCompleteTextView>(R.id.spinner_provider_policy).apply {
            setAdapter(ArrayAdapter(this@RuleSnippetActivity, android.R.layout.simple_dropdown_item_1line, policyOptions))
            val pos = policyOptions.indexOf(validPolicy).coerceAtLeast(0)
            setText(adapter.getItem(pos).toString(), false)
            listSelection = pos
        }
        view.findViewById<AutoCompleteTextView>(R.id.spinner_manual_policy).apply {
            setAdapter(ArrayAdapter(this@RuleSnippetActivity, android.R.layout.simple_dropdown_item_1line, policyOptions))
            val pos = policyOptions.indexOf(validPolicy).coerceAtLeast(0)
            setText(adapter.getItem(pos).toString(), false)
            listSelection = pos
        }
        view.findViewById<AutoCompleteTextView>(R.id.spinner_provider_behavior).setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, listOf("classical", "domain", "ipcidr")))
        val manualTypeOptions = listOf("GEOSITE", "GEOIP", "Custom")
        view.findViewById<AutoCompleteTextView>(R.id.spinner_manual_type).setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, manualTypeOptions))
        val customKindOptions = resources.getStringArray(DesignR.array.clash_manual_rule_types).toList()
        view.findViewById<AutoCompleteTextView>(R.id.spinner_manual_custom_kind).setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customKindOptions))

        title.text = getString(DesignR.string.rule_add_new)

        fun insertModeFromSpinner(s: AutoCompleteTextView): String {
            val pos = (s.adapter as ArrayAdapter<String>).getPosition(s.text.toString())
            return when (pos) {
                1 -> "prepend"
                2 -> "index:0"
                else -> "append"
            }
        }

        fun selected(spinner: AutoCompleteTextView) = spinner.text.toString().trim().orEmpty()

        fun buildPreview() {
            val typePos = (typeSpinner.adapter as ArrayAdapter<String>).getPosition(typeSpinner.text.toString())
            when (typePos) {
                0 -> {
                    val chosen = selected(view.findViewById(R.id.spinner_preset_policy))
                    val policy = if (chosen == getString(DesignR.string.rule_policy_auto)) {
                        preferredPolicy(policyOptions)
                    } else {
                        chosen.ifBlank { preferredPolicy(policyOptions) }
                    }
                    val presetPos = (presetSpinner.adapter as ArrayAdapter<String>).getPosition(presetSpinner.text.toString())
                    val lines = presetLines(presetPos, policy)
                    preview.text = lines.joinToString("\n") { "- $it" }
                }
                1 -> {
                    val name = view.findViewById<TextInputEditText>(R.id.input_provider_name).text?.toString()?.trim().orEmpty().ifBlank { "MyRules" }
                    val url = view.findViewById<TextInputEditText>(R.id.input_provider_url).text?.toString()?.trim().orEmpty().ifBlank { "https://example.com/rules.yaml" }
                    val behavior = selected(view.findViewById(R.id.spinner_provider_behavior)).ifBlank { "classical" }
                    val policy = selected(view.findViewById(R.id.spinner_provider_policy)).ifBlank { "DIRECT" }
                    preview.text = buildString {
                        appendLine("rule-providers:")
                        appendLine("  $name:")
                        appendLine("    type: http")
                        appendLine("    behavior: $behavior")
                        appendLine("    url: \"$url\"")
                        appendLine("    path: ./ruleset/$name.yaml")
                        appendLine("    interval: 86400")
                        appendLine("rules:")
                        appendLine("  - RULE-SET,$name,$policy")
                    }
                }
                else -> {
                    val type = selected(view.findViewById(R.id.spinner_manual_type))
                    val value = view.findViewById<TextInputEditText>(R.id.input_manual_value).text?.toString()?.trim().orEmpty()
                    val policy = selected(view.findViewById(R.id.spinner_manual_policy)).ifBlank { "DIRECT" }
                    val line = when (type) {
                        "GEOSITE", "GEOIP" -> if (value.isBlank()) "" else "$type,$value,$policy"
                        "Custom" -> {
                            val kind = selected(view.findViewById(R.id.spinner_manual_custom_kind)).ifBlank { "DOMAIN" }
                            when {
                                kind.equals("MATCH", true) -> "MATCH,$policy"
                                value.isBlank() -> ""
                                else -> "$kind,$value,$policy"
                            }
                        }
                        else -> value
                    }
                    preview.text = if (line.isBlank()) "—" else "- $line"
                }
            }
        }

        fun syncManualHintsAndCustomRow() {
            val type = selected(view.findViewById(R.id.spinner_manual_type))
            val til = view.findViewById<TextInputLayout>(R.id.til_manual_content)
            val customRow = type == "Custom"
            view.findViewById<TextView>(R.id.tv_manual_custom_kind).visibility =
                if (customRow) View.VISIBLE else View.GONE
            view.findViewById<AutoCompleteTextView>(R.id.spinner_manual_custom_kind).visibility =
                if (customRow) View.VISIBLE else View.GONE
            when (type) {
                "Custom" -> {
                    val kind = selected(view.findViewById(R.id.spinner_manual_custom_kind))
                    til.hint = if (kind.equals("MATCH", true)) {
                        getString(DesignR.string.rule_manual_match_hint)
                    } else {
                        getString(DesignR.string.rule_manual_content_payload_hint)
                    }
                }
                "GEOSITE", "GEOIP" -> til.hint = getString(DesignR.string.rule_manual_content)
                else -> til.hint = getString(DesignR.string.rule_manual_content)
            }
        }

        fun syncGroups() {
            val typePos = (typeSpinner.adapter as ArrayAdapter<String>).getPosition(typeSpinner.text.toString())
            presetGroup.visibility = if (typePos == 0) View.VISIBLE else View.GONE
            providerGroup.visibility = if (typePos == 1) View.VISIBLE else View.GONE
            manualGroup.visibility = if (typePos == 2) View.VISIBLE else View.GONE
            syncManualHintsAndCustomRow()
            buildPreview()
        }
        typeSpinner.setOnItemClickListener { _, _, _, _ -> syncGroups() }
        presetSpinner.setOnItemClickListener { _, _, _, _ -> buildPreview() }
        view.findViewById<AutoCompleteTextView>(R.id.spinner_preset_policy).setOnItemClickListener { _, _, _, _ -> buildPreview() }
        view.findViewById<AutoCompleteTextView>(R.id.spinner_provider_behavior).setOnItemClickListener { _, _, _, _ -> buildPreview() }
        view.findViewById<AutoCompleteTextView>(R.id.spinner_provider_policy).setOnItemClickListener { _, _, _, _ -> buildPreview() }
        view.findViewById<AutoCompleteTextView>(R.id.spinner_manual_policy).setOnItemClickListener { _, _, _, _ -> buildPreview() }
        view.findViewById<AutoCompleteTextView>(R.id.spinner_manual_type).setOnItemClickListener { _, _, _, _ ->
            syncGroups()
        }
        view.findViewById<AutoCompleteTextView>(R.id.spinner_manual_custom_kind).setOnItemClickListener { _, _, _, _ ->
            syncManualHintsAndCustomRow()
            buildPreview()
        }

        applyButton.setOnClickListener {
            launch {
                val typePos = (typeSpinner.adapter as ArrayAdapter<String>).getPosition(typeSpinner.text.toString())
                val ok = when (typePos) {
                    0 -> {
                        val chosen = selected(view.findViewById(R.id.spinner_preset_policy))
                        val policy = if (chosen == getString(DesignR.string.rule_policy_auto)) {
                            preferredPolicy(policyOptions)
                        } else {
                            chosen.ifBlank { preferredPolicy(policyOptions) }
                        }
                        val presetPos = (presetSpinner.adapter as ArrayAdapter<String>).getPosition(presetSpinner.text.toString())
                        runPresetSoftChecks(design, presetPos)
                        val rules = presetLines(presetPos, policy)
                        withProfile {
                            addRules(uuid, rules, addMode = true, insertMode = "append")
                        }
                    }
                    1 -> {
                        applyProviderFromSheet(uuid, state, null, view)
                    }
                    else -> {
                        val line = manualLineFromSheet(view) ?: ""
                        if (line.isBlank()) false else withProfile {
                            addRules(
                                uuid = uuid,
                                rawRules = listOf(line),
                                addMode = true,
                                insertMode = insertModeFromSpinner(view.findViewById(R.id.spinner_manual_insert_mode))
                            )
                        }
                    }
                }
                if (ok) {
                    design.showToast(DesignR.string.rule_snippet_apply_ok, ToastDuration.Long)
                    refreshExistingProviders(design)
                    dialog.dismiss()
                } else {
                    design.showToast(DesignR.string.rule_snippet_apply_failed, ToastDuration.Long)
                }
            }
        }
        syncGroups()
        dialog.show()
    }

    private suspend fun applyProviderFromSheet(
        uuid: UUID,
        state: RuleState,
        editingProvider: RuleProviderItem?,
        view: View
    ): Boolean {
        val name = view.findViewById<TextInputEditText>(R.id.input_provider_name).text?.toString()?.trim().orEmpty()
        val url = view.findViewById<TextInputEditText>(R.id.input_provider_url).text?.toString()?.trim().orEmpty()
        if (name.isBlank() || url.isBlank()) return false
        val behavior = selected(view.findViewById<AutoCompleteTextView>(R.id.spinner_provider_behavior)).ifBlank { "classical" }
        val policy = selected(view.findViewById<AutoCompleteTextView>(R.id.spinner_provider_policy)).ifBlank { "DIRECT" }
        val enabled = view.findViewById<SwitchMaterial>(R.id.switch_provider_enabled).isChecked
        val provider = RuleProviderItem(
            id = editingProvider?.id ?: UUID.randomUUID().toString(),
            name = name,
            behavior = behavior,
            url = url,
            enabled = enabled,
            interval = editingProvider?.interval ?: 86400,
            path = "./ruleset/$name.yaml",
            source = editingProvider?.source ?: com.github.kr328.clash.service.model.RuleSource.MANUAL,
        )
        val oldName = editingProvider?.name
        val updatedProviders = state.providers.filterNot { it.id == provider.id } + provider
        val updatedRules = state.rules.map { rule ->
            if (rule.type.equals("RULE-SET", true) && (rule.providerName.equals(oldName, true) || rule.value.equals(oldName, true))) {
                rule.copy(value = name, providerName = name, policy = policy)
            } else {
                rule
            }
        }.toMutableList()
        if (updatedRules.none { it.type.equals("RULE-SET", true) && it.value.equals(name, true) }) {
            updatedRules.add(
                RuleItem(
                    id = UUID.randomUUID().toString(),
                    raw = "RULE-SET,$name,$policy",
                    type = "RULE-SET",
                    value = name,
                    policy = policy,
                    enabled = enabled,
                    source = com.github.kr328.clash.service.model.RuleSource.PROVIDER,
                    providerName = name,
                    isRestorable = true,
                    order = updatedRules.size,
                )
            )
        }
        val next = state.copy(
            providers = updatedProviders,
            rules = updatedRules.mapIndexed { index, item -> item.copy(order = index) },
        )
        return withProfile { applyRuleState(uuid, json.encodeToString(RuleState.serializer(), next)) }
    }

    private fun manualLineFromSheet(view: View): String? {
        val type = selected(view.findViewById<AutoCompleteTextView>(R.id.spinner_manual_type))
        val value = view.findViewById<TextInputEditText>(R.id.input_manual_value).text?.toString()?.trim().orEmpty()
        val policy = selected(view.findViewById<AutoCompleteTextView>(R.id.spinner_manual_policy)).ifBlank { "DIRECT" }
        return when (type) {
            "GEOSITE", "GEOIP" -> if (value.isBlank()) null else "$type,$value,$policy"
            "Custom" -> {
                val kind = view.findViewById<Spinner>(R.id.spinner_manual_custom_kind).selectedItem?.toString()?.trim().orEmpty()
                if (kind.isBlank()) return null
                when {
                    kind.equals("MATCH", true) -> "MATCH,$policy"
                    value.isBlank() -> null
                    else -> "$kind,$value,$policy"
                }
            }
            else -> value.ifBlank { null }
        }
    }

    private fun presetLines(index: Int, policy: String): List<String> = when (index) {
        1 -> listOf("GEOSITE,telegram,$policy")
        2 -> listOf("GEOSITE,discord,$policy")
        3 -> listOf(
            "GEOSITE,openai,$policy",
            "GEOSITE,anthropic,$policy",
            "GEOSITE,huggingface,$policy",
        )
        4 -> listOf("GEOSITE,facebook,$policy")
        5 -> listOf("GEOSITE,instagram,$policy")
        6 -> listOf("GEOSITE,twitter,$policy")
        7 -> listOf("GEOSITE,youtube,$policy")
        8 -> listOf("GEOSITE,tiktok,$policy")
        9 -> listOf("GEOSITE,netflix,$policy")
        10 -> listOf("GEOSITE,whatsapp,$policy")
        11 -> listOf(
            "GEOSITE,telegram,$policy",
            "GEOSITE,whatsapp,$policy",
            "GEOSITE,discord,$policy",
        )
        else -> listOf("GEOSITE,openai,$policy")
    }

    private fun preferredPolicy(all: List<String>): String {
        val builtIn = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS")
        return all.firstOrNull { it.uppercase() !in builtIn } ?: "DIRECT"
    }

    private suspend fun loadAvailableProxyGroups(uuid: UUID): List<String> {
        val fromRuntime = runCatching {
            withClash { queryProxyGroupNames(uiStore.proxyExcludeNotSelectable) }
        }.getOrElse { emptyList() }
        val fromPreview = runCatching {
            withProfile { readProxyGroupsPreview(uuid).keys.toList() }
        }.getOrElse { emptyList() }
        val fromYaml = runCatching {
            withProfile { readImportedConfigYaml(uuid) }
                ?.let { ProxyGroupsYamlPreview.listProxyGroupNames(it) }
        }.getOrNull() ?: emptyList()
        return (fromRuntime + fromPreview + fromYaml).distinct().sorted()
    }

    private suspend fun runPresetSoftChecks(design: RuleSnippetDesign, presetIndex: Int) {
        val needsGeosite = presetLines(presetIndex, "DIRECT").any { it.startsWith("GEOSITE,", ignoreCase = true) }
        if (!needsGeosite) return

        val geositeFile = File(clashDir, "geosite.dat")
        if (!geositeFile.isFile || geositeFile.length() <= 0L) {
            design.showToast(DesignR.string.rule_preset_geosite_missing_data, ToastDuration.Long)
            return
        }

        if (presetIndex >= 5) {
            design.showToast(DesignR.string.rule_preset_geosite_soft_check_note, ToastDuration.Short)
        }
    }

    private fun selected(spinner: AutoCompleteTextView): String =
        spinner.text?.toString()?.trim().orEmpty()

    private fun selected(spinner: Spinner): String =
        spinner.selectedItem?.toString()?.trim().orEmpty()
}

