package com.github.kr328.clash.service.util

import java.io.File

/**
 * Turns a cryptic fetch/parse failure into a clear reason when the subscription
 * server returned a NON-config body (an HTML error page, a rate-limit notice, or
 * an empty response) instead of YAML. In that case mihomo fails with a confusing
 * `yaml: line N: ...` pointing at the error page. A genuine config error (valid
 * YAML, invalid values) is left untouched so its precise engine message survives.
 */
object FetchErrorClassifier {
    fun clarify(processingDir: File, original: Throwable): Throwable {
        val file = File(processingDir, "config.yaml")
        // No body downloaded → network/other failure; keep the original error.
        if (!file.isFile) return original
        val body = runCatching { file.readText() }.getOrNull() ?: return original

        val reason = when {
            body.isBlank() ->
                "the subscription server returned an empty response"
            looksLikeHtml(body) ->
                "the subscription server returned a web page, not a config (likely rate-limited or an error page)"
            else -> return original // valid-looking config body → keep the engine's precise error
        }
        return IllegalStateException("$reason — try again later.", original)
    }

    internal fun looksLikeHtml(body: String): Boolean {
        val head = body.trimStart().take(512).lowercase()
        if (head.isEmpty()) return false
        return head.startsWith("<!doctype") ||
            head.startsWith("<html") ||
            head.startsWith("<?xml") ||
            head.contains("<head") ||
            head.contains("<body") ||
            head.contains("<title") ||
            (head.startsWith("<") && head.contains("</"))
    }
}
