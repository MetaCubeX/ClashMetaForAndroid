package com.github.kr328.clash.companion.protocol

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Identifier & secret generation/encoding (PROTOCOL.md §3.1, §3.2, §7.2).
 *
 * - deviceId: 16 random bytes -> base64url(no-pad) = 22 chars.
 * - token:    32 random bytes -> base64url(no-pad) = 43 chars.
 * - tokenHash: lowercasehex(SHA-256(token)) — what the agent stores at rest.
 */
object Ids {
    private val random = SecureRandom()

    fun newDeviceId(): String = Base64Url.encode(ByteArray(16).also(random::nextBytes))

    fun newToken(): String = Base64Url.encode(ByteArray(32).also(random::nextBytes))

    /** Hash of the raw bearer token string, as the agent persists it (§7.2). */
    fun tokenHash(token: String): String =
        Hex.encode(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))

    /** Constant-time comparison of two token hashes, to avoid timing leaks on auth. */
    fun hashesEqual(a: String, b: String): Boolean = MessageDigest.isEqual(
        a.toByteArray(Charsets.UTF_8),
        b.toByteArray(Charsets.UTF_8),
    )
}
