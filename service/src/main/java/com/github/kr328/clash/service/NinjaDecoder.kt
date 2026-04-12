package com.github.kr328.clash.service

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

object NinjaDecoder {
    data class Input(
        val server: String,
        val port: Int,
        val password: String,
        val nodePassword: String,
    )

    data class Output(
        val server: String,
        val port: Int,
        val nodePassword: String,
    )

    fun decode(input: Input): Output {
        val domainAlphabet = saltShuffle("0123456789abcdefghijklmnopqrstuvwxyz", input.password)
        val nodeAlphabet =
            saltShuffle("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ@#$%^&*", input.password)

        val serverParts = input.server.split('.', limit = 2)
        val label = serverParts.firstOrNull() ?: throw IllegalStateException("bad label format")
        val suffix = serverParts.getOrNull(1)

        val labelSplitAt = label.indexOf('-')
        if (labelSplitAt < 0) {
            throw IllegalStateException("bad label format")
        }

        val id = label.substring(0, labelSplitAt).takeLast(3)
        val payload = label.substring(labelSplitAt + 1)
        if (payload.isEmpty()) {
            throw IllegalArgumentException("empty payload")
        }
        if (id !in setOf("01a", "01b", "01c", "02a", "02b", "02c")) {
            throw IllegalStateException("bad id")
        }

        val decodedNodePayload = inflateRawDeflate(
            strXor(baseNDecode(input.nodePassword, nodeAlphabet), payload)
        ) ?: ByteArray(0)

        val separator = decodedNodePayload.indexOf(0x1f)
        val nodePassword = if (separator >= 0) {
            decodedNodePayload.copyOfRange(0, separator).toString(Charsets.UTF_8)
        } else {
            ""
        }

        val (portSeed, payloadSuffix) = if (separator >= 0) {
            val rest = decodedNodePayload.copyOfRange(separator + 1, decodedNodePayload.size)
            val splitAt = rest.indexOf('#'.code.toByte())

            if (splitAt >= 0) {
                val seed = rest.copyOfRange(0, splitAt).toString(Charsets.UTF_8).toLongOrNull() ?: 0L
                val extra = rest.copyOfRange(splitAt + 1, rest.size).toString(Charsets.UTF_8)
                seed to extra
            } else {
                0L to ""
            }
        } else {
            0L to ""
        }

        val serverPayload = payload + payloadSuffix
        val xorKey = "${input.password}:${input.port}"

        val server = when (id) {
            "01a", "01b" -> {
                val decoded = strXor(baseNDecode(serverPayload, domainAlphabet), xorKey)
                    .toString(Charsets.UTF_8)
                if (!suffix.isNullOrEmpty()) "$decoded.$suffix" else decoded
            }

            "01c" -> {
                val decoded = inflateRawDeflate(strXor(baseNDecode(serverPayload, domainAlphabet), xorKey))
                    ?: ByteArray(0)
                decoded.toString(Charsets.UTF_8)
            }

            "02a" -> {
                val decoded = baseNDecode(serverPayload, domainAlphabet)
                require(decoded.size == 4) { "bad ipv4 payload" }
                decoded.joinToString(".") { (it.toInt() and 0xff).toString() }
            }

            "02b", "02c" -> {
                strXor(baseNDecode(serverPayload, domainAlphabet), xorKey)
                    .toString(Charsets.UTF_8)
                    .lowercase(Locale.ROOT)
            }

            else -> throw IllegalStateException("unknown id")
        }

        val crc = CRC32().apply {
            update(input.password.toByteArray(Charsets.UTF_8))
        }.value

        var port = (portSeed * 10001L) + (input.port - 10000L) - crc
        if (port < 0) {
            port = 0
        }
        if (port > 0xffff) {
            port %= 0x10000
        }

        return Output(
            server = server,
            port = port.toInt(),
            nodePassword = nodePassword
        )
    }

    private fun baseNDecode(input: String, alphabet: String): ByteArray {
        if (input.isEmpty()) {
            return ByteArray(0)
        }

        val base = alphabet.length
        val indexes = IntArray(128) { -1 }
        alphabet.forEachIndexed { index, ch ->
            require(ch.code < 128) { "alphabet must be ASCII" }
            indexes[ch.code] = index
        }

        val zero = alphabet.first()
        var leadingZeroCount = 0
        while (leadingZeroCount < input.length && input[leadingZeroCount] == zero) {
            leadingZeroCount++
        }

        val buffer = ArrayList<Int>(input.length)
        input.forEach { ch ->
            var carry = if (ch.code < 128) indexes[ch.code] else -1
            require(carry >= 0) { "invalid character in input: '$ch'" }

            for (index in buffer.indices) {
                val value = (buffer[index] * base) + carry
                buffer[index] = value and 0xff
                carry = value ushr 8
            }

            while (carry > 0) {
                buffer.add(carry and 0xff)
                carry = carry ushr 8
            }
        }

        return ByteArray(buffer.size + leadingZeroCount).also { output ->
            buffer.forEachIndexed { index, value ->
                output[output.lastIndex - index] = value.toByte()
            }
        }
    }

    private fun inflateRawDeflate(input: ByteArray): ByteArray? {
        if (input.isEmpty()) {
            return ByteArray(0)
        }

        return runCatching {
            InflaterInputStream(ByteArrayInputStream(input), Inflater(true)).use { inflater ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val read = inflater.read(buffer)
                        if (read <= 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
            }
        }.getOrNull()
    }

    private fun saltShuffle(alphabet: String, salt: String): String {
        return alphabet.map { ch ->
            val digest = MessageDigest.getInstance("SHA-256").run {
                update(salt.toByteArray(Charsets.UTF_8))
                update("|".toByteArray(Charsets.UTF_8))
                update(byteArrayOf(ch.code.toByte()))
                digest()
            }
            PairKV(digest, ch)
        }.sortedWith { left, right ->
            val min = minOf(left.key.size, right.key.size)
            for (index in 0 until min) {
                val diff = (left.key[index].toInt() and 0xff) - (right.key[index].toInt() and 0xff)
                if (diff != 0) {
                    return@sortedWith diff
                }
            }
            left.key.size - right.key.size
        }.joinToString(separator = "") { it.ch.toString() }
    }

    private fun strXor(input: ByteArray, salt: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(salt.toByteArray(Charsets.UTF_8))
        return ByteArray(input.size) { index ->
            (input[index].toInt() xor (digest[index % digest.size].toInt() and 0xff)).toByte()
        }
    }

    private data class PairKV(
        val key: ByteArray,
        val ch: Char,
    )
}
