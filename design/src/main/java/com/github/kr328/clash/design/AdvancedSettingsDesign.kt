package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.clickable
import com.github.kr328.clash.design.preference.category
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.switch
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.store.ServiceStore

class AdvancedSettingsDesign(
    context: Context,
    srvStore: ServiceStore,
) : Design<AdvancedSettingsDesign.Request>(context) {
    enum class Request {
        StartExpertMetaFeatures,
        StartExpertOverride,
    }

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface
        binding.activityBarLayout.applyFrom(context)
        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        val screen = preferenceScreen(context) {
            category(R.string.advanced_category_runtime_tuning)

            clickable(
                title = R.string.meta_features,
                summary = R.string.advanced_meta_features_summary,
            ) {
                clicked {
                    requests.trySend(Request.StartExpertMetaFeatures)
                }
            }

            category(R.string.advanced_category_network_control)

            clickable(
                title = R.string.override,
                summary = R.string.advanced_override_summary,
            ) {
                clicked {
                    requests.trySend(Request.StartExpertOverride)
                }
            }

            category(R.string.advanced_vpn_experiments)

            switch(
                value = srvStore::allowBypass,
                title = R.string.allow_bypass,
                summary = R.string.allow_bypass_dev_summary,
            )
        }

        binding.content.addView(screen.root)
    }
}
