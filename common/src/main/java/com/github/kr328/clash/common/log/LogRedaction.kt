package com.github.kr328.clash.common.log

/**
 * Safe strings for logs/UI when errors may embed URLs, tokens, or query parameters.
 */
object LogRedaction {
    fun throwableMessage(t: Throwable): String {
        val raw = (t.message ?: t.javaClass.simpleName).trim()
        return redactSuspicious(raw)
    }

    fun redactSuspicious(message: String): String {
        var s = message
        listOf(
            Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE),
            Regex("""Bearer\s+\S+""", RegexOption.IGNORE_CASE),
            Regex("""token=\S+""", RegexOption.IGNORE_CASE),
            Regex("""password=\S+""", RegexOption.IGNORE_CASE),
        ).forEach { rx ->
            s = rx.replace(s) { "[redacted]" }
        }
        return s
    }
}
