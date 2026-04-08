package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ConfigurationOverride
import java.security.SecureRandom
import java.util.Base64

/**
 * Generates per-process security credentials for local runtime surfaces.
 * Values are never persisted and are rotated on service restart.
 */
object RuntimeSocksAuth {
    private const val LOOPBACK_BIND = "127.0.0.1"
    private val secureRandom = SecureRandom()
    @Volatile private var sessionCredential: String? = null
    @Volatile private var sessionControllerSecret: String? = null

    /**
     * Apply a session-scoped runtime credential to [configuration].
     * @return true if configuration was changed.
     */
    fun applyTo(configuration: ConfigurationOverride): Boolean {
        var changed = false

        if (configuration.allowLan != false) {
            configuration.allowLan = false
            changed = true
        }

        val normalizedBind = normalizeBindAddress(configuration.bindAddress)
        if (configuration.bindAddress != normalizedBind) {
            configuration.bindAddress = normalizedBind
            changed = true
        }

        val normalizedController = normalizeControllerAddress(configuration.externalController)
        if (configuration.externalController != normalizedController) {
            configuration.externalController = normalizedController
            changed = true
        }

        val normalizedControllerTls = normalizeControllerAddress(configuration.externalControllerTLS)
        if (configuration.externalControllerTLS != normalizedControllerTls) {
            configuration.externalControllerTLS = normalizedControllerTls
            changed = true
        }

        val credential = sessionCredential ?: newCredential().also { sessionCredential = it }
        val current = configuration.authentication

        if (!(current?.size == 1 && current.firstOrNull() == credential)) {
            configuration.authentication = listOf(credential)
            changed = true
        }

        val hasController = !configuration.externalController.isNullOrBlank() ||
            !configuration.externalControllerTLS.isNullOrBlank()
        if (hasController && configuration.secret.isNullOrBlank()) {
            val secret = sessionControllerSecret ?: randomToken(24).also { sessionControllerSecret = it }
            configuration.secret = secret
            changed = true
        }

        return changed
    }

    private fun normalizeBindAddress(bindAddress: String?): String {
        if (bindAddress.isNullOrBlank()) return LOOPBACK_BIND
        if (bindAddress == "*" || bindAddress == "0.0.0.0" || bindAddress == "::") {
            return LOOPBACK_BIND
        }
        return bindAddress
    }

    private fun normalizeControllerAddress(address: String?): String? {
        if (address.isNullOrBlank()) return address
        return address
            .replace("0.0.0.0:", "$LOOPBACK_BIND:")
            .replace("[::]:", "$LOOPBACK_BIND:")
            .replace(":::", "$LOOPBACK_BIND:")
    }

    private fun newCredential(): String {
        val user = "cf_" + randomToken(8)
        val pass = randomToken(24)
        return "$user:$pass"
    }

    private fun randomToken(size: Int): String {
        val data = ByteArray(size)
        secureRandom.nextBytes(data)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }
}

