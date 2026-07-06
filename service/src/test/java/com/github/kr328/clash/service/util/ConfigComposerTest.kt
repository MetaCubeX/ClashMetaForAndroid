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

    @Test fun url_test_exclude_filter_survives_composition() {
        // Repro of the user report: a url-test group with include-all + exclude-filter listing a flag
        // emoji + codes. The engine applies exclude-filter correctly (proven by the Go oracle), so if
        // the bug is real the field must be lost/mangled here, before the engine sees it.
        val fetched = """
            mixed-port: 7890
            mode: rule
            proxies:
              - {name: "🇩🇪 Germany", type: ss, server: 1.1.1.1, port: 443, cipher: aes-128-gcm, password: a}
              - {name: "🇸🇪 Sweden", type: ss, server: 1.1.1.6, port: 443, cipher: aes-128-gcm, password: a}
            proxy-groups:
              - name: "⚡ Fastest For All"
                type: url-test
                url: https://www.gstatic.com/generate_204
                interval: 300
                tolerance: 300
                lazy: true
                include-all: true
                exclude-filter: '🇸🇪|SE|Sweden'
                proxies: []
            rules:
              - MATCH,⚡ Fastest For All
        """.trimIndent()
        for (mode in listOf(ProxyHardeningMode.Off, ProxyHardeningMode.Strict, ProxyHardeningMode.Compat)) {
            val out = ConfigComposer.compose(fetched, UserLayer(), geo, mode)
            assertTrue("exclude-filter" in out, "[$mode] exclude-filter key must survive composition: $out")
            assertTrue("Sweden" in out, "[$mode] exclude-filter value must survive intact: $out")
            assertTrue("🇸🇪" in out, "[$mode] flag emoji in exclude-filter must survive: $out")
            assertTrue("include-all" in out, "[$mode] include-all must survive: $out")
        }
    }

    @Test fun url_test_exclude_filter_survives_with_remnawave_quirks() {
        // Faithful to the reported subscription shape: a nonstandard `remnawave:` nested map inside the
        // group + a `proxies:` list that is comment-only (parses to null). If our block-patcher/hardener
        // mangles the group here, the engine gets a broken exclude-filter — reproducing the on-device
        // "not excluded even when connected" case.
        val fetched = """
            mixed-port: 7890
            mode: rule
            proxies:
              - {name: "🇩🇪 Germany", type: ss, server: 1.1.1.1, port: 443, cipher: aes-128-gcm, password: a}
              - {name: "🇸🇪 Sweden", type: ss, server: 1.1.1.6, port: 443, cipher: aes-128-gcm, password: a}
            proxy-groups:
              - name: "⚡ Fastest For All"
                type: url-test
                url: https://www.gstatic.com/generate_204
                interval: 300
                tolerance: 300
                lazy: true
                include-all: true
                exclude-filter: '🇸🇪|SE|Sweden'
                remnawave:
                  include-proxies: false
                proxies:
                  # LEAVE THIS LINE!
            rules:
              - MATCH,⚡ Fastest For All
        """.trimIndent()
        for (mode in listOf(ProxyHardeningMode.Off, ProxyHardeningMode.Strict, ProxyHardeningMode.Compat)) {
            val out = ConfigComposer.compose(fetched, UserLayer(), geo, mode)
            assertTrue("exclude-filter" in out, "[$mode] exclude-filter must survive: $out")
            assertTrue("Sweden" in out, "[$mode] exclude value must survive: $out")
            assertTrue("🇸🇪" in out, "[$mode] flag emoji must survive: $out")
            assertTrue("include-all" in out, "[$mode] include-all must survive: $out")
        }
    }

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
