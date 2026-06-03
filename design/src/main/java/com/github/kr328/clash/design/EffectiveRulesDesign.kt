package com.github.kr328.clash.design

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

class EffectiveRulesDesign(context: Context) : Design<EffectiveRulesDesign.Request>(context) {
    sealed class Request {
        object OpenLogcat : Request()
    }

    private val binding = DesignEffectiveRulesBinding
        .inflate(context.layoutInflater, context.root, false)
    private val adapter = EffectiveRuleAdapter()

    private var sourceRules: List<RuleItem> = emptyList()
    private var sourceProviders: Map<String, RuleProviderItem> = emptyMap()
    private var searchQuery: String = ""

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.header.screenTitle.text = context.getString(R.string.effective_rules_title)
        binding.ruleList.layoutManager = LinearLayoutManager(context)
        binding.ruleList.adapter = adapter
        binding.btnOpenLogcat.setOnClickListener {
            requests.trySend(Request.OpenLogcat)
        }
        binding.ruleSearch.addTextChangedListener {
            searchQuery = it?.toString().orEmpty()
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
            .filter { item -> query.isBlank() || matchesQuery(item, query) }

        adapter.submit(filtered, sourceProviders)
        binding.effectiveRulesSummary.text = context.getString(
            R.string.effective_rules_count_fmt,
            filtered.size,
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

    private class EffectiveRuleAdapter : RecyclerView.Adapter<EffectiveRuleAdapter.Holder>() {
        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val index: TextView = view.findViewById(R.id.rule_index)
            val line: TextView = view.findViewById(R.id.rule_line)
            val meta: TextView = view.findViewById(R.id.rule_meta)
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
