package com.github.kr328.clash.design

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.databinding.DesignEffectiveRulesBinding
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.R
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleProviderItem
import com.github.kr328.clash.service.model.RuleSource

class EffectiveRulesDesign(context: Context) : Design<EffectiveRulesDesign.Request>(context) {
    enum class FilterMode {
        ALL,
        ACTIVE,
        DISABLED,
        DELETED,
    }
    sealed class Request {
        object OpenLogcat : Request()
        data class ToggleRule(
            val ruleId: String,
            val enabled: Boolean,
        ) : Request()
        data class DeleteRule(
            val ruleId: String,
        ) : Request()
        data class RestoreRule(
            val ruleId: String,
        ) : Request()
    }

    private val binding = DesignEffectiveRulesBinding
        .inflate(context.layoutInflater, context.root, false)
    private var sourceRules: List<RuleItem> = emptyList()
    private var sourceProviders: Map<String, RuleProviderItem> = emptyMap()
    private var filterMode: FilterMode = FilterMode.ALL

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)
        binding.ruleList.layoutManager = LinearLayoutManager(context)
        binding.ruleList.adapter = EffectiveRuleAdapter(
            emptyList(),
            emptyMap(),
            { _, _ -> },
            { _ -> },
            { _ -> },
        )
        binding.btnOpenLogcat.setOnClickListener {
            requests.trySend(Request.OpenLogcat)
        }
        binding.ruleFilterSpinner.adapter =
            ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    context.getString(R.string.effective_rules_filter_all),
                    context.getString(R.string.effective_rules_filter_active),
                    context.getString(R.string.effective_rules_filter_disabled),
                    context.getString(R.string.effective_rules_filter_deleted),
                ),
            )
        binding.ruleFilterSpinner.setSelection(0, false)
        binding.ruleFilterSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                filterMode = when (position) {
                    1 -> FilterMode.ACTIVE
                    2 -> FilterMode.DISABLED
                    3 -> FilterMode.DELETED
                    else -> FilterMode.ALL
                }
                renderRules()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        })
    }

    fun patchRules(lines: List<String>) {
        val entries = lines.mapIndexed { index, line ->
            RuleItem(
                id = "legacy-$index",
                type = "LEGACY",
                value = line,
                policy = "",
                enabled = true,
                source = RuleSource.PROVIDER,
                order = index,
            )
        }
        patchRules(entries, emptyMap())
    }

    fun patchRules(rules: List<RuleItem>, providers: Map<String, RuleProviderItem>) {
        sourceRules = rules
        sourceProviders = providers
        renderRules()
    }

    private fun renderRules() {
        val rules = when (filterMode) {
            FilterMode.ALL -> sourceRules
            FilterMode.ACTIVE -> sourceRules.filter { it.enabled && !it.deleted }
            FilterMode.DISABLED -> sourceRules.filter { !it.enabled && !it.deleted }
            FilterMode.DELETED -> sourceRules.filter { it.deleted }
        }
        val onToggle: (String, Boolean) -> Unit = { id, enabled -> requests.trySend(Request.ToggleRule(id, enabled)) }
        val onDelete: (String) -> Unit = { id -> requests.trySend(Request.DeleteRule(id)) }
        val onRestore: (String) -> Unit = { id -> requests.trySend(Request.RestoreRule(id)) }
        binding.ruleList.adapter = EffectiveRuleAdapter(rules, sourceProviders, onToggle, onDelete, onRestore)
        binding.effectiveRulesSummary.text =
            context.getString(R.string.effective_rules_count, rules.size)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    private class EffectiveRuleAdapter(
        private val rules: List<RuleItem>,
        private val providers: Map<String, RuleProviderItem>,
        private val onToggle: (String, Boolean) -> Unit,
        private val onDelete: (String) -> Unit,
        private val onRestore: (String) -> Unit,
    ) : RecyclerView.Adapter<EffectiveRuleAdapter.Holder>() {
        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val index: TextView = view.findViewById(R.id.rule_index)
            val line: TextView = view.findViewById(R.id.rule_line)
            val meta: TextView = view.findViewById(R.id.rule_meta)
            val enabledSwitch: SwitchCompat = view.findViewById(R.id.rule_enabled_switch)
            val more: ImageButton = view.findViewById(R.id.rule_more)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.adapter_effective_rule, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = rules[position]
            holder.index.text = "${position + 1}."
            holder.line.text = buildRuleLine(item)
            holder.meta.text = buildMeta(item)
            holder.enabledSwitch.setOnCheckedChangeListener(null)
            holder.enabledSwitch.isChecked = item.enabled
            holder.enabledSwitch.isEnabled = !item.deleted
            holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item.id, isChecked)
            }
            holder.itemView.alpha = if (item.deleted) 0.55f else 1f
            holder.more.setOnClickListener { v ->
                PopupMenu(v.context, v).apply {
                    menu.add(0, 1, 0, if (item.enabled) "Disable" else "Enable")
                    if (item.deleted && item.isRestorable) {
                        menu.add(0, 3, 1, "Restore")
                    } else {
                        menu.add(0, 2, 1, "Delete")
                    }
                    setOnMenuItemClickListener {
                        when (it.itemId) {
                            1 -> onToggle(item.id, !item.enabled)
                            2 -> onDelete(item.id)
                            3 -> onRestore(item.id)
                        }
                        true
                    }
                }.show()
            }
        }

        override fun getItemCount(): Int = rules.size

        private fun buildRuleLine(item: RuleItem): String {
            return if (item.type.equals("MATCH", true)) {
                "MATCH,${item.policy}"
            } else if (item.type == "LEGACY") {
                item.value
            } else {
                "${item.type},${item.value},${item.policy}"
            }
        }

        private fun buildMeta(item: RuleItem): String {
            val source = if (item.source == RuleSource.MANUAL) "manual" else "provider"
            if (!item.type.equals("RULE-SET", true)) return source
            val providerName = providers[item.value]?.name ?: item.value
            return "$source • $providerName"
        }
    }
}
