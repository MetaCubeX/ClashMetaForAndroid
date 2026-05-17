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

    /**
     * Explicit operator opt-in for the dedicated Operator tab. Even when brand
     * identity is set, the tab does not appear unless this header is true.
     */
    val showOperatorTab: Boolean? = null,

    /**
     * Master switch — explicit opt-in. Branding only applies when the
     * operator sends `X-Branding-Enabled: true`. Absent header / `false` /
     * `null` all mean "do not brand this subscription", regardless of any
     * other X-Brand-* field that may have been parsed.
     */
    val enabled: Boolean? = null,
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
            hideRouting == null &&
            showOperatorTab == null &&
            enabled == null

    /**
     * True when the manifest has enough to brand the app visually — meaning
     * a name, logo, or accent. Operator-info URLs alone (only support / privacy)
     * do not count: those flow through legacy SubscriptionMetadata anyway,
     * and on their own they shouldn't trigger any "brand active" UI.
     *
     * Branding is **explicit opt-in**. Unless the operator sends
     * `X-Branding-Enabled: true`, identity fields are ignored and the
     * client treats this manifest as unbranded — even if name / logo /
     * accent are present. This makes branding a feature the operator
     * consciously turns on per subscription, not something every panel
     * accidentally activates by setting one field.
     */
    fun hasBrandIdentity(): Boolean {
        if (enabled != true) return false
        return !name.isNullOrBlank() ||
            !logoUrl.isNullOrBlank() ||
            !logoLightUrl.isNullOrBlank() ||
            !accentColor.isNullOrBlank()
    }

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
