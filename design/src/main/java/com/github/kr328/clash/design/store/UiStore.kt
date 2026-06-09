package com.github.kr328.clash.design.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.AppLanguage
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.model.HomeBackgroundStyle
import com.github.kr328.clash.design.model.ProfileSortMode
import com.github.kr328.clash.design.model.ThemePalette
import com.github.kr328.clash.design.model.ThemeTextScale

class UiStore(context: Context) {
    private val store = Store(
        context
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .asStoreProvider()
    )

    var enableVpn: Boolean by store.boolean(
        key = "enable_vpn",
        defaultValue = true
    )

    var darkMode: DarkMode by store.enum(
        key = "dark_mode",
        defaultValue = DarkMode.Auto,
        values = DarkMode.values()
    )

    var dynamicColors: Boolean by store.boolean(
        key = "dynamic_colors",
        defaultValue = true,
    )

    var themePalette: ThemePalette by store.enum(
        key = "theme_palette",
        defaultValue = ThemePalette.Clash,
        values = ThemePalette.values(),
    )

    var trueBlack: Boolean by store.boolean(
        key = "true_black",
        defaultValue = false,
    )

    var themeTextScale: ThemeTextScale by store.enum(
        key = "theme_text_scale",
        defaultValue = ThemeTextScale.Default,
        values = ThemeTextScale.values(),
    )

    /** User-selected app language; `System` follows the device locale. */
    var appLanguage: AppLanguage by store.enum(
        key = "app_language",
        defaultValue = AppLanguage.System,
        values = AppLanguage.values()
    )

    var homeBackgroundStyle: HomeBackgroundStyle by store.enum(
        key = "home_background_style",
        defaultValue = HomeBackgroundStyle.Preview,
        values = HomeBackgroundStyle.values(),
    )

    var hideAppIcon: Boolean by store.boolean(
        key = "hide_app_icon",
        defaultValue = false
    )

    var hideFromRecents: Boolean by store.boolean(
        key = "hide_from_recents",
        defaultValue = false,
    )

    /** Experimental gate for the per-profile DNS & Hosts editor (OFF by default). */
    var dnsHostsEnabled: Boolean by store.boolean(
        key = "dns_hosts_enabled",
        defaultValue = false,
    )

    /** Experimental gate for the per-profile Tunnels editor (OFF by default). */
    var tunnelsEnabled: Boolean by store.boolean(
        key = "tunnels_enabled",
        defaultValue = false,
    )

    var proxyExcludeNotSelectable by store.boolean(
        key = "proxy_exclude_not_selectable",
        defaultValue = false,
    )

    var proxyLine: Int by store.int(
        key = "proxy_line",
        defaultValue = 2
    )

    var proxySort: ProxySort by store.enum(
        key = "proxy_sort",
        defaultValue = ProxySort.Default,
        values = ProxySort.values()
    )

    var proxyLastGroup: String by store.string(
        key = "proxy_last_group",
        defaultValue = ""
    )

    var profileSortMode: ProfileSortMode by store.enum(
        key = "profile_sort_mode",
        defaultValue = ProfileSortMode.Manual,
        values = ProfileSortMode.values(),
    )

    /** Persisted Rule / Global / Direct choice; survives VPN stop/start. Empty = use profile/runtime default. */
    var tunnelModePreference: String by store.string(
        key = "tunnel_mode_preference",
        defaultValue = ""
    )

    var accessControlSort: AppInfoSort by store.enum(
        key = "access_control_sort",
        defaultValue = AppInfoSort.Label,
        values = AppInfoSort.values(),
    )

    var accessControlReverse: Boolean by store.boolean(
        key = "access_control_reverse",
        defaultValue = false
    )

    var accessControlSystemApp: Boolean by store.boolean(
        key = "access_control_system_app",
        defaultValue = false,
    )

    /** True once the one-time RU bypass prompt has been shown and explicitly answered. */
    var ruBypassPromptHandled: Boolean by store.boolean(
        key = "ru_bypass_prompt_handled",
        defaultValue = false,
    )

