package com.github.kr328.clash.common.branding

/**
 * Single source of truth for the wire-level header names defined by the
 * ClashFest Operator API. See docs/operator-api/headers.md for the full spec.
 *
 * All header matching is case-insensitive — the names below are just the
 * canonical spellings we document for operators.
 */
object BrandHeaders {

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

    // --- UX simplification (v3) ---
    const val HIDE_STATS = "X-Brand-Hide-Stats"
    const val HIDE_LOGS = "X-Brand-Hide-Logs"
    const val HIDE_ROUTING = "X-Brand-Hide-Routing"

    /**
     * Legacy / pre-Brand spellings we still honour. New code should reach for
     * the canonical X-Brand-* names above; these stay for backwards compatibility
     * with operators on older configurations.
     */
    object Legacy {
        val SUPPORT_URL = listOf(
            "support-url", "Support-URL", "X-Support-URL",
            "support_url", "x-support-url",
            "profile-support-url", "Profile-Support-URL",
            "subscription-support-url", "Subscription-Support-URL",
            "support", "Support",
        )
    }
}
