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
    }

    private val binding = DesignSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.header.screenTitle.text = context.getString(R.string.main_advanced_settings)
        // Experimental DNS & Hosts entry is hidden until opted in (AppSettings).
        binding.cardDnsHosts.visibility =
            if (UiStore(context).dnsHostsEnabled) View.VISIBLE else View.GONE
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
