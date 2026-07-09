package com.github.kr328.clash.common.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Operator-side metadata advertised through subscription HTTP response headers.
 * Inspired by pasarguard/panel — see https://github.com/pasarguard/panel docs.
 *
 * All fields are best-effort; missing headers map to null. Header keys are
 * matched case-insensitively. We probe the most common spellings used across
 * V2Ray / Clash compatible panels.
 */
data class SubscriptionMetadata(
    val supportUrl: String? = null,
    val profileTitle: String? = null,
    val profileWebPageUrl: String? = null,
    /** Recommended client-side update interval, in hours. */
    val profileUpdateIntervalHours: Int? = null,
    val announcement: String? = null,
    val announcementUrl: String? = null,
    /** Raw `subscription-userinfo: upload=…; download=…; total=…; expire=…` line. */
    val subscriptionUserinfo: String? = null,
    /**
     * When true, operator asks the client to disable sharing subscription links and lock sensitive edits.
     * Parsed from `share-links` / `share_links` style headers.
     */
    val shareLinksDisable: Boolean? = null,
    val hwidActive: Boolean? = null,
    val hwidNotSupported: Boolean? = null,
    val hwidMaxDevicesReached: Boolean? = null,
    val hwidLimit: Boolean? = null,
) {
    fun isEmpty(): Boolean =
        supportUrl == null &&
            profileTitle == null &&
            profileWebPageUrl == null &&
            profileUpdateIntervalHours == null &&
            announcement == null &&
            announcementUrl == null &&
            subscriptionUserinfo == null &&
            shareLinksDisable == null &&
            hwidActive == null &&
            hwidNotSupported == null &&
            hwidMaxDevicesReached == null &&
            hwidLimit == null
}

object SubscriptionMetadataFetcher {

    /**
     * @param allowHttpMetadata When false (default), skips metadata probe for `http://` subscription URLs unless user opted in.
     */
    suspend fun fetch(
        context: Context,
        urlString: String,
        userAgentOverride: String? = null,
        allowHttpMetadata: Boolean = false,
    ): SubscriptionMetadata =
        withContext(Dispatchers.IO) {
            val request = urlString.substringBefore('#')
            if (request.startsWith("http://", ignoreCase = true) && !allowHttpMetadata) {
                return@withContext SubscriptionMetadata()
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
                    if (conn.responseCode !in 200..299) return@withContext SubscriptionMetadata()
                    parse(conn)
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                SubscriptionMetadata()
            }
        }

    /**
     * Single GET that yields the metadata AND the operator brand manifest from
     * the same response. The screen-on metadata sync used to issue two
     * identical requests back-to-back (one per parser) — panels count fetches
     * against quota, so every avoidable GET matters.
     */
    suspend fun fetchWithBrand(
        context: Context,
        urlString: String,
        userAgentOverride: String? = null,
        allowHttpMetadata: Boolean = false,
    ): Pair<SubscriptionMetadata, com.github.kr328.clash.common.branding.BrandManifest> =
        withContext(Dispatchers.IO) {
            val empty = SubscriptionMetadata() to com.github.kr328.clash.common.branding.BrandManifest()
            val request = urlString.substringBefore('#')
            if (request.startsWith("http://", ignoreCase = true) && !allowHttpMetadata) {
                return@withContext empty
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
                    if (conn.responseCode !in 200..299) return@withContext empty
                    parse(conn) to
                        com.github.kr328.clash.common.branding.BrandManifestParser.parseFromConnection(conn)
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                empty
            }
        }

    private fun parse(conn: HttpURLConnection): SubscriptionMetadata =
        parseHeaders { key -> conn.getHeaderField(key) }

