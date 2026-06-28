package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.ProxyHardeningMode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileComposerTest {
    private val dir: File = Files.createTempDirectory("profile-composer").toFile()
    private val geo = GeoDataUrls(geoIp = "", geoSite = "", mmdb = "", asn = "")

    @AfterTest fun cleanup() {
        dir.deleteRecursively()
    }

    private val subscription = """
        mixed-port: 7890
        proxies: []
        rule-providers:
          ip-check: {type: http, behavior: ipcidr, url: https://e/ip.yaml, path: ./ipc.yaml}
        dns:
          enable: true
          nameserver-policy:
            "rule-set:ip-check": https://dns.google/dns-query
        rules:
          - MATCH,DIRECT
    """.trimIndent() + "\n"

    @Test fun materialize_writes_composed_config_from_subscription_and_layer() {
        File(dir, ProfileComposer.SUBSCRIPTION_FILE).writeText(subscription)
        val layer = UserLayer(dnsHosts = DnsHostsConfig(enable = true, hosts = mapOf("a.test" to "1.2.3.4")))

        val ok = ProfileComposer.materialize(dir, layer, geo, ProxyHardeningMode.Off)
        assertTrue(ok)

        val config = File(dir, ProfileComposer.CONFIG_FILE)
        assertTrue(config.isFile, "config.yaml must be written")
        val text = config.readText()
        assertTrue("a.test" in text, "user override composed in")
        assertTrue("ip-check" in text, "subscription provider survives (no GC)")
        // subscription.yaml stays untouched (the canonical base).
        assertTrue("a.test" !in File(dir, ProfileComposer.SUBSCRIPTION_FILE).readText(), "subscription must stay raw")
    }

    @Test fun materialize_is_idempotent() {
        File(dir, ProfileComposer.SUBSCRIPTION_FILE).writeText(subscription)
        val layer = UserLayer(dnsHosts = DnsHostsConfig(enable = true, hosts = mapOf("a.test" to "1.2.3.4")))
        ProfileComposer.materialize(dir, layer, geo, ProxyHardeningMode.Off)
        val first = File(dir, ProfileComposer.CONFIG_FILE).readText()
        ProfileComposer.materialize(dir, layer, geo, ProxyHardeningMode.Off)
        val second = File(dir, ProfileComposer.CONFIG_FILE).readText()
        assertTrue(first == second, "re-materialize from canonical subscription must be stable")
    }

    @Test fun materialize_without_subscription_returns_false() {
        assertFalse(ProfileComposer.materialize(dir, UserLayer(), geo, ProxyHardeningMode.Off))
    }
}
