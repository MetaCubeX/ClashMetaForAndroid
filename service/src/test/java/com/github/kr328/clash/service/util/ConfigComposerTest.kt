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

    @Test fun rule_providers_layer_is_unioned_in() {
        val layer = UserLayer(
            ruleProviders = "myset:\n  type: http\n  behavior: domain\n  url: https://e/m.yaml\n  path: ./m.yaml",
        )
        val out = ConfigComposer.compose(fetched, layer, geo, ProxyHardeningMode.Off)
        assertTrue("myset" in out, "user rule-provider must be composed in: $out")
        assertTrue("MATCH,DIRECT" in out, "subscription content remains")
    }

    @Test fun relay_group_is_appended() {
        val layer = UserLayer(relayGroups = listOf(RelayGroup(name = "MyRelay", providerKeys = listOf("provA"))))
        val out = ConfigComposer.compose(fetched, layer, geo, ProxyHardeningMode.Off)
        assertTrue("MyRelay" in out, "relay group must be appended: $out")
    }

    @Test fun proxy_chain_dialer_is_composed_onto_config_proxies() {
        val withProxy = """
            mixed-port: 7890
            proxies:
              - name: US-1
                type: ss
                server: 1.2.3.4
                port: 8388
                cipher: aes-128-gcm
                password: pass
              - name: JP-2
                type: ss
                server: 5.6.7.8
                port: 8388
                cipher: aes-128-gcm
                password: pass
            rules:
              - MATCH,DIRECT
        """.trimIndent()
        val layer = UserLayer(proxyChain = mapOf("US-1" to "JP-2"))
        val out = ConfigComposer.compose(withProxy, layer, geo, ProxyHardeningMode.Off)
        assertTrue("dialer-proxy: JP-2" in out, "proxy-chain dialer must be applied to US-1: $out")
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
