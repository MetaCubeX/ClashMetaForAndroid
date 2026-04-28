package com.github.kr328.clash.util

import com.github.kr328.clash.common.network.AppNetworkDefaults
import java.net.HttpURLConnection
import java.net.URL

object HttpTextFetcher {
    fun fetchUtf8(
        url: String,
        connectTimeoutMs: Int = AppNetworkDefaults.CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = AppNetworkDefaults.READ_TIMEOUT_MS,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        return try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
