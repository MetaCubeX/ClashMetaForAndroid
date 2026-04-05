package com.github.kr328.clash.design

import android.content.Context
import androidx.appcompat.widget.PopupMenu
import android.text.TextUtils
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import com.github.kr328.clash.design.databinding.DesignRuleSnippetBinding
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import java.net.URL

class RuleSnippetDesign(context: Context) : Design<RuleSnippetDesign.Request>(context) {
    enum class Request {
        CopyYaml,
        OpenEngineOverrides,
        ApplyRuleProvider,
        OpenRuleProvidersEditor,
    }

    private val binding = DesignRuleSnippetBinding
        .inflate(context.layoutInflater, context.root, false)

    private val manualRuleLines = mutableListOf<String>()
    private var isProviderMode = true
    private var lastProviderYaml: String = ""
    private var yamlPreviewExpanded: Boolean = false

    override val root: View
        get() = binding.root

    val generatedYaml: String
        get() = binding.outputText.text?.toString().orEmpty()

    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)

        val scopeLabels = listOf(
            context.getString(R.string.rule_apply_active_only),
            context.getString(R.string.rule_apply_all_imported),
        )
        binding.spinnerApplyScope.adapter =
            ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                scopeLabels,
            )

        val ruleTypes = context.resources.getStringArray(R.array.clash_manual_rule_types)
        binding.spinnerRuleType.adapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, ruleTypes)
        binding.spinnerRuleType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                val type = ruleTypes.getOrNull(position).orEmpty()
                binding.manualMatchHint.visibility =
                    if (type == "MATCH") View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        setPolicyOptions(emptyList())

        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            isProviderMode = checkedId == R.id.btn_mode_provider
            binding.panelProvider.visibility = if (isProviderMode) View.VISIBLE else View.GONE
            binding.panelManual.visibility = if (isProviderMode) View.GONE else View.VISIBLE
            updateOutput()
        }

        binding.btnGenerate.setOnClickListener {
            regenerateProvider()
            updateOutput()
        }

        binding.btnApplyProvider.setOnClickListener {
            requests.trySend(Request.ApplyRuleProvider)
        }

        binding.btnRuleOverflow.setOnClickListener { anchor ->
            PopupMenu(context, anchor).apply {
                menuInflater.inflate(R.menu.rule_snippet_overflow, menu)
                menu.findItem(R.id.menu_rule_advanced).isVisible = isProviderMode
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.menu_rule_copy -> {
                            requests.trySend(Request.CopyYaml)
                            true
                        }
                        R.id.menu_rule_edit_providers -> {
                            requests.trySend(Request.OpenRuleProvidersEditor)
                            true
                        }
                        R.id.menu_rule_advanced -> {
                            val panel = binding.panelProviderAdvanced
                            val show = panel.visibility != View.VISIBLE
                            panel.visibility = if (show) View.VISIBLE else View.GONE
                            true
                        }
                        R.id.menu_rule_engine -> {
                            requests.trySend(Request.OpenEngineOverrides)
                            true
                        }
                        else -> false
                    }
                }
            }.show()
        }

        binding.btnPrependRule.setOnClickListener {
            addManualRule(prepend = true)
        }
        binding.btnAppendRule.setOnClickListener {
            addManualRule(prepend = false)
        }
        binding.btnClearManual.setOnClickListener {
            manualRuleLines.clear()
            updateOutput()
        }

        binding.btnToggleYaml.setOnClickListener {
            yamlPreviewExpanded = !yamlPreviewExpanded
            applyYamlPreviewExpansion()
        }

        regenerateProvider()
        updateOutput()
    }

    private fun applyYamlPreviewExpansion() {
        val tv = binding.outputText
        if (yamlPreviewExpanded) {
            tv.maxLines = Int.MAX_VALUE
            tv.ellipsize = null
            binding.btnToggleYaml.setText(R.string.rule_yaml_hide_preview)
        } else {
            tv.maxLines = 5
            tv.ellipsize = TextUtils.TruncateAt.END
            binding.btnToggleYaml.setText(R.string.rule_yaml_show_preview)
        }
    }

    /**
     * Built-in policies plus proxy group names from the running engine (when available).
     */
    fun setPolicyOptions(proxyGroups: List<String>) {
        val policies = buildList {
            add("DIRECT")
            add("REJECT")
            add("REJECT-DROP")
            add("PASS")
            addAll(proxyGroups.distinct())
        }
        val itemLayout = android.R.layout.simple_spinner_dropdown_item
        val a = ArrayAdapter(context, itemLayout, policies)
        binding.spinnerPolicy.adapter = a
        binding.spinnerProviderPolicy.adapter =
            ArrayAdapter(context, itemLayout, policies.toList())
    }

    /** When true, [Request.ApplyRuleProvider] should merge into every imported profile. */
    fun applyToAllImportedProfiles(): Boolean =
        binding.spinnerApplyScope.selectedItemPosition == 1

    /** YAML block with only `rule-providers:` (for merging into config). */
    fun ruleProvidersYamlForMerge(): String {
        val url = binding.inputUrl.text?.toString()?.trim().orEmpty()
        val hours = binding.inputHours.text?.toString()?.toLongOrNull() ?: 24L
        var key = binding.inputKey.text?.toString()?.trim().orEmpty()
        if (key.isEmpty() && url.isNotEmpty()) {
            key = guessKeyFromUrl(url)
        }
        val intervalSec = (hours.coerceIn(1L, 24 * 30) * 3600).toInt()
        val k = key.ifEmpty { "MyRules" }
        return buildString {
            appendLine("rule-providers:")
            append("  ").append(k).appendLine(":")
            appendLine("    type: http")
            appendLine("    behavior: classical")
            append("    url: \"").append(url.ifEmpty { "https://example.com/rules.yaml" }).appendLine("\"")
            append("    path: ./ruleset/").append(k).appendLine(".yaml")
            append("    interval: ").append(intervalSec).appendLine()
        }.trimEnd()
    }

    /** First rules list line to inject after `rules:` (RULE-SET …). */
    fun prependRuleLineForMerge(): String {
        var key = binding.inputKey.text?.toString()?.trim().orEmpty()
        val url = binding.inputUrl.text?.toString()?.trim().orEmpty()
        if (key.isEmpty() && url.isNotEmpty()) {
            key = guessKeyFromUrl(url)
        }
        val k = key.ifEmpty { "MyRules" }
        val selector = binding.spinnerProviderPolicy.selectedItem?.toString()?.trim().orEmpty()
            .ifBlank { "DIRECT" }
        return "  - RULE-SET,$k,$selector"
    }

    private fun addManualRule(prepend: Boolean) {
        val types = context.resources.getStringArray(R.array.clash_manual_rule_types)
        val type = types.getOrNull(binding.spinnerRuleType.selectedItemPosition).orEmpty()
        val content = binding.inputRuleContent.text?.toString()?.trim().orEmpty()
        val policy = binding.spinnerPolicy.selectedItem?.toString()?.trim().orEmpty()
            .ifBlank { "DIRECT" }

        val line = when (type) {
            "MATCH" -> "MATCH,$policy"
            else -> {
                if (content.isEmpty()) {
                    Toast.makeText(context, R.string.rule_manual_need_content, Toast.LENGTH_SHORT)
                        .show()
                    return
                }
                "$type,$content,$policy"
            }
        }
        if (prepend) {
            manualRuleLines.add(0, line)
        } else {
            manualRuleLines.add(line)
        }
        updateOutput()
    }

    private fun regenerateProvider() {
        val url = binding.inputUrl.text?.toString()?.trim().orEmpty()
        val hours = binding.inputHours.text?.toString()?.toLongOrNull() ?: 24L
        var key = binding.inputKey.text?.toString()?.trim().orEmpty()
        if (key.isEmpty() && url.isNotEmpty()) {
            key = guessKeyFromUrl(url)
            binding.inputKey.setText(key)
        }
        val selector = binding.spinnerProviderPolicy.selectedItem?.toString()?.trim().orEmpty()
            .ifBlank { "DIRECT" }
        val intervalSec = (hours.coerceIn(1L, 24 * 30) * 3600).toInt()
        val k = key.ifEmpty { "MyRules" }

        lastProviderYaml = buildString {
            appendLine("rule-providers:")
            append("  ").append(k).appendLine(":")
            appendLine("    type: http")
            appendLine("    behavior: classical")
            append("    url: \"").append(url.ifEmpty { "https://example.com/rules.yaml" }).appendLine("\"")
            append("    path: ./ruleset/").append(k).appendLine(".yaml")
            append("    interval: ").append(intervalSec).appendLine()
            appendLine()
            appendLine("rules:")
            append("  - RULE-SET,").append(k).append(",").append(selector)
                .appendLine()
            appendLine("  - MATCH,DIRECT")
        }
    }

    private fun buildManualYaml(): String = buildString {
        appendLine("rules:")
        if (manualRuleLines.isEmpty()) {
            appendLine("  # Use Prepend / Append to add lines")
        } else {
            manualRuleLines.forEach { append("  - ").appendLine(it) }
        }
        appendLine("  - MATCH,DIRECT")
    }

    private fun updateOutput() {
        binding.outputText.text =
            if (isProviderMode) {
                lastProviderYaml
            } else {
                buildManualYaml()
            }
        applyYamlPreviewExpansion()
    }

    private fun guessKeyFromUrl(url: String): String =
        try {
            val path = URL(url).path.trim('/').split('/').filter { it.isNotBlank() }.lastOrNull()
                ?: "rules"
            path.replace(Regex("[^a-zA-Z0-9._-]"), "-").take(32)
        } catch (_: Exception) {
            "MyRules"
        }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
