package com.github.kr328.clash.common.branding

import com.github.kr328.clash.common.util.MaybeBase64

/**
 * Pure validators for operator-supplied brand values. Every method takes a raw
 * header value and returns either a sanitised result or null when the input
 * fails the rules in docs/operator-api/security.md.
 *
 * Validation here is intentionally strict — UI layers must be able to trust
 * any non-null value without further checks.
 */
object BrandValidation {

    const val NAME_MAX_LENGTH = 32
    const val TAGLINE_MAX_LENGTH = 64

    private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    private val CONTROL_CHARS = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    fun cleanName(raw: String?): String? = cleanString(raw, NAME_MAX_LENGTH)

    fun cleanTagline(raw: String?): String? = cleanString(raw, TAGLINE_MAX_LENGTH)

    /** Strips control chars, trims, decodes base64 prefix, truncates to [maxLength]. */
    fun cleanString(raw: String?, maxLength: Int): String? {
        if (raw.isNullOrBlank()) return null
        val decoded = MaybeBase64.decode(raw.trim())
        val stripped = decoded.replace(CONTROL_CHARS, "").trim()
        if (stripped.isBlank()) return null
        return if (stripped.length > maxLength) stripped.substring(0, maxLength) else stripped
    }

    /**
     * Accepts `https://`, `tg://`, `mailto:`, and `t.me/` (auto-promoted).
     * Returns the normalised URL or null. **http:// is rejected** for brand
     * URLs — they're displayed prominently and must be transport-secure.
     */
    fun cleanBrandUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val v = raw.trim().trim('"', '\'')
        if (v.isBlank()) return null
        val normalised = if (v.startsWith("t.me/", ignoreCase = true)) "https://$v" else v
        val ok = normalised.startsWith("https://", ignoreCase = true) ||
            normalised.startsWith("tg://", ignoreCase = true) ||
            normalised.startsWith("mailto:", ignoreCase = true)
        if (!ok) return null
        // Reject embedded whitespace / control chars that some operators occasionally
        // glue onto URLs.
        if (normalised.any { it.isWhitespace() || it.code < 0x20 }) return null
        return normalised
    }

    /**
     * Accepts only `https://` for logo URLs. Stricter than [cleanBrandUrl]
     * because the client will follow these and download bytes from them.
     */
    fun cleanLogoUrl(raw: String?): String? {
        val cleaned = cleanBrandUrl(raw) ?: return null
        return if (cleaned.startsWith("https://", ignoreCase = true)) cleaned else null
    }

    /**
     * Accepts a `#RRGGBB` hex color. Returns the normalised form (`#` + uppercase
     * hex), or null. Does **not** check contrast — that needs the surface color
     * which is a UI-layer concern. See [hasMinContrast].
     */
    fun cleanHexColor(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val v = raw.trim().trim('"', '\'')
        if (!HEX_COLOR.matches(v)) return null
        return "#" + v.substring(1).uppercase()
    }

    fun parseBoolean(raw: String?): Boolean? {
        if (raw.isNullOrBlank()) return null
        return when (raw.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
    }

    /**
     * WCAG-relative-luminance contrast check used to reject accent colors that
     * would be unreadable against a given surface. Returns true when the
     * contrast ratio between [foreground] and [surface] is at least
     * [minRatio] (default 3.0, the AA "large text" minimum).
     *
     * UI layer calls this with the current theme's primary-on surface pair
     * before applying an operator-supplied accent.
     */
    fun hasMinContrast(foreground: Int, surface: Int, minRatio: Double = 3.0): Boolean {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(surface)
        val (lighter, darker) = if (l1 > l2) l1 to l2 else l2 to l1
        return (lighter + 0.05) / (darker + 0.05) >= minRatio
    }

    private fun relativeLuminance(color: Int): Double {
        val r = channelLuminance(((color shr 16) and 0xFF) / 255.0)
        val g = channelLuminance(((color shr 8) and 0xFF) / 255.0)
        val b = channelLuminance((color and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channelLuminance(c: Double): Double {
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
}
