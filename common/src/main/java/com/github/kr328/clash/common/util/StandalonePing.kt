package com.github.kr328.clash.common.util

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale

/**
 * Latency tests that do not require Clash core / VPN — used when the engine is unavailable.
 */
object StandalonePing {

    /**
     * Reads `server` / `port` from a single proxy YAML block (Clash / Mihomo style).
     */
    fun parseServerPortFromProxyYaml(yaml: String): Pair<String, Int>? {
        val serverLine = Regex("(?m)^\\s*server:\\s*(.+)$").find(yaml) ?: return null
        var host = serverLine.groupValues[1].trim()
        if (host.startsWith('"') && host.endsWith('"') && host.length >= 2) {
            host = host.substring(1, host.length - 1)
        } else if (host.startsWith('\'') && host.endsWith('\'') && host.length >= 2) {
            host = host.substring(1, host.length - 1)
        }
        host = host.trim()
        if (host.isEmpty()) return null
        val portLine = Regex("(?m)^\\s*port:\\s*(\\d+)").find(yaml)
        val port = portLine?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 } ?: 443
        return host to port
    }

    /**
     * Built-in policy names — no remote host to TCP-probe from YAML.
     */
    fun isBuiltinProxyName(name: String): Boolean =
        name.uppercase(Locale.US) in BUILTIN_NAMES

    private val BUILTIN_NAMES = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE")

    /**
     * Measures time to complete HTTPS/HTTP request handshake (GET; many CDNs block HEAD).
     */
    suspend fun measureHttpLatency(urlString: String): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clean = urlString.trim().substringBefore('#')
                require(clean.startsWith("http://", true) || clean.startsWith("https://", true)) {
                    "not an http(s) url"
                }
                val url = URL(clean)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept-Encoding", "identity")
                }
                val t0 = SystemClock.elapsedRealtime()
                try {
                    conn.connect()
                    // Force the full request/response round-trip (not just the TCP connect) so the
                    // measured time reflects a real HTTP exchange. Return value intentionally ignored.
                    conn.responseCode
                    (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
                } finally {
                    conn.disconnect()
                }
            }
        }

    /**
     * TCP connect to [host]:[port] (e.g. 443) — fallback when URL is not http(s).
     */
    suspend fun tcpConnectMs(host: String, port: Int = 443): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val t0 = SystemClock.elapsedRealtime()
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), 10_000)
                }
                (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
            }
        }
}
