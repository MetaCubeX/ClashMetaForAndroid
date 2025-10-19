package com.github.kr328.clash.design.util

import com.github.kr328.clash.common.util.PatternFileName
import dev.oom_wg.purejoy.mlang.MLang

private object InputLimit {
    const val PORT_MIN = 1
    const val PORT_MAX = 65535
    const val MAX_URL_LENGTH = 2048
}

private object RegexPatterns {
    val IPV4_PATTERN = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
    val IPV6_PATTERN = Regex("""^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$""")
    val URL_PATTERN = Regex(
        """^(https?|clash|clashmeta)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]$""",
        RegexOption.IGNORE_CASE
    )
    val DOMAIN_PATTERN = Regex(
        """^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}$"""
    )
}

/** 统一输入校验器 */
object ValidatorsUnified {
    fun port(input: String?): String? {
        if (input.isNullOrBlank()) return MLang.validation_empty
        val n = input.toIntOrNull() ?: return MLang.validation_invalid_number
        if (n < InputLimit.PORT_MIN || n > InputLimit.PORT_MAX) {
            return MLang.validation_port_range
        }
        return null
    }

    fun nonBlank(input: String?): String? =
        if (input.isNullOrBlank()) MLang.validation_empty else null

    fun optional(input: String?): String? = null

    fun ipAddress(input: String?): String? {
        if (input.isNullOrBlank()) return MLang.validation_invalid_ip

        if (RegexPatterns.IPV4_PATTERN.matches(input)) {
            val parts = input.split(".")
            if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
                return null
            }
        }

        if (RegexPatterns.IPV6_PATTERN.matches(input)) {
            return null
        }

        return MLang.validation_invalid_ip
    }

    fun url(input: String?): String? {
        if (input.isNullOrBlank()) return MLang.validation_invalid_url
        if (input.length > InputLimit.MAX_URL_LENGTH) return MLang.validation_url_too_long

        return if (RegexPatterns.URL_PATTERN.matches(input)) null else MLang.validation_invalid_url
    }

    fun domain(input: String?): String? {
        if (input.isNullOrBlank()) return MLang.validation_invalid_domain

        return if (RegexPatterns.DOMAIN_PATTERN.matches(input)) null else MLang.validation_invalid_domain
    }

    fun cidr(input: String?): String? {
        if (input.isNullOrBlank()) return MLang.validation_invalid_cidr
        val parts = input.split("/")
        if (parts.size != 2) return MLang.validation_invalid_cidr

        val ipPart = parts[0]
        val maskPart = parts[1].toIntOrNull()

        if (maskPart == null || maskPart < 0 || maskPart > 128) {
            return MLang.validation_invalid_cidr
        }

        if (ipAddress(ipPart) != null) {
            return MLang.validation_invalid_cidr
        }

        return null
    }

    fun positiveInt(input: String?): String? {
        if (input.isNullOrBlank()) return MLang.validation_empty
        val n = input.toIntOrNull() ?: return MLang.validation_invalid_number
        return if (n > 0) null else MLang.validation_must_be_positive
    }

    fun nonNegativeInt(input: String?): String? {
        if (input.isNullOrBlank()) return MLang.validation_empty
        val n = input.toIntOrNull() ?: return MLang.validation_invalid_number
        return if (n >= 0) null else MLang.validation_must_be_non_negative
    }

    fun maxLength(max: Int): (String?) -> String? = { input ->
        if (input?.length ?: 0 > max) String.format(MLang.validation_text_too_long, max) else null
    }

    fun all(vararg validators: (String?) -> String?): (String?) -> String? = { input ->
        validators.firstNotNullOfOrNull { it(input) }
    }

    fun any(vararg validators: (String?) -> String?): (String?) -> String? = { input ->
        if (validators.all { it(input) != null }) {
            validators.first()(input)
        } else {
            null
        }
    }

    fun fileName(input: String?): String? {
        return if (!input.isNullOrBlank() && PatternFileName.matches(input)) {
            null
        } else {
            MLang.validation_invalid_filename
        }
    }

    fun httpUrl(input: String?): String? {
        if (input.isNullOrBlank()) return MLang.validation_empty

        val isValid = input.startsWith("https://", ignoreCase = true) ||
                input.startsWith("http://", ignoreCase = true)
        return if (isValid) null else MLang.validation_invalid_http_url
    }

    fun autoUpdateInterval(input: String?): String? {
        if (input.isNullOrBlank()) return null

        val minutes = input.toLongOrNull() ?: return MLang.validation_invalid_number
        return if (minutes >= 15) null else MLang.validation_interval_too_short
    }

    fun acceptAll(input: String?): String? = null

    fun toLegacyValidator(validator: (String?) -> String?): (String?) -> Boolean = { input ->
        validator(input) == null
    }

    fun fileNameLegacy(input: String?): Boolean = fileName(input) == null
    fun httpUrlLegacy(input: String?): Boolean = httpUrl(input) == null
    fun autoUpdateIntervalLegacy(input: String?): Boolean = autoUpdateInterval(input) == null
    fun acceptAllLegacy(input: String?): Boolean = true
}





