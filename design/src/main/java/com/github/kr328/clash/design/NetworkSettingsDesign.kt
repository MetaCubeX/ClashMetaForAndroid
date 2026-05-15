package com.github.kr328.clash.design

import android.content.Context
import android.os.Build
import android.view.View
import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.*
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.ProxyHardeningMode
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.launch

class NetworkSettingsDesign(
    context: Context,
    uiStore: UiStore,
    srvStore: ServiceStore,
    running: Boolean,
) : Design<NetworkSettingsDesign.Request>(context) {
    enum class Request {
        Unused
    }

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface
        binding.toolbar.title = (context as? Activity)?.title?.toString().orEmpty()
        binding.toolbar.setNavigationOnClickListener {
            (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
        }

        val screen = preferenceScreen(context) {
            val vpnDependencies: MutableList<Preference> = mutableListOf()

            val vpn = switch(
                value = uiStore::enableVpn,
                icon = R.drawable.ic_baseline_vpn_lock,
                title = R.string.route_system_traffic,
                summary = R.string.routing_via_vpn_service
            ) {
                listener = OnChangedListener {
                    vpnDependencies.forEach {
                        it.enabled = uiStore.enableVpn
                    }
                }
            }

            category(R.string.security_options)

            selectableList(
                value = srvStore::proxyHardeningMode,
                values = ProxyHardeningMode.values(),
                valuesText = arrayOf(
                    R.string.proxy_hardening_strict,
                    R.string.proxy_hardening_compat,
                    R.string.proxy_hardening_off,
                ),
                title = R.string.proxy_hardening_mode,
            )

            switch(
                value = srvStore::seedDefaultGeoMirrors,
                title = R.string.seed_default_geo_mirrors,
                summary = R.string.seed_default_geo_mirrors_summary,
            )

            switch(
                value = srvStore::keepConnectionsOnOldProxy,
                title = R.string.keep_connections_on_old_proxy,
                summary = R.string.keep_connections_on_old_proxy_summary,
            )

            category(R.string.vpn_service_options)

            switch(
                value = srvStore::bypassPrivateNetwork,
                title = R.string.bypass_private_network,
                summary = R.string.bypass_private_network_summary,
                configure = vpnDependencies::add,
            )

            switch(
                value = srvStore::dnsHijacking,
                title = R.string.dns_hijacking,
                summary = R.string.dns_hijacking_summary,
                configure = vpnDependencies::add,
            )

            switch(
                value = srvStore::allowIpv6,
                title = R.string.allow_ipv6,
                summary = R.string.allow_ipv6_summary,
                configure = vpnDependencies::add,
            )

            if (Build.VERSION.SDK_INT >= 29) {
                switch(
                    value = srvStore::systemProxy,
                    title = R.string.system_proxy,
                    summary = R.string.system_proxy_summary,
                    configure = vpnDependencies::add,
                )
            }

            selectableList(
                value = srvStore::tunStackMode,
                values = arrayOf(
                    "system",
                    "gvisor",
                    "mixed"
                ),
                valuesText = arrayOf(
                    R.string.tun_stack_system,
                    R.string.tun_stack_gvisor,
                    R.string.tun_stack_mixed
                ),
                title = R.string.tun_stack_mode,
                configure = vpnDependencies::add,
            )

            if (running) {
                vpn.enabled = false

                vpnDependencies.forEach {
                    it.enabled = false
                }
            } else {
                vpn.listener?.onChanged()
            }
        }

        binding.content.addView(screen.root)

        if (running) {
            launch {
                showToast(R.string.options_unavailable, ToastDuration.Indefinite)
            }
        }
    }
}