    /**
     * Parses metadata from any header source — a live HttpURLConnection or the
     * per-profile `fetch-headers.json` snapshot the Go fetch persists (values
     * there are already correct UTF-8; this parser applies no transport
     * charset recovery, only trimming and the base64 convention panels use
     * for non-ASCII announce/title values).
     */
    fun parseHeaders(headerLookup: (String) -> String?): SubscriptionMetadata {
        fun header(vararg keys: String): String? {
            for (k in keys) {
                val v = headerLookup(k)?.trim()?.trim('"', '\'')
                if (!v.isNullOrBlank()) return v
            }
            return null
        }

        val supportRaw = header(
            "support-url", "Support-URL", "X-Support-URL",
            "support_url", "x-support-url",
            "profile-support-url", "Profile-Support-URL",
            "subscription-support-url", "Subscription-Support-URL",
            "support", "Support",
        )
        val title = header(
            "profile-title", "Profile-Title", "Subscription-Title",
            "X-Subscription-Title", "Display-Name",
        )?.let { decodeMaybeBase64(it) }
        val pageUrl = header(
            "profile-web-page-url", "Profile-Web-Page-URL",
            "Subscription-Web-Page", "X-Subscription-Web-Page",
        )
        val intervalRaw = header(
            "profile-update-interval", "Profile-Update-Interval",
            "Update-Interval", "X-Profile-Update-Interval",
        )
        val announce = header(
            "announce", "Announce", "Announcement",
            "X-Announce", "X-Announcement",
        )?.let { decodeMaybeBase64(it) }
        val announceUrl = header(
            "announce-url", "Announce-URL", "Announcement-URL",
            "X-Announce-URL", "X-Announcement-URL",
        )
        val userinfo = header("Subscription-Userinfo", "subscription-userinfo")

        val shareLinksRaw = header(
            "share-links",
            "Share-Links",
            "share_links",
            "Share_Links",
            "X-Share-Links",
            "X-Share-Links-Policy",
        )
        val shareLinksDisable = parseShareLinksPolicy(shareLinksRaw)
        val hwidActive = parseBooleanHeader(header("x-hwid-active", "X-HWID-ACTIVE"))
        val hwidNotSupported = parseBooleanHeader(header("x-hwid-not-supported", "X-HWID-NOT-SUPPORTED"))
        val hwidMaxDevicesReached = parseBooleanHeader(
            header("x-hwid-max-devices-reached", "X-HWID-MAX-DEVICES-REACHED"),
        )
        val hwidLimit = parseBooleanHeader(header("x-hwid-limit", "X-HWID-LIMIT"))

        return SubscriptionMetadata(
            supportUrl = supportRaw?.let(::normalizeUrl)?.takeIf { looksLikeUrl(it) },
            profileTitle = title?.takeIf { it.isNotBlank() },
            profileWebPageUrl = pageUrl?.takeIf { looksLikeUrl(it) },
            profileUpdateIntervalHours = intervalRaw?.toIntOrNull()?.takeIf { it in 1..720 },
            announcement = announce?.takeIf { it.isNotBlank() },
            announcementUrl = announceUrl?.takeIf { looksLikeUrl(it) },
            subscriptionUserinfo = userinfo,
            shareLinksDisable = shareLinksDisable,
            hwidActive = hwidActive,
            hwidNotSupported = hwidNotSupported,
            hwidMaxDevicesReached = hwidMaxDevicesReached,
            hwidLimit = hwidLimit,
        )
    }

    private fun parseShareLinksPolicy(raw: String?): Boolean? {
        if (raw.isNullOrBlank()) return null
        return when (raw.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
    }

    private fun parseBooleanHeader(raw: String?): Boolean? {
        if (raw.isNullOrBlank()) return null
        return when (raw.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
    }

    private fun looksLikeUrl(value: String): Boolean {
        val v = value.trim()
        return v.startsWith("http://", true) ||
            v.startsWith("https://", true) ||
            v.startsWith("tg://", true) ||
            v.startsWith("mailto:", true) ||
            v.startsWith("t.me/", true)
    }

    private fun normalizeUrl(value: String): String {
        val v = value.trim()
        return if (v.startsWith("t.me/", true)) "https://$v" else v
    }

    private fun decodeMaybeBase64(raw: String): String = MaybeBase64.decode(raw)
}

/** Public helper so UI layers can lazily decode legacy announcement strings stored before fix. */
object MaybeBase64 {
    fun decode(raw: String): String {
        var s = raw.trim()
        val prefixed = s.startsWith("base64:", ignoreCase = true)
        if (prefixed) s = s.removePrefix("base64:").removePrefix("BASE64:").trim()
        if (s.length < 8) return if (prefixed) s else raw.trim()
        if (!s.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '_' || it == '-' }) {
            return if (prefixed) s else raw.trim()
        }
        return try {
            val normalized = s.replace('-', '+').replace('_', '/')
            val decoded = String(
                android.util.Base64.decode(normalized, android.util.Base64.DEFAULT),
                Charsets.UTF_8,
            ).trim()
            if (decoded.isBlank() || decoded.any { it.code in 0..8 || it.code in 14..31 }) {
                if (prefixed) s else raw.trim()
            } else {
                decoded
            }
        } catch (_: Exception) {
            if (prefixed) s else raw.trim()
        }
    }
}

/**
 * Parses `upload=N; download=N; total=N; expire=UNIX` from the `subscription-userinfo`
 * header. All fields optional.
 */
data class SubscriptionUsage(
    val upload: Long?,
    val download: Long?,
    val total: Long?,
    /** Unix epoch seconds, 0/null = never expires. */
    val expireAt: Long?,
) {
    val used: Long? get() = if (upload != null || download != null) (upload ?: 0L) + (download ?: 0L) else null

    companion object {
        fun parse(header: String?): SubscriptionUsage? {
            if (header.isNullOrBlank()) return null
            val map = HashMap<String, String>()
            for (part in header.split(';')) {
                val p = part.trim()
                val eq = p.indexOf('=')
                if (eq <= 0) continue
                map[p.substring(0, eq).trim().lowercase()] = p.substring(eq + 1).trim()
            }
            if (map.isEmpty()) return null
            return SubscriptionUsage(
                upload = map["upload"]?.toLongOrNull(),
                download = map["download"]?.toLongOrNull(),
                total = map["total"]?.toLongOrNull(),
                expireAt = map["expire"]?.toLongOrNull()?.takeIf { it > 0 },
            )
        }
    }
}
