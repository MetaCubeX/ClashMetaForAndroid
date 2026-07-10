package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.ProxyHardeningMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class YamlHardenerTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun parse(yaml: String): Map<String, Any?> {
        return YamlFormatting.parseRootMap(yaml) ?: error("not a YAML map")
    }

    // -- in-memory hardenYaml ---------------------------------------------

    @Test
    fun strict_dropsListenersBlock() {
        val input = """
            listeners:
              - name: leaky
                type: socks
                listen: 0.0.0.0
                port: 1080
            proxies: []
        """.trimIndent()
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Strict)
        assertNotNull(hardened)
        val root = parse(hardened!!)
        assertNull("Strict must remove the entire listeners block", root["listeners"])
    }

    @Test
    fun strict_dropsQuotedListenersBlock() {
        val input = """
            "listeners":
              - name: leaky
                type: socks
                listen: 0.0.0.0
                port: 1080
            'proxies': []
        """.trimIndent()
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Strict)
        assertNotNull(hardened)
        val root = parse(hardened!!)
        assertNull("Strict must remove quoted listeners blocks", root["listeners"])
        assertFalse("Strict must strip the quoted listeners source text", hardened.contains("listen: 0.0.0.0"))
        assertTrue("following quoted top-level keys must survive", root.containsKey("proxies"))
    }

    @Test
    fun compat_rebindsListenersToLoopback() {
        val input = """
            listeners:
              - name: lan-socks
                type: socks
                listen: 0.0.0.0
                port: 1080
              - name: ipv6-any
                type: http
                listen: '[::]:8080'
        """.trimIndent()
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Compat)
        assertNotNull(hardened)
        val root = parse(hardened!!)
        val listeners = root["listeners"] as? List<*> ?: error("listeners must survive Compat")
        assertEquals("Compat keeps every listener, just rewrites listen", 2, listeners.size)
        val first = listeners[0] as Map<*, *>
        assertEquals("127.0.0.1", first["listen"])
        // Port and other fields untouched.
        assertEquals(1080, first["port"])
        assertEquals("lan-socks", first["name"])
    }

    @Test
    fun off_isFullNoop() {
        val input = """
            listeners:
              - name: leaky
                type: socks
                listen: 0.0.0.0
            allow-lan: true
            external-controller: 0.0.0.0:9090
        """.trimIndent()
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Off)
        // Off must return the original text, no parse, no transform.
        assertEquals(input, hardened)
    }

    @Test
    fun strict_forcesAllowLanFalseWhenExplicitlyTrue() {
        val input = """
            allow-lan: true
            proxies: []
        """.trimIndent()
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Strict)
        assertNotNull(hardened)
        assertEquals(false, parse(hardened!!)["allow-lan"])
    }

    @Test
    fun strict_leavesAbsentAllowLanAlone() {
        // mihomo defaults allow-lan to false when absent. Touching the file
        // just to write the same default would churn the signature cache
        // and produce a misleading "modified" event for the user.
        val input = """
            proxies: []
        """.trimIndent()
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Strict)
        assertEquals(input, hardened)
    }

    @Test
    fun strict_rewritesExternalControllerHostToLoopback() {
        val input = """
            external-controller: 0.0.0.0:9090
            proxies: []
        """.trimIndent()
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Strict)
        assertNotNull(hardened)
        assertEquals("127.0.0.1:9090", parse(hardened!!)["external-controller"])
    }

    @Test
    fun strict_rewritesExternalControllerBarePort() {
        val input = """
            external-controller: ':9090'
            proxies: []
        """.trimIndent()
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Strict)
        assertNotNull(hardened)
        assertEquals("127.0.0.1:9090", parse(hardened!!)["external-controller"])
    }

    @Test
    fun strict_keepsLoopbackExternalControllerUntouched() {
        val input = """
            external-controller: 127.0.0.1:9090
            proxies: []
        """.trimIndent()
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Strict)
        assertEquals(input, hardened)
    }

    @Test
    fun strict_rewritesBindAddressToLoopback() {
        val input = """
            bind-address: '*'
            proxies: []
        """.trimIndent()
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Strict)
        assertNotNull(hardened)
        assertEquals("127.0.0.1", parse(hardened!!)["bind-address"])
    }

    // -- SEC-1: global proxy ports stripped in Strict ---------------------

    @Test
    fun strict_stripsAllGlobalProxyPorts() {
        val input = """
            port: 7892
            socks-port: 7891
            mixed-port: 7890
            redir-port: 7893
            tproxy-port: 7894
            proxies: []
        """.trimIndent()
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Strict)
        assertNotNull(hardened)
        val root = parse(hardened!!)
        for (key in listOf("port", "socks-port", "mixed-port", "redir-port", "tproxy-port")) {
            assertNull("Strict must strip $key from the model", root[key])
            assertFalse("Strict must strip $key from the source text", hardened.contains(key))
        }
        // unrelated keys survive
        assertTrue(root.containsKey("proxies"))
    }

    @Test
    fun strict_stripsQuotedGlobalProxyPorts() {
        val input = """
            "mixed-port": 7890
            'socks-port': 7891
            proxies: []
        """.trimIndent()
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Strict)
        assertNotNull(hardened)
        val root = parse(hardened!!)
        assertNull(root["mixed-port"])
        assertNull(root["socks-port"])
        assertFalse(hardened.contains("mixed-port"))
        assertFalse(hardened.contains("socks-port"))
    }

    @Test
    fun strict_portStripIsIdempotent() {
        val input = "mixed-port: 7890\nproxies: []\n"
        val first = YamlHardener.hardenYaml(input, ProxyHardeningMode.Strict)
        assertNotNull(first)
        assertFalse(first!!.contains("mixed-port"))
        val second = YamlHardener.hardenYaml(first, ProxyHardeningMode.Strict)
        assertEquals("second pass on already-stripped YAML must be a no-op", first, second)
    }

    @Test
    fun compat_keepsGlobalProxyPorts() {
        val input = "mixed-port: 7890\nsocks-port: 7891\nproxies: []\n"
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Compat)
        // Compat must not strip global ports (loopback bind + RuntimeSocksAuth).
        assertEquals(7890, parse(hardened!!)["mixed-port"])
        assertEquals(7891, parse(hardened)["socks-port"])
    }

    @Test
    fun off_keepsGlobalProxyPorts() {
        val input = "mixed-port: 7890\nproxies: []\n"
        val hardened = YamlHardener.hardenYaml(input, ProxyHardeningMode.Off)
        assertEquals(input, hardened)
    }

    @Test
    fun isIdempotent_secondPassNoChange() {
        val dirty = """
            listeners:
              - name: leaky
                type: socks
                listen: 0.0.0.0
                port: 1080
            allow-lan: true
            external-controller: 0.0.0.0:9090
        """.trimIndent()
        val first = YamlHardener.hardenYaml(dirty, ProxyHardeningMode.Strict)
        assertNotNull(first)
        val second = YamlHardener.hardenYaml(first!!, ProxyHardeningMode.Strict)
        assertEquals("re-running hardener on clean YAML must be a no-op", first, second)
    }

    @Test
    fun returnsNullOnUnparseableInput() {
        assertNull(YamlHardener.hardenYaml("not a map - just a string", ProxyHardeningMode.Strict))
    }

    // -- on-disk hardenProfile + config.original.yaml ---------------------

    @Test
    fun hardenProfile_savesOriginalOnceAndRewritesConfig() {
        val profile = tmp.newFolder("profile-1")
        val original = """
            listeners:
              - name: leaky
                type: socks
                listen: 0.0.0.0
            proxies: []
        """.trimIndent()
        File(profile, "config.yaml").writeText(original)

        val changed = YamlHardener.hardenProfile(profile, ProxyHardeningMode.Strict)
        assertTrue("first run must report a change", changed)

        val originalFile = File(profile, "config.original.yaml")
        assertTrue("config.original.yaml must be created", originalFile.isFile)
        assertEquals(original, originalFile.readText())

        val cfgText = File(profile, "config.yaml").readText()
        assertFalse("listeners must be stripped on disk", cfgText.contains("listen: 0.0.0.0"))
    }

    @Test
    fun hardenProfile_keepsOriginalAcrossSubsequentRuns() {
        // First import saves original. A later subscription update writes a
        // fresh subscription YAML; YamlHardener must not overwrite the
        // pre-existing original.yaml — the user's first snapshot of the
        // subscription is the audit trail we never want to clobber.
        val profile = tmp.newFolder("profile-2")
        val firstImport = "listeners:\n  - name: a\n    listen: 0.0.0.0\n"
        File(profile, "config.yaml").writeText(firstImport)
        YamlHardener.hardenProfile(profile, ProxyHardeningMode.Strict)
        val originalAfterFirst = File(profile, "config.original.yaml").readText()
        assertEquals(firstImport, originalAfterFirst)

        // Simulate refresh: fresh subscription replaces config.yaml.
        val secondImport = "listeners:\n  - name: b\n    listen: 0.0.0.0\n"
        File(profile, "config.yaml").writeText(secondImport)
        YamlHardener.hardenProfile(profile, ProxyHardeningMode.Strict)
        assertEquals(
            "original.yaml must reflect the very first import, not later refreshes",
            firstImport,
            File(profile, "config.original.yaml").readText(),
        )
    }

    @Test
    fun hardenProfile_offModeIsNoOp() {
        val profile = tmp.newFolder("profile-off")
        val original = "listeners:\n  - name: leaky\n    listen: 0.0.0.0\n"
        File(profile, "config.yaml").writeText(original)

        val changed = YamlHardener.hardenProfile(profile, ProxyHardeningMode.Off)
        assertFalse("Off must not rewrite the file", changed)
        assertFalse(
            "Off must not create the original snapshot — there's nothing to back up",
            File(profile, "config.original.yaml").isFile,
        )
        assertEquals(original, File(profile, "config.yaml").readText())
    }
}
