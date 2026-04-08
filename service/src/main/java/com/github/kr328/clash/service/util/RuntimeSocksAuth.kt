package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ConfigurationOverride
import java.security.SecureRandom
import java.util.Base64

/**
 * Generates per-process security credentials for local runtime surfaces.
 * Values are never persisted and are rotated on service restart.
 */
object RuntimeSocksAuth {
    private val secureRandom = SecureRandom()
    @Volatile private var sessionCredential: String? = null
    @Volatile private var sessionControllerSecret: String? = null

    /**
     * Apply a session-scoped runtime credential to [configuration].
     * @return true if configuration was changed.
     */
    fun applyTo(configuration: ConfigurationOverride): Boolean {
        var changed = false

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

