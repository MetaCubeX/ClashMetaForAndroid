package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleSource
import com.github.kr328.clash.service.model.RuleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the subscription-refresh reconcile contract for the rule editor: a user's
 * enable/disable/delete decision on a PROVIDER rule must re-bind to the SAME rule
 * after a refresh (identity = type,value,policy — not order), and MANUAL rules must
 * never be lost.
 */
class RuleReconcileTest {
    private fun provider(type: String, value: String, policy: String, id: String, enabled: Boolean = true, deleted: Boolean = false) =
        RuleItem(id = id, type = type, value = value, policy = policy, enabled = enabled, deleted = deleted, source = RuleSource.PROVIDER)

    @Test
    fun disabledProviderRule_staysDisabledAfterRefresh_evenAtNewPosition() {
        val stored = RuleState(rules = listOf(provider("RULE-SET", "ads", "REJECT", id = "stored-1", enabled = false)))
        // fresh fetch reintroduces the rule (default enabled) at a DIFFERENT position
        val incoming = RuleState(rules = listOf(
            provider("DOMAIN", "a.com", "DIRECT", id = "f1"),
            provider("RULE-SET", "ads", "REJECT", id = "f2", enabled = true),
        ))

        val merged = syncStoredWithParsed(stored, incoming)
        val ads = merged.rules.first { it.type == "RULE-SET" && it.value == "ads" }
        assertFalse(ads.enabled, "user's disable must persist across refresh")
        assertEquals("stored-1", ads.id, "re-binds to the stored rule's identity")
    }

    @Test
    fun deletedProviderRule_staysDeletedAfterRefresh() {
        val stored = RuleState(rules = listOf(provider("RULE-SET", "ads", "REJECT", id = "s", deleted = true)))
        val incoming = RuleState(rules = listOf(provider("RULE-SET", "ads", "REJECT", id = "f")))
        val merged = syncStoredWithParsed(stored, incoming)
        assertTrue(merged.rules.first { it.value == "ads" }.deleted, "delete persists")
    }

    @Test
    fun manualRules_retainedAcrossRefresh() {
        val stored = RuleState(rules = listOf(
            RuleItem(id = "m", type = "DOMAIN", value = "mine.com", policy = "DIRECT", source = RuleSource.MANUAL),
        ))
        val incoming = RuleState(rules = listOf(provider("RULE-SET", "ads", "REJECT", id = "f")))
        val merged = syncStoredWithParsed(stored, incoming)
        assertTrue(merged.rules.any { it.value == "mine.com" && it.source == RuleSource.MANUAL }, "manual rule kept")
        assertTrue(merged.rules.any { it.value == "ads" }, "fetched provider rule present")
    }

    @Test
    fun deletedManualRule_isPurged_notResurrected() {
        // The user deleted their own MANUAL rule. It must NOT come back on the next
        // load/refresh (the "delete does nothing" bug) — a manual rule has no upstream
        // to restore from, unlike a deleted PROVIDER rule.
        val stored = RuleState(rules = listOf(
            RuleItem(id = "m", type = "DOMAIN", value = "gone.com", policy = "DIRECT",
                source = RuleSource.MANUAL, deleted = true, enabled = false),
        ))
        val incoming = RuleState(rules = listOf(provider("RULE-SET", "ads", "REJECT", id = "f")))
        val merged = syncStoredWithParsed(stored, incoming)
        assertFalse(merged.rules.any { it.value == "gone.com" }, "deleted manual rule must be purged")
    }

    @Test
    fun deletedProviderRule_isStillRetained() {
        // Contrast: a deleted PROVIDER rule stays (soft-delete guards a sub refresh
        // from resurrecting it + supports restore).
        val stored = RuleState(rules = listOf(
            provider("DOMAIN", "ad.com", "REJECT", id = "p", deleted = true, enabled = false),
        ))
        val incoming = RuleState(rules = listOf(provider("RULE-SET", "ads", "REJECT", id = "f")))
        val merged = syncStoredWithParsed(stored, incoming)
        assertTrue(merged.rules.any { it.value == "ad.com" && it.deleted }, "deleted provider rule retained")
    }

    @Test
    fun reAddingDeletedManualKey_comesBackEnabled_notStuckDeleted() {
        // After deleting DOMAIN,x,DIRECT the user re-adds the same rule. The stale
        // deleted=true entry must not stamp itself onto the fresh re-add.
        val stored = RuleState(rules = listOf(
            RuleItem(id = "old", type = "DOMAIN", value = "x.com", policy = "DIRECT",
                source = RuleSource.MANUAL, deleted = true, enabled = false),
        ))
        // re-added rule is present in the (merged) config => parsed PROVIDER, reclassified MANUAL
        val incoming = RuleState(rules = listOf(
            RuleItem(id = "new", type = "DOMAIN", value = "x.com", policy = "DIRECT", source = RuleSource.MANUAL),
        ))
        val merged = syncStoredWithParsed(stored, incoming)
        val x = merged.rules.single { it.value == "x.com" }
        assertTrue(x.enabled && !x.deleted, "re-added rule must be live, not inherit the old delete")
    }

    @Test
    fun freshProviderRule_defaultsEnabled() {
        val merged = syncStoredWithParsed(
            RuleState(),
            RuleState(rules = listOf(provider("RULE-SET", "new", "DIRECT", id = "f"))),
        )
        assertTrue(merged.rules.single().enabled)
    }

    // --- stable ordering (toggle restores position) ---
    private fun ordered(id: String, order: Int, enabled: Boolean = true) =
        RuleItem(id = id, type = "DOMAIN", value = id, policy = "Proxy", enabled = enabled, order = order)