    /** Optional support contact (e.g. https://t.me/your_bot). Shown in About + Settings. */
    var supportUrl: String by store.string(
        key = "support_url",
        defaultValue = "",
    )

    /** Short message shown as a card on the main screen when non-empty. */
    var announcement: String by store.string(
        key = "announcement",
        defaultValue = "",
    )

    /** Optional URL the announcement card links to. */
    var announcementUrl: String by store.string(
        key = "announcement_url",
        defaultValue = "",
    )

    /**
     * Last announcement hash the user has already opened (per subscription).
     * The key is `announcement_read_hash_<profile-uuid>`; the value is a stable
     * digest of the announcement payload from that subscription. When the
     * stored hash differs from the current announcement, the banner shows a
     * `New` indicator until the user opens the sheet.
     */
    fun announcementReadHashFor(uuid: java.util.UUID): String =
        rawAnnouncementPrefs.getString(announcementReadKey(uuid), "").orEmpty()

    fun setAnnouncementReadHashFor(uuid: java.util.UUID, hash: String) {
        rawAnnouncementPrefs.edit().also { e ->
            if (hash.isBlank()) e.remove(announcementReadKey(uuid))
            else e.putString(announcementReadKey(uuid), hash)
        }.apply()
    }

    private val rawAnnouncementPrefs =
        context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    private fun announcementReadKey(uuid: java.util.UUID) = "announcement_read_hash_$uuid"

    /**
     * JSON object: profile UUID string → { a, au, s, u } (announcement, announcement URL,
     * support URL, subscription-userinfo) from the last successful metadata fetch for that profile.
     */
    var subscriptionMetaCacheJson: String by store.string(
        key = "sub_meta_profile_cache_v1",
        defaultValue = "",
    )

    /** When `true`, suppress operator-pushed values from overwriting user-edited fields. */
    var subscriptionMetadataLockUser: Boolean by store.boolean(
        key = "sub_meta_lock_user",
        defaultValue = false,
    )

    /**
     * When false (default), subscription metadata HTTP probes use HTTPS only.
     * Opt-in allows probing `http://` subscription URLs (cleartext risk).
     */
    var subscriptionMetadataAllowInsecureHttp: Boolean by store.boolean(
        key = "sub_meta_allow_http",
        defaultValue = false,
    )

    /** UNIX seconds of last successful subscription metadata fetch; 0 = never. */
    var subscriptionMetadataLastFetch: Long by store.long(
        key = "sub_meta_last_fetch",
        defaultValue = 0L,
    )

    /**
     * Profile UUID this [subscriptionMetadataLastFetch] cooldown was last tied to.
     * When the active profile differs, cooldown is ignored so a new subscription still gets headers.
     */
    var subscriptionMetadataLastFetchProfileId: String by store.string(
        key = "sub_meta_last_fetch_profile",
        defaultValue = "",
    )

    /** Cached `subscription-userinfo` header (used/total/expiry) of active profile. */
    var subscriptionUserinfo: String by store.string(
        key = "sub_userinfo",
        defaultValue = "",
    )

    /** Cached Remnawave diagnostics headers (`true`/`false`/empty when unknown). */
    var subscriptionHwidActive: String by store.string(
        key = "sub_hwid_active",
        defaultValue = "",
    )
    var subscriptionHwidNotSupported: String by store.string(
        key = "sub_hwid_not_supported",
        defaultValue = "",
    )
    var subscriptionHwidMaxDevicesReached: String by store.string(
        key = "sub_hwid_max_devices_reached",
        defaultValue = "",
    )
    var subscriptionHwidLimit: String by store.string(
        key = "sub_hwid_limit",
        defaultValue = "",
    )

    /** Operator-published "open this URL" companion (e.g. dashboard). */
    var profileWebPageUrl: String by store.string(
        key = "profile_web_page_url",
        defaultValue = "",
    )

    /** Operator-recommended update interval, in hours. 0 = unset. */
    var profileUpdateIntervalHours: Int by store.int(
        key = "profile_update_interval_hours",
        defaultValue = 0,
    )

    companion object {
        private const val PREFERENCE_NAME = "ui"
    }
}
