package com.github.kr328.clash.common.util

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlin.text.Charsets

/**
 * Best-effort title for a remote subscription URL (Clash / similar).
 * Uses response headers (incl. subscription-userinfo) and structured fields in the body.
 */
object SubscriptionNameGuesser {
    fun guessFast(urlString: String): String {
        val trimmed = urlString.trim()
        if (isMierusLinkForSubscriptionTitle(trimmed)) {
            parseFragmentName(trimmed)?.let { return sanitizeName(it) }
            return "mieru"
        }
        parseFragmentName(trimmed)?.let { return sanitizeName(it) }
        return fallbackName(stripUrlFragment(trimmed))
    }

    suspend fun guess(context: Context, urlString: String): String =
        withContext(Dispatchers.IO) {
            if (isMierusLinkForSubscriptionTitle(urlString.trim())) {
                parseFragmentName(urlString)?.let { return@withContext sanitizeName(it) }
                return@withContext "mieru"
            }
            parseFragmentName(urlString)?.let { return@withContext sanitizeName(it) }
            val requestUrl = stripUrlFragment(urlString)
            try {
                val url = URL(requestUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20_000
                    readTimeout = 20_000
                    instanceFollowRedirects = true
                    val ver = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            ?: "0"
                    } catch (_: Exception) {
                        "0"
                    }
                    setRequestProperty("User-Agent", "ClashFest/$ver")
                    SubscriptionHttpHeaders.applyTo(this, context)
                }
                try {
                    conn.connect()
                    if (conn.responseCode !in 200..299) {
                        return@withContext fallbackName(requestUrl)
                    }
                    // Prefer decoded title headers over Content-Disposition filename (often account id, e.g. BRIDGE).
                    listOf(
                        "Subscription-Title",
                        "Profile-Title",
                        "X-Subscription-Title",
                        "Display-Name",
                        "X-Display-Name",
                        "Subscription-Display-Name",
                    ).forEach { key ->
                        conn.getHeaderField(key)?.let(::normalizeHeaderValue)?.takeIf { it.isNotBlank() }?.let {
                            decodeMaybeEncodedName(it)?.let { decoded ->
                                return@withContext sanitizeName(decoded)
                            }
                            return@withContext sanitizeName(it)
                        }
                    }

                    conn.getHeaderField("Content-Disposition")?.let(::parseFilenameFromContentDisposition)
                        ?.let(::sanitizeName)?.takeIf { it.isNotBlank() }?.let { return@withContext it }

                    conn.getHeaderField("Subscription-Userinfo")?.let(::parseSubscriptionUserinfo)
                        ?.let(::sanitizeName)?.takeIf { it.isNotBlank() }?.let { return@withContext it }

                    val raw = conn.inputStream.use { it.readBytes() }
                    val max = 256 * 1024
                    val body = if (raw.size <= max) raw else raw.copyOfRange(0, max)
                    val text = decodeResponseText(body)
                    parseNameFromSubscriptionBody(text)?.let(::sanitizeName)?.takeIf { it.isNotBlank() }
                        ?.let { return@withContext it }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                // fall through
            }
            fallbackName(requestUrl)
        }

