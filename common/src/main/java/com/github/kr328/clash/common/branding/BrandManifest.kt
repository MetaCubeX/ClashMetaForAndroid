package com.github.kr328.clash.common.branding

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Operator-supplied brand data parsed from HTTP response headers.
 *
 * Every field is null when the corresponding header is absent or fails
 * validation — UI layers should treat null as "operator didn't supply this,
 * use default". See [BrandValidation] for the rules and
 * docs/operator-api/headers.md for the full spec.
 *
 * Source profile UUID is tracked separately so the store can purge brand
 * data when the owning subscription is deleted.
 */
@Serializable
data class BrandManifest(
    // Identity
    val name: String? = null,
    val tagline: String? = null,
    val logoUrl: String? = null,
    val logoLightUrl: String? = null,
    /** Validated hex like `#5E35B1`. Null if missing or contrast-rejected. */
    val accentColor: String? = null,

    // Operator info
    val websiteUrl: String? = null,
    val supportUrl: String? = null,
    val telegramUrl: String? = null,
    val botUrl: String? = null,
    val privacyUrl: String? = null,
    val termsUrl: String? = null,
    val helpUrl: String? = null,
    val statusUrl: String? = null,
    val renewUrl: String? = null,

    // UX simplification flags. Null = operator hasn't expressed a preference.
    val hideStats: Boolean? = null,
    val hideLogs: Boolean? = null,
    val hideRouting: Boolean? = null,
) {
    fun isEmpty(): Boolean =
        name == null &&
            tagline == null &&
            logoUrl == null &&
            logoLightUrl == null &&
            accentColor == null &&
            websiteUrl == null &&
            supportUrl == null &&
            telegramUrl == null &&
            botUrl == null &&
            privacyUrl == null &&
            termsUrl == null &&
            helpUrl == null &&
            statusUrl == null &&
            renewUrl == null &&
            hideStats == null &&
            hideLogs == null &&
            hideRouting == null

    /**
     * Pick the right logo URL for the user's current theme.
     * Falls back to [logoUrl] when no light-specific variant is provided.
     */
    fun logoUrlFor(darkTheme: Boolean): String? =
        if (darkTheme) logoUrl
        else (logoLightUrl ?: logoUrl)

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

        val EMPTY = BrandManifest()

        fun fromJson(raw: String?): BrandManifest {
            if (raw.isNullOrBlank()) return EMPTY
            return try {
                json.decodeFromString(serializer(), raw)
            } catch (_: Exception) {
                EMPTY
            }
        }
    }
}
