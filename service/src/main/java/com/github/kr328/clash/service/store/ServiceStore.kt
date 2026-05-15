package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import android.os.Build
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.GeoDataSourcePreset
import com.github.kr328.clash.service.model.ProxyHardeningMode
import java.util.*

class ServiceStore(context: Context) {
    private val store = Store(
        PreferenceProvider
            .createSharedPreferencesFromContext(context)
            .asStoreProvider()
    )

    var activeProfile: UUID? by store.typedString(
        key = "active_profile",
        from = { if (it.isBlank()) null else UUID.fromString(it) },
        to = { it?.toString() ?: "" }
    )

    var bypassPrivateNetwork: Boolean by store.boolean(
        key = "bypass_private_network",
        defaultValue = true
    )

    var accessControlMode: AccessControlMode by store.enum(
        key = "access_control_mode",
        defaultValue = AccessControlMode.AcceptAll,
        values = AccessControlMode.values()
    )

    var accessControlPackages by store.stringSet(
        key = "access_control_packages",
        defaultValue = emptySet()
    )

    /**
     * `true` after we have seeded [accessControlPackages] with the default
     * Russian bypass list (банки/госуслуги/связь/маркетплейсы) on first switch
     * to [AccessControlMode.DenySelected]. Prevents repeatedly clobbering the
     * user's manual edits.
     */
    var russianBypassSeeded by store.boolean(
        key = "russian_bypass_seeded",
        defaultValue = false
    )

    var dnsHijacking by store.boolean(
        key = "dns_hijacking",
        defaultValue = true
    )

    var systemProxy by store.boolean(
        key = "system_proxy",
        defaultValue = true
    )

    /** When true, [android.net.VpnService.Builder.allowBypass] is used (apps may exit the VPN). Default off; UI hidden — prefer profile rules. */
    var allowBypass by store.boolean(
        key = "allow_bypass",
        defaultValue = false
    )

    var allowIpv6 by store.boolean(
        key = "allow_ipv6",
        defaultValue = false
    )

    var tunStackMode by store.string(
        key = "tun_stack_mode",
        defaultValue = "system"
    )

    var dynamicNotification by store.boolean(
        key = "dynamic_notification",
        defaultValue = true
    )

    /**
     * Hardening level applied at runtime against SOCKS5/HTTP/Mixed listener
     * leaks and direct access to the TUN interface from other apps. See
     * [ProxyHardeningMode]. Default is [ProxyHardeningMode.Strict] on Android
     * 10+ and [ProxyHardeningMode.Compat] otherwise.
     */
    var proxyHardeningMode: ProxyHardeningMode by store.enum(
        key = "proxy_hardening_mode",
        defaultValue = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ProxyHardeningMode.Strict
        else
            ProxyHardeningMode.Compat,
        values = ProxyHardeningMode.values()
    )

    /**
     * When true, ClashFest seeds default mirrors for `geox-url` if the
     * imported profile does not specify them. Prevents the
     * `cant download geoip.dat` failure on subscriptions that rely on
     * GEOIP/GEOSITE rules without bundling a download URL.
     */
    var seedDefaultGeoMirrors by store.boolean(
        key = "seed_default_geo_mirrors",
        defaultValue = true
    )

    /**
     * When true, manual proxy selector switches keep active connections on the
     * previous proxy (useful for long-running downloads/streams). Default false
     * preserves historical behavior: connections are closed so new traffic
     * uses the freshly selected proxy.
     */
    var keepConnectionsOnOldProxy by store.boolean(
        key = "keep_connections_on_old_proxy",
        defaultValue = false
    )

    var geoDataSourcePreset: GeoDataSourcePreset by store.enum(
        key = "geo_data_source_preset",
        defaultValue = GeoDataSourcePreset.Global,
        values = GeoDataSourcePreset.values()
    )

    var geoDataCustomGeoIp: String by store.string(
        key = "geo_data_custom_geoip",
        defaultValue = ""
    )

    var geoDataCustomGeoSite: String by store.string(
        key = "geo_data_custom_geosite",
        defaultValue = ""
    )

    var geoDataCustomMmdb: String by store.string(
        key = "geo_data_custom_mmdb",
        defaultValue = ""
    )

    var geoDataCustomAsn: String by store.string(
        key = "geo_data_custom_asn",
        defaultValue = ""
    )

    /**
     * When true (operator policy via `share-links` headers), subscription URL/source
     * edits from UI are rejected and share/copy actions are disabled.
     */
    var subscriptionShareLinksLocked: Boolean by store.boolean(
        key = "subscription_share_links_locked",
        defaultValue = false,
    )

    companion object {
        private const val KEY_ALLOW_BYPASS = "allow_bypass"
        private const val MIGRATION_ALLOW_BYPASS_OFF_V1 = "migration_allow_bypass_off_v1"

        /**
         * One-time: force allow-bypass off for upgrades (previously default true).
         * Keeps the preference key for a future dev/advanced toggle.
         */
        fun runMigrations(context: Context) {
            val prefs = PreferenceProvider.createSharedPreferencesFromContext(context)
            if (prefs.getBoolean(MIGRATION_ALLOW_BYPASS_OFF_V1, false)) return
            prefs.edit()
                .putBoolean(KEY_ALLOW_BYPASS, false)
                .putBoolean(MIGRATION_ALLOW_BYPASS_OFF_V1, true)
                .apply()
        }
    }
}
