package com.github.kr328.clash.util

import com.github.kr328.clash.common.network.AppNetworkDefaults
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream

object HttpTextFetcher {
    fun fetchUtf8(
        url: String,
        connectTimeoutMs: Int = AppNetworkDefaults.CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = AppNetworkDefaults.READ_TIMEOUT_MS,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Int = DEFAULT_MAX_BYTES,
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        return try {
            val declared = conn.contentLengthLong
            require(declared < 0 || declared <= maxBytes) { "HTTP response exceeds $maxBytes bytes" }
            val bytes = conn.inputStream.use { input ->
                val out = ByteArrayOutputStream(minOf(maxBytes, declared.coerceAtLeast(0).toInt()))
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "HTTP response exceeds $maxBytes bytes" }
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            bytes.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    internal const val DEFAULT_MAX_BYTES = 1024 * 1024
}
