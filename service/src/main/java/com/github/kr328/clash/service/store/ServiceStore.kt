package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.GeoDataSourcePreset
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