package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ConfigurationOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session hardening of the runtime config: local listeners must never be LAN-reachable or
 * unauthenticated, and the external controller must be clamped to loopback. These are leak-
 * prevention guarantees, so they are asserted directly.
 */
class RuntimeSocksAuthTest {

    private val credentialFormat = Regex("""^cf_[A-Za-z0-9_-]+:[A-Za-z0-9_-]+$""")

    @Test
    fun forcesAllowLanOff() {
        val config = ConfigurationOverride().apply { allowLan = true }
        val changed = RuntimeSocksAuth.applyTo(config)
        assertTrue(changed)
        assertEquals(false, config.allowLan)
    }

    @Test
    fun normalizesWildcardBindToLoopback() {
        for (wild in listOf(null, "", "0.0.0.0", "*", "::")) {
            val config = ConfigurationOverride().apply { bindAddress = wild }
            RuntimeSocksAuth.applyTo(config)
            assertEquals("wildcard '$wild' must clamp", "127.0.0.1", config.bindAddress)
        }
    }

    @Test
    fun keepsSpecificBindAddress() {
        val config = ConfigurationOverride().apply { bindAddress = "192.168.1.5" }
        RuntimeSocksAuth.applyTo(config)
        assertEquals("192.168.1.5", config.bindAddress)
    }

    @Test
    fun clampsExternalControllerToLoopback() {
        for (addr in listOf("0.0.0.0:9090", "[::]:9090", "1.2.3.4:9090")) {
            val config = ConfigurationOverride().apply { externalController = addr }
            RuntimeSocksAuth.applyTo(config)
            assertEquals("'$addr' must clamp", "127.0.0.1:9090", config.externalController)
        }
    }

    @Test
    fun keepsLoopbackControllerAndNull() {
        val loopback = ConfigurationOverride().apply { externalController = "127.0.0.1:9090" }
        RuntimeSocksAuth.applyTo(loopback)
        assertEquals("127.0.0.1:9090", loopback.externalController)

        val none = ConfigurationOverride()
        RuntimeSocksAuth.applyTo(none)
        assertNull(none.externalController)
    }

    @Test
    fun setsSingleSessionAuthenticationCredential() {
        val config = ConfigurationOverride()
        RuntimeSocksAuth.applyTo(config)
        val auth = config.authentication
        assertEquals(1, auth?.size)
        assertTrue("credential format: ${auth?.firstOrNull()}", credentialFormat.matches(auth!!.first()))
    }

    @Test
    fun generatesControllerSecretOnlyWhenControllerPresent() {
        val withController = ConfigurationOverride().apply { externalController = "0.0.0.0:9090" }
        RuntimeSocksAuth.applyTo(withController)
        assertTrue("secret must be set when a controller is exposed", !withController.secret.isNullOrBlank())

        val noController = ConfigurationOverride()
        RuntimeSocksAuth.applyTo(noController)
        assertNull(noController.secret)
    }

    @Test
    fun isIdempotent_secondApplyMakesNoChange() {
        val config = ConfigurationOverride().apply {
            allowLan = true
            bindAddress = "0.0.0.0"
            externalController = "0.0.0.0:9090"
        }
        assertTrue(RuntimeSocksAuth.applyTo(config))
        // Everything is now normalized and the session credential/secret are stable per process.
        assertFalse(RuntimeSocksAuth.applyTo(config))
    }
}
