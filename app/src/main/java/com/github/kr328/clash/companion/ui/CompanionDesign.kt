package com.github.kr328.clash.companion.ui

import android.app.Activity
import android.content.Context
import android.view.View
import com.github.kr328.clash.companion.CompanionStore
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.OnChangedListener
import com.github.kr328.clash.design.preference.category
import com.github.kr328.clash.design.preference.clickable
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.switch
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

/**
 * Companion "Remote control" hub. Two sections — Agent (this device can be controlled) and
 * Controller (control other devices). Both roles compile in; form factor only changes emphasis
 * (handled by the activity), nothing is hard-disabled.
 */
class CompanionDesign(
    context: Context,
    private val store: CompanionStore,
    onAgentToggle: (Boolean) -> Unit,
) : Design<CompanionDesign.Request>(context) {
    enum class Request {
        ShowQr,
        ManagePairings,
        ScanToPair,
        OpenControl,
    }

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface
        binding.header.screenTitle.text = (context as? Activity)?.title?.toString().orEmpty()

        val screen = preferenceScreen(context) {
            category(R.string.companion_agent_section)

            switch(
                value = store::agentEnabled,
                icon = R.drawable.ic_baseline_stack,
                title = R.string.companion_agent_enable_title,
                summary = R.string.companion_agent_enable_summary,
            ) {
                listener = OnChangedListener { onAgentToggle(store.agentEnabled) }
            }

            clickable(
                title = R.string.companion_show_qr_title,
                icon = R.drawable.ic_baseline_language,
                summary = R.string.companion_show_qr_summary,
            ) {
                clicked { requests.trySend(Request.ShowQr) }
            }

            clickable(
                title = R.string.companion_manage_pairings_title,
                icon = R.drawable.ic_baseline_hide,
                summary = R.string.companion_manage_pairings_summary,
            ) {
                clicked { requests.trySend(Request.ManagePairings) }
            }

            category(R.string.companion_controller_section)

            clickable(
                title = R.string.companion_scan_title,
                icon = R.drawable.ic_baseline_language,
                summary = R.string.companion_scan_summary,
            ) {
                clicked { requests.trySend(Request.ScanToPair) }
            }

            clickable(
                title = R.string.companion_devices_title,
                icon = R.drawable.ic_baseline_swap_horiz,
                summary = R.string.companion_devices_summary,
            ) {
                clicked { requests.trySend(Request.OpenControl) }
            }
        }

        binding.content.addView(screen.root)
    }
}
