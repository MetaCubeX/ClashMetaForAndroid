package com.github.kr328.clash.design

import android.widget.TextView
import com.google.android.material.color.MaterialColors

object RulesHubPolicyChip {
    fun bind(
        chip: TextView,
        policy: String,
        targetMissing: Boolean,
    ) {
        val label = policy.trim()
        if (label.isBlank()) {
            chip.visibility = TextView.GONE
            return
        }
        chip.visibility = TextView.VISIBLE
        chip.text = label

        val ctx = chip.context
        when {
            targetMissing -> {
                chip.setBackgroundResource(R.drawable.bg_m3_policy_chip_missing)
                chip.setTextColor(
                    MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnErrorContainer),
                )
            }
            label.equals("DIRECT", ignoreCase = true) -> {
                chip.setBackgroundResource(R.drawable.bg_m3_policy_chip_direct)
                chip.setTextColor(ctx.getColor(R.color.policy_on_direct_container))
            }
            label.equals("REJECT", ignoreCase = true) ||
                label.equals("REJECT-DROP", ignoreCase = true) -> {
                chip.setBackgroundResource(R.drawable.bg_m3_policy_chip_reject)
                chip.setTextColor(
                    MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnErrorContainer),
                )
            }
            else -> {
                chip.setBackgroundResource(R.drawable.bg_m3_policy_chip_primary)
                chip.setTextColor(
                    MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnPrimaryContainer),
                )
            }
        }
    }
}
