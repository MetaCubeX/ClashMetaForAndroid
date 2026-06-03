package com.github.kr328.clash.design

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.databinding.DesignEffectiveRulesBinding
import com.github.kr328.clash.design.util.diffWith
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleProviderItem
import com.github.kr328.clash.service.model.RuleSource
import com.google.android.material.switchmaterial.SwitchMaterial

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
    private val adapter = EffectiveRuleAdapter(
        onToggle = { id, enabled -> requests.trySend(Request.ToggleRule(id, enabled)) },
        onDelete = { id -> requests.trySend(Request.DeleteRule(id)) },
        onRestore = { id -> requests.trySend(Request.RestoreRule(id)) },
    )

    private var sourceRules: List<RuleItem> = emptyList()
    private var sourceProviders: Map<String, RuleProviderItem> = emptyMap()
    private var filterMode: FilterMode = FilterMode.ALL
    private var searchQuery: String = ""

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.ruleList.layoutManager = LinearLayoutManager(context)
        binding.ruleList.adapter = adapter
        binding.btnOpenLogcat.setOnClickListener {
            requests.trySend(Request.OpenLogcat)
        }
        binding.ruleSearch.addTextChangedListener {
            searchQuery = it?.toString().orEmpty()
            renderRules()
        }
        val filterModes = listOf(
            FilterMode.ALL,
            FilterMode.ACTIVE,
            FilterMode.DISABLED,
            FilterMode.DELETED,
        )
        val filterLabels = listOf(
            context.getString(R.string.effective_rules_filter_all),
            context.getString(R.string.effective_rules_filter_active),
            context.getString(R.string.effective_rules_filter_disabled),
            context.getString(R.string.effective_rules_filter_deleted),
        )
        binding.ruleFilterDropdown.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, filterLabels),
        )
        binding.ruleFilterDropdown.setText(filterLabels[filterModes.indexOf(filterMode)], false)
        binding.ruleFilterDropdown.setOnItemClickListener { _, _, position, _ ->
            filterMode = filterModes.getOrElse(position) { FilterMode.ALL }
            renderRules()
        }
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
        val query = searchQuery.trim().lowercase()
        val filtered = sourceRules
            .asSequence()
            .filter { item ->
                when (filterMode) {
                    FilterMode.ALL -> true
                    FilterMode.ACTIVE -> item.enabled && !item.deleted
                    FilterMode.DISABLED -> !item.enabled && !item.deleted
                    FilterMode.DELETED -> item.deleted
                }
            }
            .filter { item -> query.isBlank() || matchesQuery(item, query) }
            .toList()

        adapter.submit(filtered, sourceProviders)
        val active = sourceRules.count { it.enabled && !it.deleted }
        val disabled = sourceRules.count { !it.enabled && !it.deleted }
        val deleted = sourceRules.count { it.deleted }
        binding.effectiveRulesSummary.text = context.getString(
            R.string.effective_rules_summary_fmt,
            filtered.size,
            active,
            disabled,
            deleted,
        )
    }

    private fun matchesQuery(item: RuleItem, query: String): Boolean {
        val provider = item.providerName ?: sourceProviders[item.value]?.name.orEmpty()
        return sequenceOf(
            item.raw,
            item.type,
            item.value,
            item.policy,
            provider,
        ).any { it.lowercase().contains(query) }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    private class EffectiveRuleAdapter(
        private val onToggle: (String, Boolean) -> Unit,
        private val onDelete: (String) -> Unit,
        private val onRestore: (String) -> Unit,
    ) : RecyclerView.Adapter<EffectiveRuleAdapter.Holder>() {
        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val index: TextView = view.findViewById(R.id.rule_index)
            val line: TextView = view.findViewById(R.id.rule_line)
            val meta: TextView = view.findViewById(R.id.rule_meta)
            val enabledSwitch: SwitchMaterial = view.findViewById(R.id.rule_enabled_switch)
            val more: ImageButton = view.findViewById(R.id.rule_more)
        }

        private var rules: List<RuleItem> = emptyList()
        private var providers: Map<String, RuleProviderItem> = emptyMap()

        fun submit(newRules: List<RuleItem>, newProviders: Map<String, RuleProviderItem>) {
            val providersChanged = providers != newProviders
            val diff = rules.diffWith(newRules, id = RuleItem::id)
            rules = newRules
            providers = newProviders
            if (providersChanged) {
                notifyDataSetChanged()
            } else {
                diff.dispatchUpdatesTo(this)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.adapter_effective_rule, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = rules[position]
            holder.index.text = "${position + 1}"
            holder.line.text = buildRuleLine(item)
            holder.meta.text = buildMeta(holder.itemView.context, item)
            holder.enabledSwitch.setOnCheckedChangeListener(null)
            holder.enabledSwitch.isChecked = item.enabled
            holder.enabledSwitch.isEnabled = !item.deleted
            holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item.id, isChecked)
            }
            holder.itemView.alpha = if (item.deleted) 0.55f else 1f
            holder.more.setOnClickListener { v ->
                PopupMenu(v.context, v).apply {
                    menu.add(
                        0,
                        1,
                        0,
                        v.context.getString(
                            if (item.enabled) R.string.rule_action_disable else R.string.rule_action_enable
                        ),
                    )
                    if (item.deleted && item.isRestorable) {
                        menu.add(0, 3, 1, v.context.getString(R.string.rule_action_restore))
                    } else {
                        menu.add(0, 2, 1, v.context.getString(R.string.delete))
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
            // Logical/opaque rules (AND/OR/NOT/SUB-RULE/SCRIPT) keep the whole
            // line in `raw` with empty value/policy — reconstructing them from
            // fields yields "AND,,". Show the raw line verbatim instead.
            if (item.value.isBlank() && item.policy.isBlank() && item.raw.isNotBlank()) {
                return item.raw
            }
            return if (item.type.equals("MATCH", true)) {
                "MATCH,${item.policy}"
            } else if (item.type == "LEGACY") {
                item.value
            } else {
                "${item.type},${item.value},${item.policy}"
            }
        }

        private fun buildMeta(context: Context, item: RuleItem): String {
            val source = context.getString(
                if (item.source == RuleSource.MANUAL) {
                    R.string.effective_rules_source_manual
                } else {
                    R.string.effective_rules_source_provider
                }
            )
            if (!item.type.equals("RULE-SET", true)) return source
            val providerName = providers[item.value]?.name ?: item.providerName ?: item.value
            return "$source · $providerName"
        }
    }
}
