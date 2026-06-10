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
}
