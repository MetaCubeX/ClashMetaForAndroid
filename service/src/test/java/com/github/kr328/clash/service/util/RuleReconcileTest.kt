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
}