    @Test
    fun disabledRule_keepsItsSlot_notMovedToTail() {
        val rules = listOf(ordered("a", 0), ordered("b", 1, enabled = false), ordered("c", 2))
        val out = normalizeRuleOrder(rules)
        // b (disabled) stays in the middle, so re-enabling restores its position.
        assertEquals(listOf("a", "b", "c"), out.map { it.id })
        assertEquals(listOf(0, 1, 2), out.map { it.order })
    }

    @Test
    fun noPriorityResort_userOrderWins() {
        // A REJECT / RULE-SET rule placed by the user/subscription must STAY put,
        // not be auto-hoisted by rule type.
        val rules = listOf(
            RuleItem(id = "x", type = "DOMAIN", value = "x", policy = "Proxy", order = 0),
            RuleItem(id = "r", type = "DOMAIN", value = "r", policy = "REJECT", order = 1),
            RuleItem(id = "rs", type = "RULE-SET", value = "ads", policy = "DIRECT", order = 2),
        )
        assertEquals(listOf("x", "r", "rs"), normalizeRuleOrder(rules).map { it.id })
    }

    @Test
    fun reclassifyBySubscription_healsManualBackToSub_keepsGenuineManual() {
        val rules = listOf(
            // plain rule that IS in the fetched subscription (was wrongly stored MANUAL)
            RuleItem(id = "a", type = "DOMAIN", value = "sub.com", policy = "DIRECT", source = RuleSource.MANUAL),
            // genuine user rule, NOT present in the fetched subscription
            RuleItem(id = "b", type = "DOMAIN", value = "mine.com", policy = "DIRECT", source = RuleSource.MANUAL),
            // a subscription rule already classified PROVIDER
            RuleItem(id = "c", type = "DOMAIN", value = "other.com", policy = "Proxy", source = RuleSource.PROVIDER),
        )
        val fetchedKeys = setOf("DOMAIN,SUB.COM,DIRECT", "DOMAIN,OTHER.COM,PROXY")

        val out = reclassifyBySubscription(rules, fetchedKeys).associateBy { it.id }

        assertEquals(RuleSource.PROVIDER, out.getValue("a").source) // healed: in sub -> PROVIDER
        assertEquals(RuleSource.MANUAL, out.getValue("b").source)   // genuine manual stays MANUAL
        assertEquals(RuleSource.PROVIDER, out.getValue("c").source) // provider unchanged
    }

    @Test
    fun reclassifyBySubscription_emptyKeys_leavesUnchanged() {
        val rules = listOf(
            RuleItem(id = "m", type = "DOMAIN", value = "x", policy = "DIRECT", source = RuleSource.MANUAL),
        )
        assertEquals(RuleSource.MANUAL, reclassifyBySubscription(rules, emptySet()).single().source)
    }

    // --- full reconcile pipeline (regression: manual rule erased after sub update) ---
    // Mirrors ProfileProcessor.update end-to-end on the PURE layer (everything except the
    // native snapshot parse): merged config (post mergeAfterFetch) -> parsed-all-PROVIDER
    // -> reclassify by raw-fetch keys -> syncStoredWithParsed -> mergeStateIntoConfig.
    private val geoUrls = GeoDataUrls(geoIp = "", geoSite = "", mmdb = "", asn = "")

    @Test
    fun fullReconcile_manualRuleSurvivesSubscriptionUpdate() {
        // The merged config (mergeAfterFetch output) the engine snapshot would read:
        // the prefixed manual rule + the fresh subscription rules.
        val mergedConfig = """
            rules:
              - DOMAIN,mine.com,DIRECT
              - DOMAIN,sub.com,Proxy
              - RULE-SET,ads,REJECT
              - MATCH,Proxy
        """.trimIndent()
        // What parseStateFromSnapshot returns for that config: EVERY rule PROVIDER by default.
        val parsed = RuleState(
            rules = listOf(
                provider("DOMAIN", "mine.com", "DIRECT", id = "p0"),
                provider("DOMAIN", "sub.com", "Proxy", id = "p1"),
                provider("RULE-SET", "ads", "REJECT", id = "p2"),
                RuleItem(id = "p3", raw = "MATCH,Proxy", type = "MATCH", value = "", policy = "Proxy", source = RuleSource.PROVIDER),
            ),
        )
        // Raw fetched subscription rule keys (mine.com is NOT in the sub — it's the user's).
        val subKeys = setOf("DOMAIN,SUB.COM,PROXY", "RULE-SET,ADS,REJECT", "MATCH,,PROXY")
        // rules_state.json carried over from before the update: the user's manual rule MANUAL.
        val stored = RuleState(
            rules = listOf(
                RuleItem(id = "m", type = "DOMAIN", value = "mine.com", policy = "DIRECT", source = RuleSource.MANUAL),
                provider("DOMAIN", "sub.com", "Proxy", id = "s1"),
                provider("RULE-SET", "ads", "REJECT", id = "s2"),
            ),
        )

        val classified = parsed.copy(rules = reclassifyBySubscription(parsed.rules, subKeys))
        val merged = syncStoredWithParsed(stored, classified)
        val normalized = merged.copy(rules = normalizeRuleOrder(merged.rules))

        // 1. the manual rule kept its MANUAL source through reconcile
        val mine = normalized.rules.single { it.value == "mine.com" }
        assertEquals(RuleSource.MANUAL, mine.source)
        assertTrue(mine.enabled && !mine.deleted, "manual rule stays active")

        // 2. and it actually lands in the emitted config.yaml
        val outYaml = RuleMapper.mergeStateIntoConfig(mergedConfig, normalized, geoUrls)
        assertTrue(outYaml.contains("DOMAIN,mine.com,DIRECT"), "manual rule must survive into config:\n$outYaml")
    }
}
