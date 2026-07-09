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
        // "auto" follows the subscription's tun.stack; explicit values override it. See TunStackResolver.
        defaultValue = "auto"
    )

    var dynamicNotification by store.boolean(
        key = "dynamic_notification",
        defaultValue = true
    )

    /**
     * When true, externally-originated control intents (START/STOP/TOGGLE_CLASH
     * via the exported ExternalControlActivity) are honored. Default true so
     * existing automation (Tasker, launcher shortcuts) keeps working; when the
     * user turns it off, external apps cannot drive the VPN at all. An external
     * stop is always surfaced via a notification regardless of this flag (see
     * SEC-3 / external-control-policy).
     */
    var allowExternalControl by store.boolean(
        key = "allow_external_control",
        // Secure-by-default (H-01): external apps can't drive the VPN unless the user opts in.
        // Existing installs are preserved to ON by runMigrations so automation keeps working.
        defaultValue = false
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

    /**
     * When true (default), a default-network switch (Wi-Fi <-> cellular, Wi-Fi roaming,
     * airplane-mode recovery) closes stale connections and force-runs group health checks so
     * fallback/url-test groups converge immediately instead of waiting out their test interval.
     * See NetworkObserveModule. Off = legacy behavior (DNS refresh only).
     */
    var networkSwitchReaction by store.boolean(
        key = "network_switch_reaction",
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

    /**
     * When true (operator policy via `share-links` headers), subscription URL/source
     * edits from UI are rejected and share/copy actions are disabled.
     */
    private val rawPrefs = PreferenceProvider.createSharedPreferencesFromContext(context)

    /**
     * Per-profile share-links lock. The operator policy travels per
     * subscription (different profiles can have different `share-links`
     * policies), so this can't be a single global flag.
     */
    fun subscriptionShareLinksLockedFor(uuid: UUID): Boolean =
        rawPrefs.getBoolean("subscription_share_links_locked_$uuid", false)

    fun setSubscriptionShareLinksLockedFor(uuid: UUID, value: Boolean) {
        rawPrefs.edit().also { e ->
            if (value) e.putBoolean("subscription_share_links_locked_$uuid", true)
            else e.remove("subscription_share_links_locked_$uuid")
        }.apply()
    }

    fun clearSubscriptionShareLinksLockedFor(uuid: UUID) {
        rawPrefs.edit().remove("subscription_share_links_locked_$uuid").apply()
    }

    /**
     * Per-profile DNS & Hosts master-toggle state. When true the profile's
     * `dns:`/`hosts:` are user-owned: editable in the DNS & Hosts screen and
     * preserved across subscription refreshes. Cleared by the master-toggle
     * teardown.
     */
    fun isDnsHostsManaged(uuid: UUID): Boolean =
        rawPrefs.getBoolean("dns_hosts_managed_$uuid", false)

    fun setDnsHostsManaged(uuid: UUID, managed: Boolean) {
        rawPrefs.edit().putBoolean("dns_hosts_managed_$uuid", managed).apply()
    }

    /** Per-profile: the user manages this profile's `tunnels:` via the editor. */
    fun isTunnelsManaged(uuid: UUID): Boolean =
        rawPrefs.getBoolean("tunnels_managed_$uuid", false)

    fun setTunnelsManaged(uuid: UUID, managed: Boolean) {
        rawPrefs.edit().putBoolean("tunnels_managed_$uuid", managed).apply()
    }

    /**
     * Per-profile one-shot marker: the last subscription update produced a config
     * the engine rejected even though the fetched subscription was valid (i.e. our
     * merge introduced the break). Set by ProfileProcessor, consumed by the UI to
     * show a single warning toast.
     */
    fun setUpdateEngineWarning(uuid: UUID, warned: Boolean) {
        rawPrefs.edit().putBoolean("update_engine_warning_$uuid", warned).apply()
    }

    /** Reads and clears the marker (one-shot). */
    fun consumeUpdateEngineWarning(uuid: UUID): Boolean {
        val warned = rawPrefs.getBoolean("update_engine_warning_$uuid", false)
        if (warned) rawPrefs.edit().remove("update_engine_warning_$uuid").apply()
        return warned
    }

    /**
     * Per-profile one-shot list of rules the last update dropped because their
     * policy (proxy/group) no longer existed in the new subscription. Set by
     * ProfileProcessor, consumed by the UI to warn the user.
     */
    fun setOrphanedRulesDropped(uuid: UUID, rules: List<String>) {
        if (rules.isEmpty()) {
            rawPrefs.edit().remove("orphaned_rules_$uuid").apply()
        } else {
            rawPrefs.edit().putString("orphaned_rules_$uuid", rules.joinToString("\n")).apply()
        }
    }

    /** Reads and clears the dropped-rules marker (one-shot). */
    fun consumeOrphanedRulesDropped(uuid: UUID): List<String> {
        val s = rawPrefs.getString("orphaned_rules_$uuid", null) ?: return emptyList()
        rawPrefs.edit().remove("orphaned_rules_$uuid").apply()
        return s.split("\n").filter { it.isNotBlank() }
    }

    companion object {
        private const val KEY_ALLOW_BYPASS = "allow_bypass"
        private const val KEY_ALLOW_EXTERNAL_CONTROL = "allow_external_control"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val MIGRATION_ALLOW_BYPASS_OFF_V1 = "migration_allow_bypass_off_v1"
        private const val MIGRATION_EXTERNAL_CONTROL_DEFAULT_V1 = "migration_external_control_default_v1"

        /** One-time upgrade migrations. Each is idempotent and guarded by its own flag. */
        fun runMigrations(context: Context) {
            val prefs = PreferenceProvider.createSharedPreferencesFromContext(context)

            // Force allow-bypass off for upgrades (previously default true); keep the key for a
            // future dev/advanced toggle.
            if (!prefs.getBoolean(MIGRATION_ALLOW_BYPASS_OFF_V1, false)) {
                prefs.edit()
                    .putBoolean(KEY_ALLOW_BYPASS, false)
                    .putBoolean(MIGRATION_ALLOW_BYPASS_OFF_V1, true)
                    .apply()
            }

            // H-01: external control now defaults OFF (secure-by-default). Preserve it ON for installs
            // that already have an active profile, so existing automation keeps working; fresh installs
            // stay off. Users can toggle it in App settings either way.
            if (!prefs.getBoolean(MIGRATION_EXTERNAL_CONTROL_DEFAULT_V1, false)) {
                val existingInstall = prefs.getString(KEY_ACTIVE_PROFILE, null) != null
                prefs.edit().apply {
                    if (existingInstall && !prefs.contains(KEY_ALLOW_EXTERNAL_CONTROL)) {
                        putBoolean(KEY_ALLOW_EXTERNAL_CONTROL, true)
                    }
                    putBoolean(MIGRATION_EXTERNAL_CONTROL_DEFAULT_V1, true)
                }.apply()
            }
        }
    }
}
