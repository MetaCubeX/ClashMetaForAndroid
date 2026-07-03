package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.RuleItem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks [normalizeRuleOrder]'s stable-ordering contract: a rule keeps its slot (so toggling it
 * off then on restores its exact position), and rules are never auto-hoisted by type — the user's
 * / subscription author's order wins.
 */
class RuleOrderTest {
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
