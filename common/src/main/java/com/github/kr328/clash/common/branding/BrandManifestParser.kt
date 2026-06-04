package com.github.kr328.clash.common.branding

import android.content.Context
import com.github.kr328.clash.common.util.SubscriptionHttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Parses [BrandManifest] from HTTP response headers of a subscription URL.
 *
 * The fetcher is intentionally separate from [com.github.kr328.clash.common.util.SubscriptionMetadataFetcher]
 * — they read overlapping but different concerns:
 *
 * - SubscriptionMetadataFetcher: per-profile metadata (title, quota, hwid).
 * - BrandManifestParser:         operator-wide branding (name, logo, accent,
 *                                 operator URLs, UX flags).
 *
 * Both can be issued against the same subscription URL in a single fetch
 * (see [parseFromConnection]) to avoid double-roundtripping the network.
 */
object BrandManifestParser {

    /**
     * Single-request fetch. Use this when no other consumer needs the response.
     * For shared fetches, prefer wiring [parseFromConnection] into the existing
     * connection in [com.github.kr328.clash.common.util.SubscriptionMetadataFetcher].
     */
    suspend fun fetch(
        context: Context,
        urlString: String,
        userAgentOverride: String? = null,
        allowHttpMetadata: Boolean = false,
    ): BrandManifest = withContext(Dispatchers.IO) {
        val request = urlString.substringBefore('#')
        if (request.startsWith("http://", ignoreCase = true) && !allowHttpMetadata) {
            return@withContext BrandManifest()
        }
        try {
            val url = URL(request)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                SubscriptionHttpHeaders.applyTo(this, context, userAgentOverride)
            }
            try {
                conn.connect()
                if (conn.responseCode !in 200..299) return@withContext BrandManifest()
                parseFromConnection(conn)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            BrandManifest()
        }
    }

    /**
     * Reads brand headers off an already-connected HttpURLConnection.
     * Caller is responsible for connecting and checking response code.
     */
    fun parseFromConnection(conn: HttpURLConnection): BrandManifest = parse { key ->
        conn.getHeaderField(key)
    }

    /**
     * Generic parser keyed on a lookup function — keeps the parser unit-testable
     * without an actual HttpURLConnection.
     */
    fun parse(headerLookup: (String) -> String?): BrandManifest {
        fun raw(key: String): String? = headerLookup(key)?.trim()?.trim('"', '\'')

        // X-Brand-* namespace ONLY. Legacy `support-url` / `Profile-Support-URL`
        // are handled by SubscriptionMetadataFetcher in the common namespace
        // and surface through the existing announcement-card "Support" action —
        // we don't dual-claim them here, otherwise a profile that ships only
        // a legacy support URL would falsely look "branded".
        return BrandManifest(
            name = BrandValidation.cleanName(raw(BrandHeaders.NAME)),
            tagline = BrandValidation.cleanTagline(raw(BrandHeaders.TAGLINE)),
            logoUrl = BrandValidation.cleanLogoUrl(raw(BrandHeaders.LOGO_URL)),
            logoLightUrl = BrandValidation.cleanLogoUrl(raw(BrandHeaders.LOGO_LIGHT_URL)),
            accentColor = BrandValidation.cleanHexColor(raw(BrandHeaders.ACCENT_COLOR)),
            websiteUrl = BrandValidation.cleanBrandUrl(raw(BrandHeaders.WEBSITE_URL)),
            supportUrl = BrandValidation.cleanBrandUrl(raw(BrandHeaders.SUPPORT_URL)),
            telegramUrl = BrandValidation.cleanBrandUrl(raw(BrandHeaders.TELEGRAM_URL)),
            botUrl = BrandValidation.cleanBrandUrl(raw(BrandHeaders.BOT_URL)),
            privacyUrl = BrandValidation.cleanBrandUrl(raw(BrandHeaders.PRIVACY_URL)),
            termsUrl = BrandValidation.cleanBrandUrl(raw(BrandHeaders.TERMS_URL)),
            helpUrl = BrandValidation.cleanBrandUrl(raw(BrandHeaders.HELP_URL)),
            statusUrl = BrandValidation.cleanBrandUrl(raw(BrandHeaders.STATUS_URL)),
            renewUrl = BrandValidation.cleanBrandUrl(raw(BrandHeaders.RENEW_URL)),
            cabinetUrl = BrandValidation.cleanBrandUrl(raw(BrandHeaders.CABINET_URL)),
            userDisplayName = BrandValidation.cleanString(
                raw(BrandHeaders.USER_DISPLAY_NAME),
                BrandValidation.USER_DISPLAY_NAME_MAX_LENGTH,
            ),
            greeting = BrandValidation.cleanString(
                raw(BrandHeaders.GREETING),
                BrandValidation.GREETING_MAX_LENGTH,
            ),
            hideRouting = BrandValidation.parseBoolean(raw(BrandHeaders.HIDE_ROUTING)),
            showOperatorTab = BrandValidation.parseBoolean(raw(BrandHeaders.SHOW_OPERATOR_TAB)),
            enabled = BrandValidation.parseBoolean(raw(BrandHeaders.BRANDING_ENABLED)),
        )
    }
}
