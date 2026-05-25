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
    ) = ProfileSnapshot(
        rules = rules,
        ruleProviders = ruleProviders,
        proxyProviders = proxyProviders,
        proxyGroups = proxyGroups,
    )

    private fun jsonObj(vararg pairs: Pair<String, JsonElement>): JsonObject =
        JsonObject(pairs.toMap())

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
}
