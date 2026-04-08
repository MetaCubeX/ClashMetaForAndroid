package com.github.kr328.clash.service.util

import com.github.kr328.clash.common.log.Log

/**
 * Clears process-wide JVM proxy properties that could leak traffic behavior
 * outside the intended VPN/runtime proxy paths.
 */
object ProxyPropertyGuard {
    private val keys = listOf(
        "http.proxyHost",
        "http.proxyPort",
        "https.proxyHost",
        "https.proxyPort",
        "socksProxyHost",
        "socksProxyPort",
        "java.net.useSystemProxies",
    )

    fun clearGlobalProxyProperties() {
        var changed = false
        for (key in keys) {
            if (!System.getProperty(key).isNullOrBlank()) {
                System.clearProperty(key)
                changed = true
            }
        }
        if (changed) {
            Log.w("Cleared process global proxy properties")
        }
    }
}
