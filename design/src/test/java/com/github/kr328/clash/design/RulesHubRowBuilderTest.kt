package com.github.kr328.clash.design

import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesHubRowBuilderTest {
    @Test
    fun filterMine_excludesProviderRules() {
        val rules = listOf(
            manual("m1"),
            provider("p1"),
        )
        val filtered = RulesHubRowBuilder.filterRules(
            rules,
            RulesHubFilter.MINE,
            "",
            emptyMap(),
        )
        assertEquals(1, filtered.size)
        assertEquals("m1", filtered.single().id)
    }

    @Test
    fun isTargetMissing_whenPolicyUnknown() {
        val rule = manual("m1").copy(policy = "MissingGroup")
        assertTrue(RulesHubRowBuilder.isTargetMissing(rule, setOf("DIRECT", "PROXY")))
    }

    @Test
    fun isTargetMissing_falseForBuiltin() {
        val rule = manual("m1").copy(policy = "REJECT")
        val known = RulesHubRowBuilder.knownPolicies(emptyList())
        assertFalse(RulesHubRowBuilder.isTargetMissing(rule, known))
    }

    @Test
    fun reorderManual_movesItem() {
        val rules = listOf(manual("a"), manual("b"), manual("c"))
        val reordered = RulesHubRowBuilder.reorderManual(rules, 0, 2)
        assertEquals(listOf("b", "c", "a"), reordered.map { it.id })
    }

    private fun manual(id: String) = RuleItem(
        id = id,
        type = "DOMAIN",
        value = "$id.com",
        policy = "DIRECT",
        source = RuleSource.MANUAL,
    )

    private fun provider(id: String) = RuleItem(
        id = id,
        type = "RULE-SET",
        value = "ads",
        policy = "REJECT",
        source = RuleSource.PROVIDER,
    )
}
