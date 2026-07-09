package com.github.kr328.clash.service.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Reads the `fetch-headers.json` snapshot the Go side writes next to
 * `config.yaml` on every successful subscription download (see
 * `writeFetchHeaders` in core/src/main/golang/native/config/fetch.go).
 *
 * This replaces the SECOND OkHttp GET of the subscription that
 * [com.github.kr328.clash.service.ProfileProcessor] / ProfileManager used to
 * issue just to read response headers (subscription-userinfo quota, X-Brand-*
 * manifest, display-name headers). The extra request doubled traffic — panels
 * count fetches against quota — could race the primary download, and kept a
 * parallel header-decoding path alive.
 *
 * Keys are lowercased by the Go writer; [get] is case-insensitive so existing
 * consumers (`BrandManifestParser.parseHttpHeaders`,
 * `SubscriptionNameGuesser.titleFromHeaders`, `SubscriptionUsage.parse`) can
 * pass their historical mixed-case keys unchanged.
 */
class FetchHeadersFile private constructor(private val headers: Map<String, String>) {
    fun get(key: String): String? = headers[key.lowercase()]

    val isEmpty: Boolean get() = headers.isEmpty()

    companion object {
        const val FILE_NAME = "fetch-headers.json"

        /**
         * @return the parsed snapshot, or null when the profile has no fetched
         *         headers (content:// / inline import, pre-migration profile,
         *         or an unreadable file). Callers treat null as "no HTTP
         *         response observed" — the same meaning the old code gave a
         *         failed OkHttp request.
         */
        fun readFrom(profileDir: File): FetchHeadersFile? {
            val file = File(profileDir, FILE_NAME)
            if (!file.isFile) return null
            return runCatching {
                val root = Json.parseToJsonElement(file.readText()).jsonObject
                val map = root.entries.associate { (k, v) -> k.lowercase() to v.jsonPrimitive.content }
                FetchHeadersFile(map)
            }.getOrNull()
        }
    }
}
