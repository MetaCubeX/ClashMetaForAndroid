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

    // --- Per-user context (v2 — meaningful only when the panel substitutes
    //                       variables like {{USERNAME}} into the header) ---
    const val USER_DISPLAY_NAME = "X-Brand-User-Display-Name"
    const val GREETING = "X-Brand-Greeting"

    // --- UX simplification (v3) ---
    const val HIDE_STATS = "X-Brand-Hide-Stats"
    const val HIDE_LOGS = "X-Brand-Hide-Logs"
    const val HIDE_ROUTING = "X-Brand-Hide-Routing"

    /**
     * Explicit opt-in for the dedicated Operator tab. Brand identity / accent /
     * logo apply on their own; the Operator tab is an extra surface and the
     * operator chooses whether to enable it.
     */
    const val SHOW_OPERATOR_TAB = "X-Brand-Show-Operator-Tab"
}
