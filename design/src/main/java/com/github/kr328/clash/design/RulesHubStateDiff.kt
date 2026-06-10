package com.github.kr328.clash.design

import android.content.Context
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleState

object RulesHubStateDiff {
    data class Counts(
        val added: Int,
        val removed: Int,
        val disabled: Int,
    ) {
        val hasChanges: Boolean = added > 0 || removed > 0 || disabled > 0
    }

    fun compute(baseline: RuleState, current: RuleState): Counts {
        val baseRules = baseline.rules.associateBy(RuleItem::id)
        val curRules = current.rules.associateBy(RuleItem::id)

        var added = 0
        var removed = 0
        var disabled = 0

        for ((id, cur) in curRules) {
            val base = baseRules[id]
            when {
                base == null && !cur.deleted -> added++
                cur.deleted && (base == null || !base.deleted) -> removed++
                base != null && base.enabled && !cur.deleted && !cur.enabled -> disabled++
            }
        }
        for ((id, base) in baseRules) {
            if (id !in curRules && !base.deleted) removed++
        }
        return Counts(added, removed, disabled)
    }

    fun formatSummary(context: Context, baseline: RuleState, current: RuleState): String? {
        val counts = compute(baseline, current)
        if (!counts.hasChanges) return null
        return context.getString(
            R.string.rules_hub_diff_summary_fmt,
            counts.added,
            counts.removed,
            counts.disabled,
        )
    }
}
