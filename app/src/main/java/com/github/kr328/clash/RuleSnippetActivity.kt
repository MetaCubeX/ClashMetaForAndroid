package com.github.kr328.clash

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
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
                        RuleSnippetDesign.Request.OpenCreateSheet -> launch { showCreateSheet(design, null) }
                        RuleSnippetDesign.Request.OpenManualRules ->
                            startActivity(EffectiveRulesActivity::class.intent)
                        is RuleSnippetDesign.Request.ToggleProvider -> launch {
                            mutateProvider(design, it.id, it.name) { p -> p.copy(enabled = it.enabled) }
                        }
                        is RuleSnippetDesign.Request.DeleteProvider -> launch {
                            deleteProvider(design, it.id, it.name)
                        }
                        is RuleSnippetDesign.Request.EditProvider -> launch {
                            showCreateSheet(design, it.id, it.name)
                        }
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

    private suspend fun showCreateSheet(
        design: RuleSnippetDesign,
        editingProviderId: String?,
        editingProviderName: String? = null,
    ) {
        val uuid = withProfile { queryActive()?.uuid }
        if (uuid == null) {
            design.showToast(DesignR.string.no_profile_selected, ToastDuration.Long)
            return
        }
        proxyGroups = loadAvailableProxyGroups(uuid)
        val state = withProfile { readRuleState(uuid) }
            ?.let { runCatching { json.decodeFromString(RuleState.serializer(), it) }.getOrNull() }
            ?: RuleState()
        val editingProvider = state.providers.firstOrNull { p ->
            (editingProviderId != null && p.id == editingProviderId) ||
                (editingProviderName != null && p.name.equals(editingProviderName, true))
        }

        val dialog = AppBottomSheetDialog(this, fitContentHeight = true)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_rules_create, null)
        dialog.setContentView(view)

        val title = view.findViewById<TextView>(R.id.tv_sheet_title)
        val typeSpinner = view.findViewById<Spinner>(R.id.spinner_create_type)
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
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeOptions)

        val presetSpinner = view.findViewById<Spinner>(R.id.spinner_preset)
        presetSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(DesignR.string.rule_quick_openai),
                getString(DesignR.string.rule_quick_telegram),
                getString(DesignR.string.rule_quick_discord),
                getString(DesignR.string.rule_quick_ads),
                getString(DesignR.string.rule_quick_ai),
                "Facebook",
                "Instagram",
                "X/Twitter",
                "YouTube",
                "TikTok",
                "Netflix",
            )
        )
        val insertModes = listOf(
            getString(DesignR.string.rule_insert_append),
            getString(DesignR.string.rule_insert_prepend),
            getString(DesignR.string.rule_insert_top),
        )
        view.findViewById<Spinner>(R.id.spinner_manual_insert_mode).adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, insertModes)

        val policyOptions = buildList {
            add("DIRECT")
            add("REJECT")
            add("REJECT-DROP")
            add("PASS")
            addAll(proxyGroups.distinct())
        }
        val validPolicy = preferredPolicy(policyOptions)
        val presetPolicyOptions = listOf(getString(DesignR.string.rule_policy_auto)) + policyOptions
        view.findViewById<Spinner>(R.id.spinner_preset_policy).apply {
            adapter = ArrayAdapter(this@RuleSnippetActivity, android.R.layout.simple_spinner_dropdown_item, presetPolicyOptions)
            setSelection(0, false)
        }
        view.findViewById<Spinner>(R.id.spinner_provider_policy).apply {
            adapter = ArrayAdapter(this@RuleSnippetActivity, android.R.layout.simple_spinner_dropdown_item, policyOptions)
            setSelection(policyOptions.indexOf(validPolicy).coerceAtLeast(0), false)
        }
        view.findViewById<Spinner>(R.id.spinner_manual_policy).apply {
            adapter = ArrayAdapter(this@RuleSnippetActivity, android.R.layout.simple_spinner_dropdown_item, policyOptions)
            setSelection(policyOptions.indexOf(validPolicy).coerceAtLeast(0), false)
        }
        view.findViewById<Spinner>(R.id.spinner_provider_behavior).adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("classical", "domain", "ipcidr"))
        view.findViewById<Spinner>(R.id.spinner_manual_type).adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("GEOSITE", "GEOIP", "Custom"))

        title.text = if (editingProvider != null) getString(DesignR.string.edit) else getString(DesignR.string.rule_add_new)
        if (editingProvider != null) {
            typeSpinner.setSelection(1, false)
            view.findViewById<TextInputEditText>(R.id.input_provider_name).setText(editingProvider.name)
            view.findViewById<TextInputEditText>(R.id.input_provider_url).setText(editingProvider.url)
            view.findViewById<SwitchMaterial>(R.id.switch_provider_enabled).isChecked = editingProvider.enabled
            val behaviors = listOf("classical", "domain", "ipcidr")
            view.findViewById<Spinner>(R.id.spinner_provider_behavior)
                .setSelection(behaviors.indexOf(editingProvider.behavior).coerceAtLeast(0), false)
        }

        fun insertModeFromSpinner(s: Spinner): String = when (s.selectedItemPosition) {
            1 -> "prepend"
            2 -> "index:0"
            else -> "append"
        }

        fun selected(spinner: Spinner) = spinner.selectedItem?.toString()?.trim().orEmpty()
        fun buildPreview() {
            when (typeSpinner.selectedItemPosition) {
                0 -> {
                    val chosen = selected(view.findViewById(R.id.spinner_preset_policy))
                    val policy = if (chosen == getString(DesignR.string.rule_policy_auto)) {
                        preferredPolicy(policyOptions)
                    } else {
                        chosen.ifBlank { preferredPolicy(policyOptions) }
                    }
                    val lines = presetLines(presetSpinner.selectedItemPosition, policy)
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
                        "GEOSITE", "GEOIP" -> "$type,$value,$policy"
                        else -> value
                    }
                    preview.text = "- $line"
                }
            }
        }

        fun syncGroups() {
            presetGroup.visibility = if (typeSpinner.selectedItemPosition == 0) View.VISIBLE else View.GONE
            providerGroup.visibility = if (typeSpinner.selectedItemPosition == 1) View.VISIBLE else View.GONE
            manualGroup.visibility = if (typeSpinner.selectedItemPosition == 2) View.VISIBLE else View.GONE
            buildPreview()
        }
        typeSpinner.onItemSelectedListener = SimpleItemSelectedListener { syncGroups() }
        presetSpinner.onItemSelectedListener = SimpleItemSelectedListener { buildPreview() }
        view.findViewById<Spinner>(R.id.spinner_preset_policy).onItemSelectedListener = SimpleItemSelectedListener { buildPreview() }
        view.findViewById<Spinner>(R.id.spinner_provider_behavior).onItemSelectedListener = SimpleItemSelectedListener { buildPreview() }
        view.findViewById<Spinner>(R.id.spinner_provider_policy).onItemSelectedListener = SimpleItemSelectedListener { buildPreview() }
        view.findViewById<Spinner>(R.id.spinner_manual_policy).onItemSelectedListener = SimpleItemSelectedListener { buildPreview() }
        view.findViewById<Spinner>(R.id.spinner_manual_type).onItemSelectedListener = SimpleItemSelectedListener { buildPreview() }

        applyButton.setOnClickListener {
            launch {
                val ok = when (typeSpinner.selectedItemPosition) {
                    0 -> {
                        val chosen = view.findViewById<Spinner>(R.id.spinner_preset_policy).selectedItem?.toString()?.trim().orEmpty()
                        val policy = if (chosen == getString(DesignR.string.rule_policy_auto)) {
                            preferredPolicy(policyOptions)
                        } else {
                            chosen.ifBlank { preferredPolicy(policyOptions) }
                        }
                        val rules = presetLines(presetSpinner.selectedItemPosition, policy)
                        withProfile {
                            addRules(uuid, rules, addMode = true, insertMode = "append")
                        }
                    }
                    1 -> {
                        applyProviderFromSheet(uuid, state, editingProvider, view)
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
        val behavior = view.findViewById<Spinner>(R.id.spinner_provider_behavior).selectedItem?.toString()?.trim().orEmpty().ifBlank { "classical" }
        val policy = view.findViewById<Spinner>(R.id.spinner_provider_policy).selectedItem?.toString()?.trim().orEmpty().ifBlank { "DIRECT" }
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
        val type = view.findViewById<Spinner>(R.id.spinner_manual_type).selectedItem?.toString()?.trim().orEmpty()
        val value = view.findViewById<TextInputEditText>(R.id.input_manual_value).text?.toString()?.trim().orEmpty()
        val policy = view.findViewById<Spinner>(R.id.spinner_manual_policy).selectedItem?.toString()?.trim().orEmpty().ifBlank { "DIRECT" }
        return when (type) {
            "GEOSITE", "GEOIP" -> if (value.isBlank()) null else "$type,$value,$policy"
            else -> value.ifBlank { null }
        }
    }

    private fun presetLines(index: Int, policy: String): List<String> = when (index) {
        1 -> listOf("GEOSITE,telegram,$policy")
        2 -> listOf("GEOSITE,discord,$policy")
        3 -> listOf("GEOSITE,category-ads-all,REJECT-DROP")
        4 -> listOf(
            "GEOSITE,openai,$policy",
            "GEOSITE,anthropic,$policy",
            "GEOSITE,huggingface,$policy",
        )
        5 -> listOf("GEOSITE,facebook,$policy")
        6 -> listOf("GEOSITE,instagram,$policy")
        7 -> listOf("GEOSITE,twitter,$policy")
        8 -> listOf("GEOSITE,youtube,$policy")
        9 -> listOf("GEOSITE,tiktok,$policy")
        10 -> listOf("GEOSITE,netflix,$policy")
        else -> listOf("GEOSITE,openai,$policy")
    }

    private fun preferredPolicy(all: List<String>): String {
        val builtIn = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS")
        return all.firstOrNull { it.uppercase() !in builtIn } ?: "DIRECT"
    }

    private suspend fun mutateProvider(
        design: RuleSnippetDesign,
        providerId: String,
        providerName: String?,
        transform: (com.github.kr328.clash.service.model.RuleProviderItem) -> com.github.kr328.clash.service.model.RuleProviderItem,
    ) {
        val uuid = withProfile { queryActive()?.uuid } ?: return
        val state = withProfile { readRuleState(uuid) }
            ?.let { runCatching { json.decodeFromString(RuleState.serializer(), it) }.getOrNull() }
            ?: return
        val next = state.copy(
            providers = state.providers.map {
                if (it.id == providerId || (providerName != null && it.name.equals(providerName, true))) transform(it) else it
            }
        )
        val ok = withProfile { applyRuleState(uuid, json.encodeToString(RuleState.serializer(), next)) }
        if (ok) refreshExistingProviders(design)
    }

    private suspend fun deleteProvider(design: RuleSnippetDesign, providerId: String, providerName: String?) {
        val uuid = withProfile { queryActive()?.uuid } ?: return
        val state = withProfile { readRuleState(uuid) }
            ?.let { runCatching { json.decodeFromString(RuleState.serializer(), it) }.getOrNull() }
            ?: return
        val provider = state.providers.firstOrNull {
            it.id == providerId || (providerName != null && it.name.equals(providerName, true))
        } ?: return
        val next = state.copy(
            providers = state.providers.filterNot { it.id == providerId },
            rules = state.rules.map { r ->
                if (r.providerName.equals(provider.name, true)) r.copy(deleted = true, enabled = false) else r
            }
        )
        val ok = withProfile { applyRuleState(uuid, json.encodeToString(RuleState.serializer(), next)) }
        if (ok) refreshExistingProviders(design)
    }

    private suspend fun loadAvailableProxyGroups(uuid: UUID): List<String> {
        val fromRuntime = runCatching {
            withClash { queryProxyGroupNames(uiStore.proxyExcludeNotSelectable) }
        }.getOrDefault(emptyList())
        if (fromRuntime.isNotEmpty()) return fromRuntime

        val fromProfile = runCatching {
            withProfile { readProxyGroupsPreview(uuid).keys.toList() }
        }.getOrDefault(emptyList())
        return fromProfile
    }
}

private class SimpleItemSelectedListener(
    private val onChange: () -> Unit,
) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onChange()
    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
}
