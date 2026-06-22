package com.github.kr328.clash.companion.controller

import com.github.kr328.clash.companion.protocol.Fingerprint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Error surfaced from the gateway's uniform error envelope (PROTOCOL.md §5.5). */
class CompanionError(val code: String, message: String) : Exception(message)

/**
 * Pinned-TLS bearer client for one paired agent (PROTOCOL.md §5.2). Validates the connection
 * solely by `SHA-256(DER(leaf))` against the pinned `fp` (no CA chain, no hostname check) and
 * attaches the bearer token plus this controller's identity headers on every request.
 *
 * Blocking I/O — call from a background dispatcher.
 */
class CompanionClient(
    private val agent: ControllerStore.PairedAgent,
    private val selfId: String,
    private val selfName: String,
) {
    private val client = buildPinnedClient(agent.fp)
    private val base = "https://${bracket(agent.host)}:${agent.port}"

    data class Status(val power: String, val name: String, val capabilities: List<String>)

    fun status(): Status {
        val obj = request("GET", "/v1/status", null)
        return Status(
            power = obj["power"]?.jsonPrimitive?.content ?: "off",
            name = obj["name"]?.jsonPrimitive?.content ?: agent.name,
            capabilities = (obj["capabilities"] as? kotlinx.serialization.json.JsonArray)
                ?.map { it.jsonPrimitive.content } ?: emptyList(),
        )
    }

    /** Returns the resulting power state. */
    fun power(action: String): String {
        val obj = request("POST", "/v1/power", buildJsonObject { put("action", action) })
        return obj["power"]?.jsonPrimitive?.content ?: action
    }

    fun subscription(url: String, name: String) {
        request("POST", "/v1/subscription", buildJsonObject { put("url", url); put("name", name) })
    }

    fun rename(name: String) {
        request("POST", "/v1/rename", buildJsonObject { put("name", name) })
    }

    private fun request(method: String, path: String, body: JsonObject?): JsonObject {
        val builder = Request.Builder()
            .url(base + path)
            .header("Authorization", "Bearer ${agent.token}")
            .header("X-Clashctl-Id", selfId)
            .header("X-Clashctl-Name", selfName)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(
                (body?.toString() ?: "{}").toRequestBody(JSON_MEDIA),
            )
        }
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            val error = json?.get("error")?.jsonObject
            if (error != null) {
                throw CompanionError(
                    error["code"]?.jsonPrimitive?.content ?: "internal",
                    error["message"]?.jsonPrimitive?.content ?: "request failed",
                )
            }
            if (!resp.isSuccessful) {
                throw CompanionError("http_${resp.code}", "HTTP ${resp.code}")
            }
            return json ?: buildJsonObject {}
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()

        fun bracket(host: String): String = if (host.contains(':')) "[$host]" else host

        fun buildPinnedClient(fp: String): OkHttpClient {
            val tm = PinnedTrustManager(fp)
            val ssl = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(tm), SecureRandom())
            }
            return OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, tm)
                .hostnameVerifier { _, _ -> true } // self-signed: identity is the pinned fp, not the name
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }

    /** Trusts exactly the certificate whose DER SHA-256 equals the pinned [fpHex] (§5.2). */
    private class PinnedTrustManager(private val fpHex: String) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull()
                ?: throw CertificateException("no certificate presented")
            val actual = Fingerprint.of(leaf)
            if (!actual.equals(fpHex, ignoreCase = true)) {
                throw CertificateException("pin mismatch")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
