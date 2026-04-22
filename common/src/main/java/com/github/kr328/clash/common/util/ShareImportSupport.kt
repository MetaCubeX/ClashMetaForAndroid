package com.github.kr328.clash.common.util

/**
 * Clipboard / QR / profile URL sources that the app can import as a URL-type profile.
 */
object ShareImportSupport {

    fun isMierusSubscriptionPayload(trimmed: String): Boolean {
        val t = trimmed.trim()
        if (t.isEmpty()) return false
        var any = false
        for (line in t.lineSequence()) {
            val s = line.trim()
            if (s.isEmpty()) continue
            any = true
            if (!s.startsWith("mierus://", ignoreCase = true)) return false
        }
        return any
    }

    fun isAllowedUrlProfileSource(source: String): Boolean {
        val t = source.trim()
        if (t.startsWith("http://", ignoreCase = true)) return true
        if (t.startsWith("https://", ignoreCase = true)) return true
        if (t.startsWith("content:", ignoreCase = true)) return true
        return isMierusSubscriptionPayload(t)
    }
}
