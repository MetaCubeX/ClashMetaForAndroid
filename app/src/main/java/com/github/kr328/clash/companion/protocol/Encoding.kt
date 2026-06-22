package com.github.kr328.clash.companion.protocol

/**
 * clashctl wire encodings (PROTOCOL.md §3). Pure-JVM, no Android APIs, so the golden
 * vectors run in plain unit tests.
 *
 * `java.util.Base64` is API 26+ and the app's minSdk is 21, so base64url is hand-rolled here.
 */
object Base64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val REVERSE = IntArray(128) { -1 }.also { r ->
        ALPHABET.forEachIndexed { i, c -> r[c.code] = i }
    }

    /** base64url(no-pad) per RFC 4648 §5 — alphabet `A–Z a–z 0–9 - _`, no `=` padding. */
    fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size * 4 + 2) / 3)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else -1
            sb.append(ALPHABET[b0 ushr 2])
            when {
                b1 == -1 -> sb.append(ALPHABET[(b0 and 0x03) shl 4])
                b2 == -1 -> {
                    sb.append(ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
                    sb.append(ALPHABET[(b1 and 0x0F) shl 2])
                }
                else -> {
                    sb.append(ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
                    sb.append(ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)])
                    sb.append(ALPHABET[b2 and 0x3F])
                }
            }
            i += 3
        }
        return sb.toString()
    }

    fun decode(s: String): ByteArray {
        val out = ArrayList<Byte>(s.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in s) {
            val v = if (c.code < 128) REVERSE[c.code] else -1
            require(v >= 0) { "Invalid base64url character: $c" }
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer ushr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}

/** Lowercase hex per PROTOCOL.md (token hash §7.2, fingerprint §3.3). */
object Hex {
    private const val DIGITS = "0123456789abcdef"

    fun encode(data: ByteArray): String {
        val sb = StringBuilder(data.size * 2)
        for (b in data) {
            val v = b.toInt() and 0xFF
            sb.append(DIGITS[v ushr 4])
            sb.append(DIGITS[v and 0x0F])
        }
        return sb.toString()
    }

    fun decode(s: String): ByteArray {
        require(s.length % 2 == 0) { "Odd-length hex string" }
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(s[i * 2], 16)
            val lo = Character.digit(s[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex string" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
