package com.github.kr328.clash.design.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.AppLanguage
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.model.HomeBackgroundStyle
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

    /** Persisted Rule / Global / Direct choice; survives VPN stop/start. Empty = uninitialized. */
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

    /** When true, hide the announcement card on the main screen until text changes. */
    var announcementDismissed: Boolean by store.boolean(
        key = "announcement_dismissed",
        defaultValue = false,
    )

    /** Hash of last seen announcement; reset of [announcementDismissed] when text changes. */
    var announcementSeenHash: String by store.string(
        key = "announcement_seen_hash",
        defaultValue = "",
    )

    /** When `true`, suppress operator-pushed values from overwriting user-edited fields. */
    var subscriptionMetadataLockUser: Boolean by store.boolean(
        key = "sub_meta_lock_user",
        defaultValue = false,
    )

    /** UNIX seconds of last successful subscription metadata fetch; 0 = never. */
    var subscriptionMetadataLastFetch: Long by store.long(
        key = "sub_meta_last_fetch",
        defaultValue = 0L,
    )

    /** Cached `subscription-userinfo` header (used/total/expiry) of active profile. */
    var subscriptionUserinfo: String by store.string(
        key = "sub_userinfo",
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
