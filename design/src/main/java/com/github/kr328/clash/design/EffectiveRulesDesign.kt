package com.github.kr328.clash.design

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.databinding.DesignEffectiveRulesBinding
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.R

class EffectiveRulesDesign(context: Context) : Design<EffectiveRulesDesign.Request>(context) {
    enum class Request {
        OpenLogcat,
    }

    private val binding = DesignEffectiveRulesBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)
        binding.ruleList.layoutManager = LinearLayoutManager(context)
        binding.btnOpenLogcat.setOnClickListener {
            requests.trySend(Request.OpenLogcat)
        }
    }

    fun patchRules(lines: List<String>) {
        binding.ruleList.adapter = EffectiveRuleAdapter(lines)
        binding.effectiveRulesSummary.text =
            context.getString(R.string.effective_rules_count, lines.size)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    private class EffectiveRuleAdapter(
        private val lines: List<String>,
    ) : RecyclerView.Adapter<EffectiveRuleAdapter.Holder>() {
        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val index: TextView = view.findViewById(R.id.rule_index)
            val line: TextView = view.findViewById(R.id.rule_line)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.adapter_effective_rule, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.index.text = "${position + 1}."
            holder.line.text = lines[position]
        }

        override fun getItemCount(): Int = lines.size
    }
}
