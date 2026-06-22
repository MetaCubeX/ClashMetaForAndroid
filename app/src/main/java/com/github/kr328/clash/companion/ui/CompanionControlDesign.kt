package com.github.kr328.clash.companion.ui

import android.app.Activity
import android.content.Context
import android.view.View
import com.github.kr328.clash.companion.controller.ControllerStore
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.category
import com.github.kr328.clash.design.preference.clickable
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.tips
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

/** Controller screen: the controller's paired agents, each opening an action sheet. */
class CompanionControlDesign(
    context: Context,
    agents: List<ControllerStore.PairedAgent>,
    onAgentClick: (String) -> Unit,
) : Design<Unit>(context) {
    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface
        binding.header.screenTitle.text = (context as? Activity)?.title?.toString().orEmpty()

        val screen = preferenceScreen(context) {
            category(R.string.companion_devices_title)

            if (agents.isEmpty()) {
                tips(R.string.companion_no_devices_hint)
            } else {
                agents.forEach { agent ->
                    clickable(
                        title = R.string.companion_devices_title,
                        icon = R.drawable.ic_baseline_swap_horiz,
                    ) {
                        title = agent.name
                        summary = "${agent.host}:${agent.port}"
                        clicked { onAgentClick(agent.deviceId) }
                    }
                }
            }
        }

        binding.content.addView(screen.root)
    }
}
