package com.github.kr328.clash.companion.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Canonical JSON (PROTOCOL.md §3.4) — RFC 8785 (JCS) constrained to this protocol's value space.
 * Used for every request/response body so two implementations emit byte-identical bytes.
 *
 * Rules:
 *  1. UTF-8 output.
 *  2. Object keys sorted ascending by Unicode code point.
 *  3. Separators `,` and `:` with no insignificant whitespace; no trailing newline.
 *  4. Minimal escaping (`"`, `\`, control chars < U+0020); non-ASCII emitted raw (no `\u`).
 *  5. All numbers are integers (the protocol uses no fractional numbers).
 */
object CanonicalJson {
    private val parser = Json

    /** Parse an arbitrary JSON string and re-emit it in canonical form. */
    fun canonicalize(json: String): String = encode(parser.parseToJsonElement(json))

    fun encode(element: JsonElement): String = StringBuilder().also { append(it, element) }.toString()

    private fun append(sb: StringBuilder, element: JsonElement) {
        when (element) {
            is JsonObject -> {
                sb.append('{')
                element.entries
                    .sortedWith(compareBy(CODE_POINT_ORDER) { it.key })
                    .forEachIndexed { i, (key, value) ->
                        if (i > 0) sb.append(',')
                        appendString(sb, key)
                        sb.append(':')
                        append(sb, value)
                    }
                sb.append('}')
            }
            is JsonArray -> {
                sb.append('[')
                element.forEachIndexed { i, value ->
                    if (i > 0) sb.append(',')
                    append(sb, value)
                }
                sb.append(']')
            }
            is JsonNull -> sb.append("null")
            is JsonPrimitive ->
                if (element.isString) appendString(sb, element.content)
                else sb.append(element.content) // numbers / booleans emitted verbatim
        }
    }

    private fun appendString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else ->
                    if (c < ' ') sb.append("\\u%04x".format(c.code))
                    else sb.append(c) // raw, including non-ASCII and < > &
            }
        }
        sb.append('"')
    }

    /** Compare strings by Unicode code point (not UTF-16 code unit), per JCS key ordering. */
    private val CODE_POINT_ORDER = Comparator<String> { a, b ->
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a.codePointAt(i)
            val cb = b.codePointAt(j)
            if (ca != cb) return@Comparator ca - cb
            i += Character.charCount(ca)
            j += Character.charCount(cb)
        }
        (a.length - i) - (b.length - j)
    }
}
