package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionUpdateMergeTest {

    private fun snapshot(
        rules: List<String> = emptyList(),
        ruleProviders: Map<String, JsonObject> = emptyMap(),
        proxyProviders: Map<String, JsonObject> = emptyMap(),
        proxyGroups: List<JsonObject> = emptyList(),
        listeners: List<JsonObject> = emptyList(),
    ) = ProfileSnapshot(
        rules = rules,
        ruleProviders = ruleProviders,
        proxyProviders = proxyProviders,
        proxyGroups = proxyGroups,
        listeners = listeners,
    )

    private fun jsonObj(vararg pairs: Pair<String, JsonElement>): JsonObject =
        JsonObject(pairs.toMap())

    @Test
    fun orphanedRule_droppedWhenPolicyGroupGone_updateStaysValid() {
        // The screenshot bug: a preserved manual rule points at a group the NEW
        // subscription removed -> engine would reject the whole config. We drop
        // the dead rule (and report it) so the update still applies.
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(rules = listOf(
                "DOMAIN-SUFFIX,pornhub.com,Automatic", // group "Automatic" no longer exists
                "DOMAIN,keep.com,DIRECT",              // builtin policy -> survives
            )),
        )
        val fetched = """
            proxies:
              - {name: n1, type: socks5, server: 127.0.0.1, port: 1080}
            proxy-groups:
              - {name: Manual, type: select, proxies: [n1]}
            rules:
              - MATCH,Manual
        """.trimIndent() + "\n"

        val dropped = mutableListOf<String>()
        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved, null) { dropped += it }

        assertTrue("dangling rule reported: $dropped", dropped.any { it.contains("Automatic") })
        assertFalse("dangling rule removed from config", merged.contains("Automatic"))
        assertTrue("valid-policy rule kept", merged.contains("keep.com"))
        assertTrue("fetched rule intact", merged.contains("MATCH,Manual"))
    }

    @Test
    fun tunnels_preserved_only_when_managed() {
        val tunnelsJson = kotlinx.serialization.json.Json.parseToJsonElement(
            """[{"network":["tcp"],"address":"127.0.0.1:6553","target":"1.1.1.1:53","proxy":"G"}]""",
        ) as kotlinx.serialization.json.JsonArray
        val snap = ProfileSnapshot(rules = listOf("MATCH,G"), tunnels = tunnelsJson)

        assertNull(SubscriptionUpdateMerge.extractPreserved(snap, includeTunnels = false).tunnels)
        val on = SubscriptionUpdateMerge.extractPreserved(snap, includeTunnels = true)
        assertNotNull(on.tunnels)

        // Managed tunnels REPLACE the fetched ones on refresh.
        val fetched = "proxies:\n  - {name: G, type: socks5, server: 127.0.0.1, port: 1080}\n" +
            "tunnels:\n  - {network: [udp], address: 127.0.0.1:9, target: old.example:1, proxy: G}\n" +
            "rules:\n  - MATCH,G\n"
        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, on)
        assertTrue("preserved tunnel applied: $merged", merged.contains("1.1.1.1:53"))
        assertFalse("fetched tunnel replaced", merged.contains("old.example:1"))
    }

    @Test
    fun gc_keepsProxyProvidersUnderIncludeAll() {
        val fetched = """
            proxies:
              - {name: n1, type: socks5, server: 127.0.0.1, port: 1080}
            proxy-providers:
              provA: {type: http, url: https://e/a.yaml, path: ./a.yaml}
              provB: {type: http, url: https://e/b.yaml, path: ./b.yaml}
            proxy-groups:
              - {name: G, type: select, include-all-providers: true}
            rules:
              - MATCH,G
        """.trimIndent() + "\n"
        val preserved = SubscriptionUpdateMerge.extractPreserved(snapshot(rules = listOf("DOMAIN,k.local,DIRECT")))
        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved)
        assertTrue("include-all keeps provA", merged.contains("provA"))
        assertTrue("include-all keeps provB", merged.contains("provB"))
    }

    @Test
    fun gc_keepsProxyProviderViaUse_dropsUnused() {
        val fetched = """
            proxies:
              - {name: n1, type: socks5, server: 127.0.0.1, port: 1080}
            proxy-providers:
              used: {type: http, url: https://e/u.yaml, path: ./u.yaml}
              unused: {type: http, url: https://e/z.yaml, path: ./z.yaml}
            proxy-groups:
              - {name: G, type: select, use: [used]}
            rules:
              - MATCH,G
        """.trimIndent() + "\n"
        val preserved = SubscriptionUpdateMerge.extractPreserved(snapshot(rules = listOf("DOMAIN,k.local,DIRECT")))
        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved)
        assertTrue("used provider survives", merged.contains("used"))
        assertFalse("unused provider dropped", merged.contains("unused"))
    }

    @Test
    fun extractPreserved_emptySnapshotReturnsEmpty() {
        val preserved = SubscriptionUpdateMerge.extractPreserved(snapshot())
        assertTrue(preserved.isEmpty())
    }

    @Test
    fun extractPreserved_carriesLogicalRulesIntact() {
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(rules = listOf(
                "DOMAIN,example.com,DIRECT",
                "AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT",
                "MATCH,GLOBAL",
            )),
        )

        val rules = preserved.rules
        assertNotNull(rules)
        @Suppress("UNCHECKED_CAST")
        val list = rules as List<Any?>
        assertEquals(3, list.size)
        // Critical: AND rule comes through as ONE whole string. Pre-Path-B
        // this was split-by-comma into "((NETWORK", "UDP)", ... and then
        // re-assembled into "AND,((NETWORK,UDP)" - the corruption that
        // produced "proxy [UDP] not found" downstream.
        assertEquals("AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT", list[1])
    }

    @Test
    fun mergeAfterFetch_keepsRuleProviderReferencedOnlyInsideLogicalRule() {
        // Regression: a RULE-SET reference nested in a logical (AND/OR/NOT) rule
        // was missed by the GC, so the rule-provider got dropped on update and
        // the next subscription update failed ("Could not update: rules[n] [OR,
        // ((RULE-SET,whatsapp_domains),...").
        val fetched = """
            proxies:
              - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}
            rule-providers:
              whatsapp_domains:
                type: http
                behavior: domain
                url: https://example.com/wa.yaml
                path: ./wa.yaml
              unused_set:
                type: http
                behavior: domain
                url: https://example.com/unused.yaml
                path: ./unused.yaml
            rules:
              - OR,((RULE-SET,whatsapp_domains),(NETWORK,UDP)),DIRECT
              - MATCH,p1
        """.trimIndent() + "\n"

        // Non-empty preserved overlay so the merge (and GC) actually runs.
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(rules = listOf("DOMAIN,foo.com,DIRECT")),
        )

        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved)

        assertTrue(
            "rule-provider referenced inside the OR rule must survive",
            merged.contains("whatsapp_domains"),
        )
        // The genuinely-unreferenced provider is still GC'd.
        assertFalse("unreferenced provider should be dropped", merged.contains("unused_set"))
    }

    @Test
    fun gc_keepsRuleProvidersReferencedInEveryRuleForm() {
        // RULE-SET can appear plainly OR nested inside AND/OR/NOT logical rules.
        // The GC must recognize ALL of them or it drops a live provider and the
        // next update fails.
        val fetched = """
            proxies:
              - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}
            rule-providers:
              setPlain:   {type: http, behavior: domain, url: https://e/x.yaml, path: ./a.yaml}
              setAnd:     {type: http, behavior: domain, url: https://e/x.yaml, path: ./b.yaml}
              setOr:      {type: http, behavior: domain, url: https://e/x.yaml, path: ./c.yaml}
              setNot:     {type: http, behavior: ipcidr, url: https://e/x.yaml, path: ./d.yaml}
              setNested:  {type: http, behavior: domain, url: https://e/x.yaml, path: ./e.yaml}
              setUnused:  {type: http, behavior: domain, url: https://e/x.yaml, path: ./z.yaml}
            rules:
              - RULE-SET,setPlain,DIRECT
              - AND,((RULE-SET,setAnd),(NETWORK,UDP)),DIRECT
              - OR,((RULE-SET,setOr),(DST-PORT,443)),PROXY
              - NOT,((RULE-SET,setNot)),REJECT
              - OR,((AND,((RULE-SET,setNested),(DST-PORT,80))),(DOMAIN,a.com)),PROXY
              - MATCH,p1
        """.trimIndent() + "\n"

        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(rules = listOf("DOMAIN,kept.local,DIRECT")),
        )
        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved)

        for (name in listOf("setPlain", "setAnd", "setOr", "setNot", "setNested")) {
            assertTrue("$name (referenced) must survive GC", merged.contains(name))
        }
        assertFalse("setUnused (unreferenced) should be dropped", merged.contains("setUnused"))
    }

    @Test
    fun mergeRules_preservesManualTypesButNotSubscriptionOwned() {
        // Manual host/IP rules a user added locally must survive a refresh;
        // subscription-owned types (RULE-SET/GEOSITE/GEOIP/MATCH/AND/OR/NOT/
        // SUB-RULE) must NOT be resurrected — the new subscription owns them.
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(rules = listOf(
                "DOMAIN-SUFFIX,manual.example,DIRECT",
                "IP-CIDR,10.0.0.0/8,DIRECT,no-resolve",
                "PROCESS-NAME,curl,DIRECT",
                "RULE-SET,oldset,PROXY",
                "GEOIP,CN,DIRECT",
                "GEOSITE,category-ads,REJECT",
                "MATCH,OLD",
            )),
        )
        val fetched = """
            proxies:
              - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}
            proxy-groups:
              - {name: PROXY, type: select, proxies: [p1]}
            rules:
              - DOMAIN,fetched.example,PROXY
              - MATCH,p1
        """.trimIndent() + "\n"

        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved)

        // Manual entries preserved.
        assertTrue(merged.contains("manual.example"))
        assertTrue(merged.contains("10.0.0.0/8"))
        assertTrue(merged.contains("PROCESS-NAME,curl"))
        // Subscription-owned NOT carried over.
        assertFalse(merged.contains("oldset"))
        assertFalse(merged.contains("GEOIP,CN"))
        assertFalse(merged.contains("category-ads"))
        assertFalse(merged.contains("MATCH,OLD"))
        // Fetched rules intact.
        assertTrue(merged.contains("fetched.example"))
    }

    @Test
    fun extractPreserved_convertsRuleProvidersWithMihomoNumberTypes() {
        // mihomo marshals YAML int -> JSON number (not string), so a real
        // snapshot from the engine carries interval as a JsonPrimitive(Long).
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(ruleProviders = mapOf(
                "myrules" to jsonObj(
                    "type" to JsonPrimitive("http"),
                    "behavior" to JsonPrimitive("classical"),
                    "url" to JsonPrimitive("https://example.com/rules.yaml"),
                    "interval" to JsonPrimitive(86400),
                ),
            )),
        )

        @Suppress("UNCHECKED_CAST")
        val map = preserved.ruleProviders as Map<String, Any?>
        assertEquals(1, map.size)

        @Suppress("UNCHECKED_CAST")
        val body = map["myrules"] as Map<String, Any?>
        assertEquals("http", body["type"])
        assertEquals("classical", body["behavior"])
        assertEquals("https://example.com/rules.yaml", body["url"])
        // Numeric primitives come back as Long, ready for SnakeYAML dump.
        assertEquals(86400L, body["interval"])
    }

    @Test
    fun extractPreserved_convertsQuotedNumericStringsBackToString() {
        // If a user wrote interval as a YAML quoted string ("86400"), mihomo
        // marshals it as a JSON string. We must preserve that as string so
        // round-trip is byte-faithful.
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(ruleProviders = mapOf(
                "myrules" to jsonObj(
                    "type" to JsonPrimitive("http"),
                    "interval" to JsonPrimitive("86400"),
                ),
            )),
        )

        @Suppress("UNCHECKED_CAST")
        val body = (preserved.ruleProviders as Map<String, Any?>)["myrules"] as Map<String, Any?>
        assertEquals("86400", body["interval"])
    }

    @Test
    fun extractPreserved_convertsProxyGroupsToYamlShape() {
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(proxyGroups = listOf(
                jsonObj(
                    "name" to JsonPrimitive("MainGroup"),
                    "type" to JsonPrimitive("select"),
                ),
            )),
        )

        @Suppress("UNCHECKED_CAST")
        val groups = preserved.proxyGroups as List<Any?>
        assertEquals(1, groups.size)

        @Suppress("UNCHECKED_CAST")
        val group = groups[0] as Map<String, Any?>
        assertEquals("MainGroup", group["name"])
        assertEquals("select", group["type"])
    }

    @Test
    fun extractPreserved_omittedSectionsStayNull() {
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(rules = listOf("MATCH,DIRECT")),
        )
        assertNotNull(preserved.rules)
        assertNull(preserved.ruleProviders)
        assertNull(preserved.proxyProviders)
        assertNull(preserved.proxyGroups)
    }

    @Test
    fun mergeAfterFetch_dropsUnreferencedRuleProviders() {
        // Preserved overlay carries a rule-provider 'apple' from the old
        // subscription. The new subscription no longer references it via any
        // RULE-SET rule, so the merge should NOT carry it forward — mihomo
        // would otherwise refetch it every interval for nothing.
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(
                rules = listOf("DOMAIN,manual.example.com,DIRECT"),
                ruleProviders = mapOf(
                    "apple" to jsonObj(
                        "type" to JsonPrimitive("http"),
                        "behavior" to JsonPrimitive("domain"),
                        "format" to JsonPrimitive("mrs"),
                        "url" to JsonPrimitive("https://example.com/apple.mrs"),
                    ),
                    "used-by-fetched" to jsonObj(
                        "type" to JsonPrimitive("http"),
                        "behavior" to JsonPrimitive("classical"),
                        "url" to JsonPrimitive("https://example.com/used.yaml"),
                    ),
                ),
            ),
        )

        val fetched = """
            mixed-port: 7890
            proxies:
              - name: node-a
                type: ss
                server: 1.2.3.4
                port: 8388
                cipher: aes-256-gcm
                password: x
            rule-providers:
              used-by-fetched:
                type: http
                behavior: classical
                url: "https://example.com/used.yaml"
            rules:
              - RULE-SET,used-by-fetched,DIRECT
              - MATCH,GLOBAL
        """.trimIndent()

        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved)

        assertTrue(
            "kept provider must survive: $merged",
            merged.contains("used-by-fetched"),
        )
        assertFalse(
            "unreferenced apple.mrs must be GC'd: $merged",
            merged.contains("apple.mrs"),
        )
        assertFalse(
            "unreferenced 'apple:' key must be gone: $merged",
            merged.contains("apple:"),
        )
    }

    @Test
    fun mergeAfterFetch_dropsUnusedProxyProviders() {
        // sub-old is preserved but no proxy-group references it; should be
        // dropped. sub-new is referenced by GROUP.use → must survive.
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(
                proxyProviders = mapOf(
                    "sub-old" to jsonObj(
                        "type" to JsonPrimitive("http"),
                        "url" to JsonPrimitive("https://old.example/sub.yaml"),
                    ),
                ),
                proxyGroups = listOf(
                    jsonObj("name" to JsonPrimitive("MainGroup"), "type" to JsonPrimitive("select")),
                ),
            ),
        )

        val fetched = """
            mixed-port: 7890
            proxies:
              - name: node-a
                type: ss
                server: 1.2.3.4
                port: 8388
                cipher: aes-256-gcm
                password: x
            proxy-providers:
              sub-new:
                type: http
                url: "https://new.example/sub.yaml"
            proxy-groups:
              - name: GROUP
                type: select
                use:
                  - sub-new
            rules:
              - MATCH,GROUP
        """.trimIndent()

        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved)

        assertTrue("sub-new must survive: $merged", merged.contains("sub-new"))
        assertFalse("unreferenced sub-old must be GC'd: $merged", merged.contains("sub-old"))
    }

    @Test
    fun mergeAfterFetch_keepsAllProxyProvidersWhenGroupHasIncludeAll() {
        // include-all-providers: true expands the universe to every provider,
        // so GC must NOT prune anything.
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(
                proxyProviders = mapOf(
                    "sub-old" to jsonObj(
                        "type" to JsonPrimitive("http"),
                        "url" to JsonPrimitive("https://old.example/sub.yaml"),
                    ),
                ),
            ),
        )

        val fetched = """
            mixed-port: 7890
            proxies:
              - name: node-a
                type: ss
                server: 1.2.3.4
                port: 8388
                cipher: aes-256-gcm
                password: x
            proxy-providers:
              sub-new:
                type: http
                url: "https://new.example/sub.yaml"
            proxy-groups:
              - name: Auto
                type: url-test
                include-all-providers: true
            rules:
              - MATCH,Auto
        """.trimIndent()

        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved)

        assertTrue("sub-new must survive: $merged", merged.contains("sub-new"))
        assertTrue(
            "sub-old must survive under include-all-providers: $merged",
            merged.contains("sub-old"),
        )
    }

    @Test
    fun mergeAfterFetch_preservesManualNonSubscriptionRules() {
        // Pre-fetch snapshot: user added a manual DOMAIN rule + had a logical
        // AND rule + the subscription's MATCH. After fetch, only MATCH remains
        // in the upstream config.
        //
        // mergeRulesLists is intentional about what it carries across:
        //  - subscription-owned types (RULE-SET, GEOSITE, GEOIP, MATCH,
        //    SUB-RULE, AND, OR, NOT) are *not* preserved - they belong to
        //    the remote profile, resurrecting them would bring back rules
        //    the subscription owner deleted on purpose;
        //  - manual host/IP rules (DOMAIN, DOMAIN-SUFFIX, IP-CIDR, ...)
        //    are preserved as a prefix so they fire before subscription rules.
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(rules = listOf(
                "AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT",
                "DOMAIN,manual.example.com,DIRECT",
                "MATCH,GLOBAL",
            )),
        )

        val fetched = """
            mixed-port: 7890
            proxies:
              - name: node-a
                type: ss
                server: 1.2.3.4
                port: 8388
                cipher: aes-256-gcm
                password: x
            rules:
              - MATCH,GLOBAL
        """.trimIndent()

        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved)

        // Manual DOMAIN survives as a prefix - this was the whole point of
        // the preservation step.
        assertTrue(
            "merged config must contain DOMAIN rule, was:\n$merged",
            merged.contains("DOMAIN,manual.example.com,DIRECT"),
        )
        // Subscription's MATCH is the suffix.
        assertTrue(merged.contains("MATCH,GLOBAL"))
        // Logical AND was filtered out by design (it's subscription-owned).
        // This is the existing mergeRulesLists policy, not a Path B regression.
        assertFalse(
            "logical AND should be dropped during subscription merge per policy",
            merged.contains("AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT"),
        )
    }

    @Test
    fun extractPreserved_carriesListenersFromSnapshot() {
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(listeners = listOf(
                jsonObj(
                    "name" to JsonPrimitive("local-socks"),
                    "type" to JsonPrimitive("socks"),
                    "listen" to JsonPrimitive("127.0.0.1"),
                    "port" to JsonPrimitive(1080),
                ),
            )),
        )
        assertNotNull(preserved.listeners)
        @Suppress("UNCHECKED_CAST")
        val list = preserved.listeners as List<Any?>
        assertEquals(1, list.size)
        @Suppress("UNCHECKED_CAST")
        val entry = list[0] as Map<String, Any?>
        assertEquals("local-socks", entry["name"])
        assertEquals(1080L, entry["port"])
    }

    @Test
    fun mergeAfterFetch_preservesLocalListenersAcrossRefresh() {
        // User added a custom listener with auth — subscription refresh ships a
        // YAML without listeners. The pre-fetch snapshot must carry it over so
        // the user's edit isn't silently wiped. YamlHardener runs after this
        // merge and would force any non-loopback bind to 127.0.0.1, so it's
        // safe to merge first and harden later.
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            snapshot(listeners = listOf(
                jsonObj(
                    "name" to JsonPrimitive("user-mixed"),
                    "type" to JsonPrimitive("mixed"),
                    "listen" to JsonPrimitive("127.0.0.1"),
                    "port" to JsonPrimitive(7890),
                    "users" to JsonPrimitive("redacted"),
                ),
            )),
        )

        val fetched = """
            mixed-port: 0
            proxies:
              - name: node-a
                type: ss
                server: 1.2.3.4
                port: 8388
                cipher: aes-256-gcm
                password: x
        """.trimIndent()

        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved)
        assertTrue(
            "merged config must keep user's listener entry, was:\n$merged",
            merged.contains("user-mixed"),
        )
        assertTrue(merged.contains("listen: 127.0.0.1"))
        assertTrue(merged.contains("port: 7890"))
    }
}
