package com.github.kr328.clash.design.util

import android.util.LruCache
import java.util.Locale

data class ParsedFlag(
    val code: String,
    val emoji: String,
)

object FlagParser {
    // LruCache disallows null values; use a sentinel to memoize "no flag" results.
    private val NONE = ParsedFlag("", "")
    private val cache = LruCache<String, ParsedFlag>(512)

    private val COUNTRY_NAMES = mapOf(
        "united states" to "US", "usa" to "US", "us" to "US", "america" to "US",
        "united kingdom" to "GB", "uk" to "GB", "britain" to "GB", "england" to "GB",
        "germany" to "DE", "deutschland" to "DE",
        "france" to "FR",
        "japan" to "JP",
        "china" to "CN",
        "russia" to "RU", "россия" to "RU",
        "singapore" to "SG",
        "hong kong" to "HK", "hongkong" to "HK",
        "taiwan" to "TW",
        "south korea" to "KR", "korea" to "KR",
        "australia" to "AU",
        "canada" to "CA",
        "brazil" to "BR",
        "india" to "IN",
        "netherlands" to "NL",
        "germany" to "DE",
        "finland" to "FI",
        "sweden" to "SE",
        "switzerland" to "CH",
        "turkey" to "TR", "turkiye" to "TR",
        "argentina" to "AR",
        "ireland" to "IE",
        "poland" to "PL",
        "romania" to "RO",
        "spain" to "ES",
        "italy" to "IT",
        "portugal" to "PT",
        "mexico" to "MX",
        "colombia" to "CO",
        "chile" to "CL",
        "peru" to "PE",
        "thailand" to "TH",
        "vietnam" to "VN",
        "indonesia" to "ID",
        "malaysia" to "MY",
        "philippines" to "PH",
        "israel" to "IL",
        "uae" to "AE", "dubai" to "AE",
        "south africa" to "ZA",
        "ukraine" to "UA", "украина" to "UA",
        "norway" to "NO",
        "denmark" to "DK",
        "czech" to "CZ", "czechia" to "CZ",
        "austria" to "AT",
        "belgium" to "BE",
        "new zealand" to "NZ",
        "luxembourg" to "LU",
        "iceland" to "IS",
    )

    private val VALID_CODES = Locale.getISOCountries().toSet()

    private val BRACKET_PATTERN = Regex("""[\[\({]([A-Za-z]{2})[\]\)}]""")
    private val PREFIX_PATTERN = Regex("""^(?:Flag[_\-]?)?([A-Za-z]{2})[|_\-:.\s]""", RegexOption.IGNORE_CASE)
    private val SUFFIX_PATTERN = Regex("""[|_\-:.\s]([A-Za-z]{2})$""", RegexOption.IGNORE_CASE)
    private val STANDALONE_PATTERN = Regex("""(?:^|[|\-_.\s])([A-Za-z]{2})(?:[|\-_.\s]|$)""")

    fun parse(proxyName: String?): ParsedFlag? {
        if (proxyName.isNullOrEmpty()) return null
        cache.get(proxyName)?.let { return if (it === NONE) null else it }

        val result = parseImpl(proxyName)
        cache.put(proxyName, result ?: NONE)
        return result
    }

    private fun parseImpl(name: String): ParsedFlag? {
        parseEmojiFlag(name)?.let { return it }

        for (match in BRACKET_PATTERN.findAll(name)) {
            val code = match.groupValues[1].uppercase()
            if (code in VALID_CODES) return ParsedFlag(code, codeToEmoji(code))
        }

        for (match in PREFIX_PATTERN.findAll(name)) {
            val code = match.groupValues[1].uppercase()
            if (code in VALID_CODES) return ParsedFlag(code, codeToEmoji(code))
        }

        for (match in SUFFIX_PATTERN.findAll(name)) {
            val code = match.groupValues[1].uppercase()
            if (code in VALID_CODES) return ParsedFlag(code, codeToEmoji(code))
        }

        for (match in STANDALONE_PATTERN.findAll(name)) {
            val code = match.groupValues[1].uppercase()
            if (code in VALID_CODES) return ParsedFlag(code, codeToEmoji(code))
        }

        val lower = name.lowercase()
        for ((countryName, code) in COUNTRY_NAMES) {
            if (lower.contains(countryName)) {
                return ParsedFlag(code, codeToEmoji(code))
            }
        }

        // No country flag — fall back to any pictographic emoji in the name
        // (e.g. 🌍 in "Авто EU · 🌍 Сеть"). code="" → no SVG, shown as emoji text.
        parseStandaloneEmoji(name)?.let { return it }

        return null
    }

    /** First standalone pictographic emoji (not a regional-indicator flag pair). */
    private fun parseStandaloneEmoji(text: String): ParsedFlag? {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            if (isPictographicEmoji(cp)) {
                var end = i + charCount
                // absorb a trailing emoji variation selector (U+FE0F)
                if (end < text.length && text.codePointAt(end) == 0xFE0F) {
                    end += Character.charCount(0xFE0F)
                }
                return ParsedFlag("", text.substring(i, end))
            }
            i += charCount
        }
        return null
    }

    private fun isPictographicEmoji(cp: Int): Boolean =
        cp in 0x1F300..0x1FAFF ||   // symbols & pictographs, emoticons, transport, supplemental
        cp in 0x2600..0x27BF ||     // misc symbols + dingbats
        cp in 0x2B00..0x2BFF        // misc symbols & arrows

    private fun parseEmojiFlag(text: String): ParsedFlag? {
        var i = 0
        while (i < text.length) {
            val cp1 = text.codePointAt(i)
            val next = i + Character.charCount(cp1)
            if (next >= text.length) break
            val cp2 = text.codePointAt(next)
            if (isRegionalIndicator(cp1) && isRegionalIndicator(cp2)) {
                val code = "${regionalToLetter(cp1)}${regionalToLetter(cp2)}"
                if (code in VALID_CODES) {
                    return ParsedFlag(code, text.substring(i, next + Character.charCount(cp2)))
                }
            }
            i = next
        }
        return null
    }

    private fun isRegionalIndicator(codePoint: Int): Boolean =
        codePoint in 0x1F1E6..0x1F1FF

    private fun regionalToLetter(codePoint: Int): Char = ('A' + (codePoint - 0x1F1E6))

    fun codeToEmoji(code: String): String {
        if (code.length != 2) return ""
        val c1 = 0x1F1E6 + (code[0].uppercaseChar() - 'A')
        val c2 = 0x1F1E6 + (code[1].uppercaseChar() - 'A')
        return String(Character.toChars(c1)) + String(Character.toChars(c2))
    }
}
