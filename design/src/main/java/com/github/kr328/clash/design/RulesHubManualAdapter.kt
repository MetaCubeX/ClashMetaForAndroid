package com.github.kr328.clash.design

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.service.model.RuleItem
import com.google.android.material.materialswitch.MaterialSwitch

class RulesHubManualAdapter(
    private val onToggle: (String, Boolean) -> Unit,
    private val onEdit: (String) -> Unit,
) : RecyclerView.Adapter<RulesHubManualAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
        val index: TextView = view.findViewById(R.id.rule_index)
        val line: TextView = view.findViewById(R.id.rule_line)
        val targetMissing: TextView = view.findViewById(R.id.target_missing)
        val enabledSwitch: MaterialSwitch = view.findViewById(R.id.rule_enabled_switch)
        val edit: ImageButton = view.findViewById(R.id.btn_edit)
    }

    private var rows: List<RuleItem> = emptyList()
    private var knownPolicies: Set<String> = emptySet()

    fun submit(rules: List<RuleItem>, policies: Set<String>) {
        rows = rules
        knownPolicies = policies
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rules_hub_manual, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = rows[position]
        holder.index.text = "${position + 1}"
        holder.line.text = RulesHubRowBuilder.buildRuleLine(item)
        holder.line.alpha = if (item.deleted) 0.55f else if (!item.enabled) 0.75f else 1f
        holder.targetMissing.visibility =
            if (RulesHubRowBuilder.isTargetMissing(item, knownPolicies)) View.VISIBLE else View.GONE
        holder.enabledSwitch.setOnCheckedChangeListener(null)
        holder.enabledSwitch.isChecked = item.enabled
        holder.enabledSwitch.isEnabled = !item.deleted
        holder.enabledSwitch.setOnCheckedChangeListener { _, checked ->
            onToggle(item.id, checked)
        }
        holder.edit.setOnClickListener { onEdit(item.id) }
    }

    override fun getItemCount(): Int = rows.size
}
