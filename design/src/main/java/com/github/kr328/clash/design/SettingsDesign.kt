package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.databinding.DesignSettingsBinding
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class SettingsDesign(context: Context) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        StartApp,
        StartGeo,
        StartNetwork,
        StartMetaFeatures,
        StartDnsHosts,
        StartTunnels,
        StartOverride,
    }

    private val binding = DesignSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val ui = UiStore(context)

    init {
        binding.self = this
        binding.header.screenTitle.text = context.getString(R.string.main_advanced_settings)
        applyExperimentalVisibility()
    }

    /**
     * Re-read the experimental/expert gates and show/hide their entries. Called on
     * init AND whenever the screen returns to the foreground, so toggling a switch in
     * App settings reflects here without leaving and re-entering Advanced.
     */
    fun applyExperimentalVisibility() {
        binding.cardDnsHosts.visibility =
            if (ui.dnsHostsEnabled) View.VISIBLE else View.GONE
        binding.cardTunnels.visibility =
            if (ui.tunnelsEnabled) View.VISIBLE else View.GONE
        binding.cardOverride.visibility =
            if (ui.expertEnabled) View.VISIBLE else View.GONE
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
