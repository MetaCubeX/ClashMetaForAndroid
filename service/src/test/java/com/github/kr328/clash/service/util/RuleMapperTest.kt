package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Regression coverage for the AND/OR/SUB-RULE corruption bug
 * (rules[N] [AND,((NETWORK,UDP)] error: proxy [UDP] not found).
 *
 * The fix is two-fold:
 *  - parseRuleLine keeps opaque-type rules' raw line intact;
 *  - toRuleLine returns raw verbatim for those types so a round-trip
 *    (snapshot -> RuleState -> mergeStateIntoConfig) is byte-identical.
 */
class RuleMapperTest {

    private fun snapshotWith(vararg rules: String): ProfileSnapshot =
        ProfileSnapshot(rules = rules.toList())

    private fun providerJson(vararg pairs: Pair<String, String>): JsonObject =
        JsonObject(pairs.toMap().mapValues { JsonPrimitive(it.value) })

    @Test
    fun parseStateFromSnapshot_keepsLogicalRulesWhole() {
        val snapshot = snapshotWith(
            "DOMAIN,example.com,DIRECT",
            "AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT",
            "OR,((GEOIP,CN),(DOMAIN-SUFFIX,cn)),DIRECT",
            "NOT,((GEOIP,CN)),Proxy",
            "SUB-RULE,((DOMAIN,foo.com)),GLOBAL",
            "MATCH,GLOBAL",
        )

        val state = RuleMapper.parseStateFromSnapshot(snapshot)

        assertEquals(6, state.rules.size)
        // Logical rules survive as whole raw strings.
        assertEquals("AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT", state.rules[1].raw)
        assertEquals("OR,((GEOIP,CN),(DOMAIN-SUFFIX,cn)),DIRECT", state.rules[2].raw)
        assertEquals("NOT,((GEOIP,CN)),Proxy", state.rules[3].raw)
        assertEquals("SUB-RULE,((DOMAIN,foo.com)),GLOBAL", state.rules[4].raw)
        // Type extracted, but value/policy left empty for opaque rules so
        // the UI does not try to rewrite their internals.
        assertEquals("AND", state.rules[1].type)
        assertEquals("", state.rules[1].value)
        assertEquals("", state.rules[1].policy)
        assertEquals("SUB-RULE", state.rules[4].type)
    }

    @Test
    fun parseStateFromSnapshot_keepsRegularRulesDecomposed() {
        val snapshot = snapshotWith(
            "DOMAIN,example.com,DIRECT",
            "IP-CIDR,10.0.0.0/8,DIRECT",
            "RULE-SET,myrules,Proxy",
        )

        val state = RuleMapper.parseStateFromSnapshot(snapshot)

        assertEquals("DOMAIN", state.rules[0].type)
        assertEquals("example.com", state.rules[0].value)
        assertEquals("DIRECT", state.rules[0].policy)

        assertEquals("IP-CIDR", state.rules[1].type)
        assertEquals("10.0.0.0/8", state.rules[1].value)

        // RULE-SET is provider-backed and restorable.
        assertEquals("RULE-SET", state.rules[2].type)
        assertEquals("myrules", state.rules[2].providerName)
        assertEquals(RuleSource.PROVIDER, state.rules[2].source)
        assertTrue(state.rules[2].isRestorable)
    }

    @Test
    fun parseStateFromSnapshot_classifiesSubscriptionOwnedTypesAsProvider() {
        // Regression: GEOSITE / GEOIP / MATCH / AND / OR / NOT / SUB-RULE
        // cannot be entered via the UI add-rule form, so when they appear in
        // config.yaml they came from the subscription. Marking them MANUAL
        // (the parseRuleLine default for UI-add flows) caused
        // syncProviderRules to retain stale entries across subscription
        // refreshes that no longer contained them.
        val snapshot = snapshotWith(
            "GEOSITE,category-ads-all,REJECT",
            "GEOIP,private,DIRECT",
            "MATCH,GLOBAL",
            "AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT",
            "OR,((GEOIP,CN),(DOMAIN-SUFFIX,cn)),DIRECT",
            "NOT,((GEOIP,CN)),Proxy",
            "SUB-RULE,((DOMAIN,foo.com)),GLOBAL",
            "RULE-SET,myrules,Proxy",
        )

        val state = RuleMapper.parseStateFromSnapshot(snapshot)

        // Every subscription-owned type comes back as PROVIDER.
        state.rules.forEachIndexed { index, rule ->
            assertEquals(
                "rule[$index] type=${rule.type} must be PROVIDER",
                RuleSource.PROVIDER,
                rule.source,
            )
        }
    }

    @Test
    fun parseStateFromSnapshot_keepsManualTypesAsManual() {
        // User-addable types stay MANUAL when read from config.yaml so
        // subscription refresh does not wipe DOMAIN/IP-CIDR entries the
        // user added through the UI.
        val snapshot = snapshotWith(
            "DOMAIN,example.com,DIRECT",
            "DOMAIN-SUFFIX,corp.local,DIRECT",
            "IP-CIDR,10.0.0.0/8,DIRECT",
            "DOMAIN-KEYWORD,ads,REJECT",
        )

        val state = RuleMapper.parseStateFromSnapshot(snapshot)

        state.rules.forEach { rule ->
            assertEquals(
                "rule type=${rule.type} must keep MANUAL classification",
                RuleSource.MANUAL,
                rule.source,
            )
        }
    }

    @Test
    fun parseStateFromSnapshot_handlesMatchWithTrailingTarget() {
        val snapshot = snapshotWith("MATCH,GLOBAL")

        val state = RuleMapper.parseStateFromSnapshot(snapshot)

        assertEquals("MATCH", state.rules[0].type)
        assertEquals("GLOBAL", state.rules[0].policy)
        assertEquals("", state.rules[0].value)
    }

    @Test
    fun parseStateFromSnapshot_mapsRuleProviders() {
        val snapshot = ProfileSnapshot(
            rules = emptyList(),
            ruleProviders = mapOf(
                "myrules" to providerJson(
                    "type" to "http",
                    "behavior" to "classical",
                    "url" to "https://example.com/rules.yaml",
                    "path" to "./ruleset/myrules.yaml",
                    "interval" to "86400",
                ),
            ),
        )

        val state = RuleMapper.parseStateFromSnapshot(snapshot)

        assertEquals(1, state.providers.size)
        val p = state.providers[0]
        assertEquals("myrules", p.name)
        assertEquals("http", p.type)
        assertEquals("classical", p.behavior)
        assertEquals("https://example.com/rules.yaml", p.url)
        assertEquals("./ruleset/myrules.yaml", p.path)
        assertEquals(86400, p.interval)
        assertEquals(RuleSource.PROVIDER, p.source)
    }

    @Test
    fun parseRuleLine_logicalRule_keepsRawAndEmptyBody() {
        val line = "AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT"
        val rule = RuleMapper.parseRuleLine(line, 0)

        assertNotNull(rule)
        rule!!
        assertEquals(line, rule.raw)
        assertEquals("AND", rule.type)
        assertEquals("", rule.value)
        assertEquals("", rule.policy)
    }

    @Test
    fun parseRuleLine_match_extractsTargetCorrectly() {
        val rule = RuleMapper.parseRuleLine("MATCH,DIRECT", 0)
        assertNotNull(rule)
        rule!!
        assertEquals("MATCH", rule.type)
        assertEquals("DIRECT", rule.policy)
    }

    @Test
    fun parseRuleLine_returnsNullForBlank() {
        assertEquals(null, RuleMapper.parseRuleLine("", 0))
        assertEquals(null, RuleMapper.parseRuleLine("   ", 0))
        assertEquals(null, RuleMapper.parseRuleLine("- ", 0))
    }

    @Test
    fun toRuleLine_logicalRule_returnsRawVerbatim() {
        val rule = RuleItem(
            id = UUID.randomUUID().toString(),
            raw = "AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT",
            type = "AND",
            value = "",
            policy = "",
        )
        // Critical: the previous bug was toRuleLine synthesising from
        // type/value/policy and producing "AND,," — we must return raw.
        assertEquals(rule.raw, RuleMapper.toRuleLine(rule))
    }

    @Test
    fun toRuleLine_subRule_returnsRawVerbatim() {
        val rule = RuleItem(
            id = UUID.randomUUID().toString(),
            raw = "SUB-RULE,((DOMAIN,foo.com)),GLOBAL",
            type = "SUB-RULE",
            value = "",
            policy = "",
        )
        assertEquals(rule.raw, RuleMapper.toRuleLine(rule))
    }

    @Test
    fun toRuleLine_regularRule_synthesisesFromFields() {
        val rule = RuleItem(
            id = UUID.randomUUID().toString(),
            raw = "DOMAIN,example.com,DIRECT",
            type = "DOMAIN",
            value = "example.com",
            policy = "DIRECT",
        )
        assertEquals("DOMAIN,example.com,DIRECT", RuleMapper.toRuleLine(rule))
    }

    @Test
    fun toRuleLine_match_synthesisesCorrectly() {
        val rule = RuleItem(
            id = UUID.randomUUID().toString(),
            raw = "MATCH,DIRECT",
            type = "MATCH",
            value = "",
            policy = "DIRECT",
        )
        assertEquals("MATCH,DIRECT", RuleMapper.toRuleLine(rule))
    }

    @Test
    fun roundTrip_logicalRules_areByteIdentical() {
        val inputs = listOf(
            "AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT",
            "OR,((GEOIP,CN),(DOMAIN-SUFFIX,cn)),DIRECT",
            "NOT,((GEOIP,CN)),Proxy",
            "SUB-RULE,((DOMAIN,foo.com)),GLOBAL",
            "DOMAIN,example.com,DIRECT",
            "RULE-SET,myrules,Proxy",
            "MATCH,GLOBAL",
        )
        val snapshot = ProfileSnapshot(rules = inputs)

        val state = RuleMapper.parseStateFromSnapshot(snapshot)
        val rendered = state.rules.map(RuleMapper::toRuleLine)

        assertEquals(inputs, rendered)
    }
}
