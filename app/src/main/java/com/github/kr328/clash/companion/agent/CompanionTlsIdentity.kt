package com.github.kr328.clash.companion.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.companion.protocol.Fingerprint
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.security.auth.x500.X500Principal

/**
 * The agent's self-signed TLS identity (PROTOCOL.md §5.1): ECDSA P-256, long validity, private
 * key never leaving the device. The [fingerprint] (§3.3) is the out-of-band pinning anchor placed
 * in the QR and the discovery TXT.
 *
 * Primary path keeps the key in **AndroidKeyStore** (hardware-backed, non-exportable); when the
 * keypair is generated there the keystore mints the matching self-signed certificate for us. If
 * that path can't back the TLS handshake on a given device, we fall back to an app-private PKCS12
 * keystore with a BouncyCastle-minted certificate. Either way the served certificate, and thus the
 * fingerprint, is stable across restarts.
 */
class CompanionTlsIdentity private constructor(
    val certificate: X509Certificate,
    private val keyManagers: Array<KeyManager>,
) {
    val fingerprint: String = Fingerprint.of(certificate)

    /** SSLServerSocketFactory for NanoHTTPD, serving exactly this pinned certificate. */
    fun serverSocketFactory(): SSLServerSocketFactory {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(keyManagers, null, SecureRandom())
        return ctx.serverSocketFactory
    }

    companion object {
        private const val ALIAS = "clashctl-agent"
        private const val SUBJECT = "CN=clashctl-agent"
        private val VALIDITY_MS = 3650L * 24 * 60 * 60 * 1000 // ~10 years

        fun load(context: Context): CompanionTlsIdentity {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    return fromAndroidKeyStore()
                } catch (t: Throwable) {
                    Log.w("Companion: AndroidKeyStore TLS unavailable, falling back to PKCS12: ${t.message}")
                }
            }
            return fromPkcs12(context)
        }

        // --- AndroidKeyStore (preferred) -------------------------------------------------------

        @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.M)
        private fun fromAndroidKeyStore(): CompanionTlsIdentity {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias(ALIAS)) generateAndroidKeyStoreKey()

            val cert = ks.getCertificate(ALIAS) as X509Certificate
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, null) // AndroidKeyStore entries are unprotected by a password
            return CompanionTlsIdentity(cert, kmf.keyManagers)
        }

        @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.M)
        private fun generateAndroidKeyStoreKey() {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore",
            )
            val now = System.currentTimeMillis()
            val spec = KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                // Allow the digests TLS 1.2/1.3 may negotiate for ECDSA signatures.
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA384,
                    KeyProperties.DIGEST_SHA512,
                    KeyProperties.DIGEST_NONE,
                )
                .setCertificateSubject(X500Principal(SUBJECT))
                .setCertificateSerialNumber(BigInteger.valueOf(now))
                .setCertificateNotBefore(Date(now - 24L * 60 * 60 * 1000))
                .setCertificateNotAfter(Date(now + VALIDITY_MS))
                .build()
            kpg.initialize(spec)
            kpg.generateKeyPair()
        }

        // --- PKCS12 fallback (app-private) -----------------------------------------------------

        private fun fromPkcs12(context: Context): CompanionTlsIdentity {
            val file = File(context.filesDir, "companion/agent_tls.p12")
            val ks = KeyStore.getInstance("PKCS12")

            if (file.isFile) {
                file.inputStream().use { ks.load(it, PASSPHRASE) }
            } else {
                ks.load(null, PASSPHRASE)
                val (cert, privateKey) = mintSelfSigned()
                ks.setKeyEntry(ALIAS, privateKey, PASSPHRASE, arrayOf(cert))
                file.parentFile?.mkdirs()
                file.outputStream().use { ks.store(it, PASSPHRASE) }
            }

            val cert = ks.getCertificate(ALIAS) as X509Certificate
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, PASSPHRASE)
            return CompanionTlsIdentity(cert, kmf.keyManagers)
        }

        private fun mintSelfSigned(): Pair<X509Certificate, java.security.PrivateKey> {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val keyPair = kpg.generateKeyPair()

            val now = System.currentTimeMillis()
            val subject = X500Principal(SUBJECT)
            val builder = JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now),
                Date(now - 24L * 60 * 60 * 1000),
                Date(now + VALIDITY_MS),
                subject,
                keyPair.public,
            )
            val signer = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
            val holder = builder.build(signer)
            val cert = JcaX509CertificateConverter().getCertificate(holder)
            return cert to keyPair.private
        }

        // App-private file; the passphrase is not a security boundary (sandbox is), it only
        // satisfies the PKCS12 API. The private key never leaves the app sandbox.
        private val PASSPHRASE = "clashctl".toCharArray()
    }
}
