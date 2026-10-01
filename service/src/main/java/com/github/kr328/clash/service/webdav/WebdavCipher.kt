package com.github.kr328.clash.service.webdav

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object WebdavCipher {
    private const val ALIAS = "clash_meta_webdav"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun seal(plain: String): String {
        if (plain.isEmpty()) return ""
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return "v0:" + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = ByteBuffer.allocate(4 + iv.size + body.size)
        packed.putInt(iv.size)
        packed.put(iv)
        packed.put(body)
        return "v1:" + Base64.encodeToString(packed.array(), Base64.NO_WRAP)
    }

    fun open(sealed: String): String {
        if (sealed.isEmpty()) return ""
        return try {
            when {
                sealed.startsWith("v0:") -> String(
                    Base64.decode(sealed.removePrefix("v0:"), Base64.DEFAULT),
                    Charsets.UTF_8,
                )
                sealed.startsWith("v1:") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    val packed = ByteBuffer.wrap(Base64.decode(sealed.removePrefix("v1:"), Base64.DEFAULT))
                    val ivLength = packed.int
                    val iv = ByteArray(ivLength)
                    packed.get(iv)
                    val body = ByteArray(packed.remaining())
                    packed.get(body)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                    String(cipher.doFinal(body), Charsets.UTF_8)
                }
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
