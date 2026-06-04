package com.github.kr328.clash.service.util

import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Verifies process-wide JVM proxy properties are cleared so traffic can't leak around the VPN. */
class ProxyPropertyGuardTest {

    private val keys = listOf(
        "http.proxyHost", "http.proxyPort",
        "https.proxyHost", "https.proxyPort",
        "socksProxyHost", "socksProxyPort",
        "java.net.useSystemProxies",
    )

    private lateinit var saved: Map<String, String?>

    @Before
    fun saveProps() {
        saved = keys.associateWith { System.getProperty(it) }
    }

    @After
    fun restoreProps() {
        for (k in keys) {
            val v = saved[k]
            if (v == null) System.clearProperty(k) else System.setProperty(k, v)
        }
    }

    @Test
    fun clearsAllManagedProxyProperties() {
        keys.forEach { System.setProperty(it, "leak") }

        ProxyPropertyGuard.clearGlobalProxyProperties()

        keys.forEach { assertNull("still set: $it", System.getProperty(it)) }
    }

    @Test
    fun noOpWhenNothingSet_andLeavesUnmanagedPropsAlone() {
        keys.forEach { System.clearProperty(it) }
        System.setProperty("user.defined.keep", "keep-me")

        ProxyPropertyGuard.clearGlobalProxyProperties()

        keys.forEach { assertNull(System.getProperty(it)) }
        assertEquals("keep-me", System.getProperty("user.defined.keep"))
        System.clearProperty("user.defined.keep")
    }
}
