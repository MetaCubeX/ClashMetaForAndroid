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
import com.github.kr328.clash.service.util.TunStackResolver
import com.github.kr328.clash.service.util.importedDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        binding.header.screenTitle.text = (context as? Activity)?.title?.toString().orEmpty()

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

            val stackPref = selectableList(
                value = srvStore::tunStackMode,
                values = arrayOf(
                    "auto",
                    "system",
                    "gvisor",
                    "mixed"
                ),
                valuesText = arrayOf(
                    R.string.tun_stack_auto,
                    R.string.tun_stack_system,
                    R.string.tun_stack_gvisor,
                    R.string.tun_stack_mixed
                ),
                title = R.string.tun_stack_mode,
                configure = vpnDependencies::add,
            )
            // While the VPN is running on Auto, show the stack actually in use (Auto: gvisor/system/
            // mixed) resolved from the active subscription — so the effective stack isn't opaque.
            if (running) {
                launch {
                    val resolved = withContext(Dispatchers.IO) {
                        if (srvStore.tunStackMode != TunStackResolver.AUTO) return@withContext null
                        val uuid = srvStore.activeProfile ?: return@withContext null
                        val cfg = runCatching {
                            File(context.importedDir.resolve(uuid.toString()), "config.yaml").readText()
                        }.getOrNull()
                        TunStackResolver.resolve(cfg, TunStackResolver.AUTO)
                    }
                    if (resolved != null) {
                        stackPref.summary = context.getString(R.string.tun_stack_auto_fmt, resolved)
                    }
                }
            }

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
            // The "options unavailable" snackbar is pinned to the bottom and would otherwise cover
            // the last preference (the stack row). Pad the scroll content so that row can scroll
            // clear of it.
            val extra = (88 * context.resources.displayMetrics.density).toInt()
            binding.content.setPadding(0, 0, 0, extra)
            launch {
                showToast(R.string.options_unavailable, ToastDuration.Indefinite)
            }
        }
    }
}