    /**
     * Resolve a subscription display title from ALREADY-FETCHED response headers, using the SAME
     * decoding chain as [guess] (explicit title headers → `Content-Disposition` filename* (RFC-5987)
     * → `Subscription-Userinfo`), so the update path (`ProfileManager.updateFlow`) gets identical
     * quality to import without a second network round-trip. Returns null when nothing usable. (E-19)
     */
    fun titleFromHeaders(get: (String) -> String?): String? {
        listOf(
            "Subscription-Title",
            "Profile-Title",
            "X-Subscription-Title",
            "Display-Name",
            "X-Display-Name",
            "Subscription-Display-Name",
        ).forEach { key ->
            val raw = get(key)?.let(::normalizeHeaderValue)?.takeIf { it.isNotBlank() } ?: return@forEach
            val name = sanitizeName(decodeMaybeEncodedName(raw) ?: raw)
            if (!name.isNullOrBlank()) return name
        }
        get("Content-Disposition")?.let(::parseFilenameFromContentDisposition)
            ?.let(::sanitizeName)?.takeIf { it.isNotBlank() }?.let { return it }
        get("Subscription-Userinfo")?.let(::parseSubscriptionUserinfo)
            ?.let(::sanitizeName)?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    /** First non-empty line starts with `mierus://` (QR / clipboard mierus share). */
    private fun isMierusLinkForSubscriptionTitle(trimmed: String): Boolean {
        val first = trimmed.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: return false
        return first.startsWith("mierus://", ignoreCase = true)
    }

    private fun stripUrlFragment(urlString: String): String =
        urlString.substringBefore('#')

    /**
     * `#name=Title` or `#Title` (single segment) in subscription links.
     */
    private fun parseFragmentName(urlString: String): String? {
        val frag = urlString.substringAfter('#', "").trim()
        if (frag.isEmpty()) return null
        if (!frag.contains('=')) {
            if (frag.length in 2..72 && !frag.contains("://")) {
                // Decode percent-escapes so `#Home%20VPN` becomes "Home VPN".
                // Only when a '%' is present, to avoid URLDecoder turning a
                // literal '+' into a space.
                return if (frag.contains('%')) {
                    try {
                        URLDecoder.decode(frag, Charsets.UTF_8.name())
                    } catch (_: Exception) {
                        frag
                    }
                } else {
                    frag
                }
            }
            return null
        }
        for (part in frag.split('&')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val key = part.substring(0, eq).trim()
            if (!key.equals("name", ignoreCase = true)) continue
            val raw = part.substring(eq + 1)
            return try {
                val decoded = URLDecoder.decode(raw, Charsets.UTF_8.name())
                decodeMaybeEncodedName(decoded) ?: decoded
            } catch (_: Exception) {
                decodeMaybeEncodedName(raw) ?: raw
            }
        }
        return null
    }

    private fun parseFilenameFromContentDisposition(disposition: String): String? {
        val star = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(disposition)
        if (star != null) {
            return try {
                URLDecoder.decode(star.groupValues[1].trim('"'), Charsets.UTF_8.name())
                    .substringBeforeLast('.')
            } catch (_: Exception) {
                null
            }
        }
        Regex("filename=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(disposition)?.let {
            return it.groupValues[1].trim().substringBeforeLast('.')
        }
        // attachment; filename=BRIDGE (no quotes)
        Regex("filename=([^;\\s]+)", RegexOption.IGNORE_CASE).find(disposition)?.let {
            return it.groupValues[1].trim().trim('"', '\'').substringBeforeLast('.')
        }
        return null
    }

    /**
     * Common airport headers: semicolon-separated stats, or base64-encoded JSON with account fields.
     */
    private fun parseSubscriptionUserinfo(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null

        fun fromJson(json: JSONObject): String? {
            val keys = listOf(
                "displayName", "display_name", "username", "userName", "account",
                "name", "title", "plan", "plan_name", "remark", "nickname", "email",
            )
            for (k in keys) {
                if (!json.has(k) || json.isNull(k)) continue
                val v = json.optString(k).trim()
                if (v.isNotBlank()) return v
            }
            return null
        }

        if (s.startsWith("{")) {
            return try {
                fromJson(JSONObject(s))
            } catch (_: Exception) {
                null
            }
        }

        val looksLikeB64 = s.length >= 24 && s.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        if (looksLikeB64) {
            val decoded = try {
                String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim()
            } catch (_: Exception) {
                return null
            }
            if (decoded.startsWith("{")) {
                return try {
                    fromJson(JSONObject(decoded))
                } catch (_: Exception) {
                    null
                }
            }
        }

        val trafficKeys = setOf("upload", "download", "total", "expire")
        val nameKeys = setOf(
            "display-name", "displayname", "username", "account", "name", "title",
            "plan", "plan_name", "remark", "app_name", "nickname", "usernickname",
        )
        for (part in s.split(';')) {
            val p = part.trim()
            val idx = p.indexOf('=')
            if (idx <= 0) continue
            val key = p.substring(0, idx).trim().lowercase()
            if (key in trafficKeys) continue
            if (key !in nameKeys) continue
            val v = p.substring(idx + 1).trim().trim('"', '\'')
            decodeMaybeEncodedName(v)?.let { return it }
            if (v.isNotBlank()) return v
        }
        return null
    }

    private fun normalizeHeaderValue(value: String): String =
        value.trim()
            .trim('"', '\'')
            // Fullwidth colon (some CDNs / panels)
            .replace('\uFF1A', ':')

    /**
     * Handles `base64:...`, bare base64 payloads, and URL-safe alphabet.
     */
    private fun decodeMaybeEncodedName(value: String): String? {
        val raw = normalizeHeaderValue(value)
        if (raw.isEmpty()) return null

        val payload = when {
            Regex("^base64\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(raw) ->
                raw.replaceFirst(Regex("^base64\\s*:", RegexOption.IGNORE_CASE), "").trim()
            else -> raw
        }
        if (payload.length < 4) return null

        fun looksLikeBase64(s: String): Boolean =
            s.isNotEmpty() && s.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '-' || it == '_' }

        if (!looksLikeBase64(payload)) return null

        fun tryDecode(flags: Int): String? = try {
            val normalized = payload.replace('-', '+').replace('_', '/')
            val decoded = String(Base64.decode(normalized, flags), Charsets.UTF_8).trim()
            decoded.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

        return tryDecode(Base64.DEFAULT)
            ?: tryDecode(Base64.DEFAULT or Base64.URL_SAFE)
    }

    private fun decodeResponseText(body: ByteArray): String {
        val raw = String(body, Charsets.UTF_8)
        tryDecodeBase64Config(raw)?.let { return it }
        return raw
    }

    private fun tryDecodeBase64Config(text: String): String? {
        val trimmed = text.trim().replace("\r", "").replace("\n", "")
        if (trimmed.length < 32) return null
        if (!trimmed.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) return null
        return try {
            val decoded = Base64.decode(trimmed, Base64.DEFAULT)
            String(decoded, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseNameFromSubscriptionBody(content: String): String? {
        val yaml = tryDecodeBase64Config(content.trim()) ?: content.trim()
        val lines = yaml.lines().take(64)
        for (line in lines) {
            val t = line.trim()
            if (t.startsWith("#")) {
                val c = t.removePrefix("#").trim()
                if (c.startsWith("!MANAGED-CONFIG", ignoreCase = true)) continue
                val title = Regex("^TITLE:\\s*(.+)$", RegexOption.IGNORE_CASE).find(c)
                if (title != null) {
                    val v = title.groupValues[1].trim()
                    return decodeMaybeEncodedName(v) ?: v
                }
            }
            val kv = Regex("^(?:name|title)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE).find(t)
            if (kv != null) {
                val v = kv.groupValues[1].trim().trim('"', '\'')
                return decodeMaybeEncodedName(v) ?: v
            }
            val profileTitle =
                Regex("^profile-title\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE).find(t)
            if (profileTitle != null) {
                val rawTitle = profileTitle.groupValues[1].trim().trim('"', '\'')
                return decodeMaybeEncodedName(rawTitle) ?: rawTitle
            }
        }
        return null
    }

    private fun sanitizeName(s: String): String =
        s.replace(Regex("[\r\n\t]"), " ").trim().take(64)

    /**
     * Best-effort name when no server-supplied title is available.
     *
     * Used to favour the last URL path segment, but for nearly every modern
     * panel (Marzban / Pasarguard / Marzneshin / 3X-UI / ...) that segment is a
     * subscription token like `asdkjzx1238sZasd` — looks awful as a profile
     * label. We now derive a brand name from the host:
     *
     *   sub.cubereon.io/abcdef…    → "cubereon"
     *   m.example.co.uk/abc        → "example"
     *   marzban.host.ru            → "marzban" (after dropping "host" as a
     *                                noisy infrastructure subdomain… no, kept;
     *                                we drop only generic prefixes like
     *                                sub/www/api/panel/subscription)
     *
     * The path segment is used *only* when it looks human (short or contains
     * obvious word separators), never for long alphanum tokens.
     */
    private fun fallbackName(urlString: String): String =
        try {
            val uri = URL(urlString)
            val pathSeg = uri.path?.trim('/')?.split('/')?.filter { it.isNotBlank() }?.lastOrNull()
            val humanPath = pathSeg?.let { trimFileExtension(it) }
                ?.takeIf { looksHumanReadable(it) && !looksLikeOpaqueToken(it) }
            val byHost = uri.host?.let(::brandFromHost)
            // Prefer a readable path label, but fall back to the host brand when
            // the last segment is an opaque subscription token (gsU8_wQwF814_Eo).
            val pick = humanPath ?: byHost
            pick?.replace(Regex("[^a-zA-Z0-9._-]"), "_")?.takeIf { it.isNotBlank() }
                ?: "Subscription"
        } catch (_: Exception) {
            "Subscription"
        }

    private fun trimFileExtension(segment: String): String =
        when {
            segment.endsWith(".yaml", ignoreCase = true) ||
                segment.endsWith(".yml", ignoreCase = true) ||
                segment.endsWith(".txt", ignoreCase = true) ||
                segment.endsWith(".json", ignoreCase = true) ->
                segment.substringBeforeLast('.')
            else -> segment
        }

    /**
     * True when a path segment plausibly carries a user-meaningful label
     * rather than a random subscription token: short (≤16), or contains word
     * separators with reasonable length.
     */
    private fun looksHumanReadable(segment: String): Boolean {
        if (segment.length <= 16) return true
        if (segment.length > 32) return false
        return segment.contains('-') || segment.contains('_')
    }

    /**
     * True when a segment looks like a random subscription token rather than a
     * label — the base62-ID signature: contains digits AND both upper- and
     * lower-case letters (e.g. `gsU8_wQwF814_Eo`). Plain word labels
     * (`premium`, `My-Plan`, `my_vpn`) are NOT flagged.
     */
    /**
     * True when a string looks like a random opaque token (mixed case + a digit, ≥8 core chars) —
     * a subscription id / auth token, not a human name. Shared by import (URL-segment guessing) and
     * update (ProfileManager decides whether to replace a stored name), so both agree on what a
     * "token" is and the update path never overwrites a name that import deliberately kept. (E-17)
     */
    fun looksLikeOpaqueToken(segment: String): Boolean {
        val core = segment.replace(Regex("[_-]"), "")
        if (core.length < 8) return false
        return core.any { it.isDigit() } &&
            core.any { it.isUpperCase() } &&
            core.any { it.isLowerCase() }
    }

    /**
     * Derive a brand-ish name from a host:
     *   - drop generic infra subdomain prefixes (sub, www, api, panel, m, …)
     *   - take the first remaining label (typically the operator brand)
     *   - fall back to the raw first label if everything was noisy
     */
    private fun brandFromHost(host: String): String? {
        val parts = host.lowercase().split('.').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val noisy = setOf(
            "sub", "subs", "subscription", "subscriptions",
            "www", "api", "panel", "app",
            "m", "s", "v1", "v2",
        )
        val significant = parts.dropWhile { it in noisy }
        return significant.firstOrNull()?.takeIf { it.length >= 2 } ?: parts.firstOrNull()
    }
}
