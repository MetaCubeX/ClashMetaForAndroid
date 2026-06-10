package com.github.kr328.clash.design

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleProviderItem
import com.google.android.material.materialswitch.MaterialSwitch

class RulesHubProviderAdapter(
    private val onToggle: (String, Boolean) -> Unit,
    private val onRestore: (String) -> Unit,
) : RecyclerView.Adapter<RulesHubProviderAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val index: TextView = view.findViewById(R.id.rule_index)
        val line: TextView = view.findViewById(R.id.rule_line)
        val meta: TextView = view.findViewById(R.id.rule_meta)
        val enabledSwitch: MaterialSwitch = view.findViewById(R.id.rule_enabled_switch)
        val restore: ImageButton = view.findViewById(R.id.btn_restore)
    }

    private var rows: List<RuleItem> = emptyList()
    private var providers: Map<String, RuleProviderItem> = emptyMap()

    fun submit(rules: List<RuleItem>, providerMap: Map<String, RuleProviderItem>) {
        rows = rules
        providers = providerMap
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rules_hub_provider, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = rows[position]
        holder.index.text = "${position + 1}"
        holder.line.text = RulesHubRowBuilder.buildRuleLine(item)
        holder.line.alpha = if (item.deleted) 0.55f else if (!item.enabled) 0.75f else 1f
        holder.meta.visibility = View.VISIBLE
        holder.meta.text = if (item.type.equals("RULE-SET", true)) {
            providers[item.value]?.name ?: item.providerName ?: item.value
        } else {
            ""
        }
        holder.meta.visibility = if (holder.meta.text.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.enabledSwitch.setOnCheckedChangeListener(null)
        holder.enabledSwitch.isChecked = item.enabled
        holder.enabledSwitch.isEnabled = !item.deleted
        holder.enabledSwitch.setOnCheckedChangeListener { _, checked ->
            onToggle(item.id, checked)
        }
        val showRestore = item.deleted && item.isRestorable
        holder.restore.visibility = if (showRestore) View.VISIBLE else View.GONE
        holder.restore.setOnClickListener { onRestore(item.id) }
    }

    override fun getItemCount(): Int = rows.size
}
