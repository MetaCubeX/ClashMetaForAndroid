package com.github.kr328.clash.companion.protocol

import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * TLS certificate fingerprint (PROTOCOL.md §3.3):
 * `fp = lowercasehex(SHA-256(DER(leaf certificate)))` — 64 hex chars, no colons, no 0x.
 *
 * This is the pinning identity carried out-of-band in the QR/pairing payload and the discovery
 * TXT record; both sides compare it against the certificate actually served.
 */
object Fingerprint {
    fun of(der: ByteArray): String =
        Hex.encode(MessageDigest.getInstance("SHA-256").digest(der))

    fun of(certificate: X509Certificate): String = of(certificate.encoded)
}
