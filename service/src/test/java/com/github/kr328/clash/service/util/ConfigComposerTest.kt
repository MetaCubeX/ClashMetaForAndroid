package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.ProxyHardeningMode
import kotlin.test.Test
import kotlin.test.assertTrue

class ConfigComposerTest {
    private val geo = GeoDataUrls(geoIp = "", geoSite = "", mmdb = "", asn = "")

    private val fetched = """
        mixed-port: 7890
        mode: rule
        proxies: []
        proxy-groups: []
        rules:
          - MATCH,DIRECT
    """.trimIndent()

    @Test fun empty_layer_leaves_subscription_rules_intact() {
        val out = ConfigComposer.compose(fetched, UserLayer(), geo, ProxyHardeningMode.Off)
        assertTrue(out.isNotBlank())
        assertTrue("MATCH,DIRECT" in out, "subscription rule must survive composition: $out")
    }

    @Test fun dns_hosts_override_is_composed_on_top() {
        val layer = UserLayer(
            dnsHosts = DnsHostsConfig(enable = true, hosts = mapOf("a.test" to "1.2.3.4")),
        )
        val out = ConfigComposer.compose(fetched, layer, geo, ProxyHardeningMode.Off)
        assertTrue("a.test" in out, "user host key must be present: $out")
        assertTrue("1.2.3.4" in out, "user host value must be present: $out")
        assertTrue("MATCH,DIRECT" in out, "subscription content must remain")
    }

    @Test fun strict_hardening_runs_last_on_composed_config() {
        val withListener = """
            mixed-port: 7890
            socks-port: 7891
            proxies: []
            rules:
              - MATCH,DIRECT
        """.trimIndent()
        val out = ConfigComposer.compose(withListener, UserLayer(), geo, ProxyHardeningMode.Strict)
        // Strict mode forces local listeners off (port 0); the composed result must reflect that.
        assertTrue("7891" !in out || "socks-port: 0" in out, "strict hardening should neutralize the socks listener: $out")
    }
}
