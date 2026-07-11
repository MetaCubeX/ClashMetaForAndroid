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
    fun parseFromConnection(conn: HttpURLConnection): BrandManifest =
        parseHttpHeaders { key -> conn.getHeaderField(key) }

    /**
     * Parse from RAW HTTP header values — i.e. exactly what a transport hands back
     * (`HttpURLConnection.getHeaderField`, OkHttp `Headers[key]`, …), which decode header
     * bytes as ISO-8859-1. This applies [decodeHeaderUtf8] so UTF-8 values (Cyrillic / emoji
     * display names, greetings) are recovered. Every real network fetch MUST go through here
     * rather than raw [parse]; [parse] is the connection-free/decoded entry point used by tests.
     */
    fun parseHttpHeaders(headerLookup: (String) -> String?): BrandManifest =
        parse { key -> headerLookup(key)?.let(::decodeHeaderUtf8) }

    /**
     * `HttpURLConnection.getHeaderField` decodes header bytes as ISO-8859-1 (per the HTTP spec's
     * historical default), so a panel that sends a UTF-8 value — e.g. a Cyrillic / emoji
     * `X-Brand-User-Display-Name: {{USERNAME}}` — arrives mojibake'd (each UTF-8 byte read as a
     * Latin-1 char). Re-encode to the original bytes and decode as UTF-8 to recover it. ASCII values
     * (URLs, hex colours, booleans) round-trip unchanged, so this is safe to apply to every header.
     */
    internal fun decodeHeaderUtf8(value: String): String =
        String(value.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)

    /**
     * Generic parser keyed on a lookup function — keeps the parser unit-testable
     * without an actual HttpURLConnection.
     */
    fun parse(headerLookup: (String) -> String?): BrandManifest {
        fun raw(key: String): String? = headerLookup(key)?.trim()?.trim('"', '\'')

        val enabled = BrandValidation.parseBoolean(raw(BrandHeaders.BRANDING_ENABLED))
        val hideGlobalMode = BrandValidation.parseBoolean(raw(BrandHeaders.HIDE_GLOBAL_MODE))
        if (enabled != true) {
            return BrandManifest(
                hideGlobalMode = hideGlobalMode,
                enabled = enabled,
            )
        }

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
            hideGlobalMode = hideGlobalMode,
            showOperatorTab = BrandValidation.parseBoolean(raw(BrandHeaders.SHOW_OPERATOR_TAB)),
            enabled = enabled,
        )
    }
}
