package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.ProxyHardeningMode
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layer-1 fidelity net for [ConfigComposer] (config-overlay-architecture, Group 3.4). The
 * invariant: composition only ADDS the user layer on top of the fetched subscription — it must
 * NEVER drop anything the subscription declares. There is no GC and no reconcile here, so the bug
 * class that hit production (`gcUnusedRuleProviders` dropping `ip-check`, referenced only in
 * `dns.nameserver-policy`, → "not found rule-set: ip-check" on update) is structurally impossible.
 *
 * Like [SubscriptionMergeFidelityTest], this is a pure-JVM net; the real mihomo engine is the
 * ultimate oracle but lives in the Go `native/snapshot` layer + the runtime gate (Group 1).
 */
class ConfigCompositionFidelityTest {
    private val geo = GeoDataUrls(geoIp = "", geoSite = "", mmdb = "", asn = "")

    private fun keysOf(yaml: String, block: String): Set<String> =
        ((MihomoConfigDocument.parseOrEmpty(yaml).root[block] as? Map<*, *>)?.keys ?: emptySet<Any?>())
            .mapNotNull { it?.toString() }.toSet()

    /** Heavy template shape: a rule-provider referenced ONLY in dns.nameserver-policy (the prod bug),
     *  plus space-in-name and logical-rule references. */
    private val heavyFetched = """
        proxies:
          - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}
        proxy-groups:
          - {name: G, type: select, proxies: [p1]}
        rule-providers:
          ip-check:   {type: http, behavior: ipcidr, url: https://e/ip.yaml, path: ./ipc.yaml}
          Ad Block:   {type: http, behavior: domain, url: https://e/ad.yaml, path: ./ad.yaml}
          geosite-ru: {type: http, behavior: domain, url: https://e/ru.yaml, path: ./ru.yaml}
        dns:
          enable: true
          nameserver-policy:
            "rule-set:ip-check": https://dns.google/dns-query
        rules:
          - RULE-SET,Ad Block,REJECT
          - AND,((RULE-SET,geosite-ru),(NETWORK,UDP)),DIRECT
          - MATCH,G
    """.trimIndent() + "\n"

    @Test fun composition_never_drops_a_fetched_rule_provider() {
        // A user layer that DOES route rules through RuleMapper (the heaviest path) plus a dns edit.
        val layer = UserLayer(
            rules = RuleState(
                rules = listOf(RuleItem(id = "u1", type = "DOMAIN", value = "mine.example", policy = "DIRECT")),
            ),
            dnsHosts = DnsHostsConfig(enable = true, hosts = mapOf("a.test" to "1.2.3.4")),
        )
        val out = ConfigComposer.compose(heavyFetched, layer, geo, ProxyHardeningMode.Off)

        val declared = keysOf(heavyFetched, "rule-providers")
        val survived = keysOf(out, "rule-providers")
        for (key in declared) {
            assertTrue("fetched rule-provider '$key' must survive composition (no GC)", key in survived)
        }
        // The exact prod regression: ip-check is referenced ONLY by dns.nameserver-policy.
        assertTrue("ip-check (dns-only ref) must survive composition", "ip-check" in survived)
        // Subscription rules remain, and the user's rule landed.
        assertTrue("subscription rule must remain", "Ad Block" in out)
        assertTrue("user rule must be composed in", "mine.example" in out)
        assertTrue("user dns/host override applied", "a.test" in out)
    }

    @Test fun empty_layer_returns_subscription_essentially_untouched() {
        val out = ConfigComposer.compose(heavyFetched, UserLayer(), geo, ProxyHardeningMode.Off)
        val declared = keysOf(heavyFetched, "rule-providers")
        val survived = keysOf(out, "rule-providers")
        assertTrue("empty layer must keep every provider", survived.containsAll(declared))
    }
}
