package com.github.kr328.clash.common.util

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlin.text.Charsets

/**
 * Best-effort title for a remote subscription URL (Clash / similar).
 * Uses response headers and first lines of the body (incl. base64-decoded config).
 */
object SubscriptionNameGuesser {

    suspend fun guess(context: Context, urlString: String): String =
        withContext(Dispatchers.IO) {
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
                }
                try {
                    conn.connect()
                    if (conn.responseCode !in 200..299) {
                        return@withContext fallbackName(requestUrl)
                    }
                    conn.getHeaderField("Content-Disposition")?.let(::parseFilenameFromContentDisposition)
                        ?.let(::sanitizeName)?.takeIf { it.isNotBlank() }?.let { return@withContext it }

                    listOf(
                        "Subscription-Title",
                        "Profile-Title",
                        "X-Subscription-Title",
                    ).forEach { key ->
                        conn.getHeaderField(key)?.trim()?.takeIf { it.isNotBlank() }?.let {
                            return@withContext sanitizeName(it)
                        }
                    }

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

    private fun stripUrlFragment(urlString: String): String =
        urlString.substringBefore('#')

    /**
     * `#name=Title` or `#Title` (single segment) in subscription links.
     */
    private fun parseFragmentName(urlString: String): String? {
        val frag = urlString.substringAfter('#', "").trim()
        if (frag.isEmpty()) return null
        if (!frag.contains('=')) {
            if (frag.length in 2..72 && !frag.contains("://")) return frag
            return null
        }
        for (part in frag.split('&')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val key = part.substring(0, eq).trim()
            if (!key.equals("name", ignoreCase = true)) continue
            val raw = part.substring(eq + 1)
            return try {
                URLDecoder.decode(raw, Charsets.UTF_8.name())
            } catch (_: Exception) {
                raw
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
        val plain = Regex("filename=\"([^\"]+)\"").find(disposition) ?: return null
        return plain.groupValues[1].substringBeforeLast('.')
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
        val lines = yaml.lines().take(48)
        for (line in lines) {
            val t = line.trim()
            if (t.startsWith("#")) {
                val c = t.removePrefix("#").trim()
                if (c.startsWith("!MANAGED-CONFIG", ignoreCase = true)) continue
                val title = Regex("^TITLE:\\s*(.+)$", RegexOption.IGNORE_CASE).find(c)
                if (title != null) return title.groupValues[1].trim()
            }
            val kv = Regex("^(?:name|title)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE).find(t)
            if (kv != null) return kv.groupValues[1].trim().trim('"', '\'')
            val profileTitle =
                Regex("^profile-title\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE).find(t)
            if (profileTitle != null) {
                return profileTitle.groupValues[1].trim().trim('"', '\'')
            }
        }
        for (line in lines) {
            val t = line.trim()
            if (!t.startsWith("#")) continue
            val c = t.removePrefix("#").trim()
            if (c.length in 3..72 && !c.contains("://") && !c.startsWith("!")) return c
        }
        return null
    }

    private fun sanitizeName(s: String): String =
        s.replace(Regex("[\r\n\t]"), " ").trim().take(64)

    private fun fallbackName(urlString: String): String =
        try {
            val uri = URL(urlString)
            val path = uri.path?.trim('/')?.split('/')?.filter { it.isNotBlank() }?.lastOrNull()
            val safePath = path?.takeIf { it.length <= 48 }?.let { p ->
                p.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            }
            when {
                !safePath.isNullOrBlank() -> safePath
                !uri.host.isNullOrBlank() -> uri.host.substringBefore('.')
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                else -> "Subscription"
            }
        } catch (_: Exception) {
            "Subscription"
        }
}
