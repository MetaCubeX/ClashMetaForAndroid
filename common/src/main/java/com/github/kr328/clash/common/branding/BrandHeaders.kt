package com.github.kr328.clash.common.branding

/**
 * Single source of truth for the wire-level header names defined by the
 * ClashFest Operator API. See docs/operator-api/headers.md for the full spec.
 *
 * All header matching is case-insensitive — the names below are just the
 * canonical spellings we document for operators.
 */
object BrandHeaders {

    /**
     * Master kill switch. When the operator sends `X-Branding-Enabled: false`,
     * every other X-Brand-* header is ignored and the client reverts to the
     * default ClashFest UI. Lets an operator roll back branding instantly if
     * a deployed configuration introduces a regression, without having to
     * remove all of their branding headers one-by-one.
     */
    const val BRANDING_ENABLED = "X-Branding-Enabled"

    // --- Brand identity (v1) ---
    const val NAME = "X-Brand-Name"
    const val TAGLINE = "X-Brand-Tagline"
    const val LOGO_URL = "X-Brand-Logo-URL"
    const val LOGO_LIGHT_URL = "X-Brand-Logo-Light-URL"
    const val ACCENT_COLOR = "X-Brand-Accent-Color"

    // --- Operator info (v1) ---
    const val WEBSITE_URL = "X-Brand-Website-URL"
    const val SUPPORT_URL = "X-Brand-Support-URL"
    const val TELEGRAM_URL = "X-Brand-Telegram-URL"
    const val BOT_URL = "X-Brand-Bot-URL"
    const val PRIVACY_URL = "X-Brand-Privacy-URL"
    const val TERMS_URL = "X-Brand-Terms-URL"
    const val HELP_URL = "X-Brand-Help-URL"

    // --- Operator info (v2) ---
    const val STATUS_URL = "X-Brand-Status-URL"
    const val RENEW_URL = "X-Brand-Renew-URL"

    /**
     * Personal account / billing entry-point for the current user of this
     * subscription. Built by the operator with a panel template variable
     * (e.g. `https://t.me/<bot>?startapp={{SHORT_UUID}}` for a Telegram
     * Mini App, or `https://billing.example.com/account?ref={{ID}}` for a
     * web cabinet). ClashFest opens the URL via `ACTION_VIEW` — `tg://` and
     * `https://t.me/...` route to Telegram, plain `https://` to a browser.
     */
    const val CABINET_URL = "X-Brand-Cabinet-URL"

    // --- Per-user context (v2 — meaningful only when the panel substitutes
    //                       variables like {{USERNAME}} into the header) ---
    const val USER_DISPLAY_NAME = "X-Brand-User-Display-Name"
    const val GREETING = "X-Brand-Greeting"

    // --- UX simplification (v3) ---
    // Gated by X-Branding-Enabled + the Operator tab (see MainDesign.reconcileTabsForBrand).
    const val HIDE_ROUTING = "X-Brand-Hide-Routing"

    // --- Operator policy (v4) — applies WITHOUT X-Branding-Enabled ---
    /**
     * Operator restriction, NOT cosmetic branding: hides the Home Rule/Global mode toggle and pins
     * the app to Rule mode. Because it's a policy (the operator restricting behaviour, e.g. stopping
     * users from routing all traffic through the proxy and bypassing rules) rather than a look, it
     * takes effect on header presence alone — it does NOT require `X-Branding-Enabled: true`.
     * This is the one header that works unbranded; every X-Brand-* identity/tab/info field still
     * needs branding explicitly enabled.
     */
    const val HIDE_GLOBAL_MODE = "X-Brand-Hide-Global-Mode"

    /**
     * Explicit opt-in for the dedicated Operator tab. Brand identity / accent /
     * logo apply on their own; the Operator tab is an extra surface and the
     * operator chooses whether to enable it.
     */
    const val SHOW_OPERATOR_TAB = "X-Brand-Show-Operator-Tab"
}
