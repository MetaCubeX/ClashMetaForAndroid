package com.github.kr328.clash.design

import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleSource
import com.github.kr328.clash.service.model.RuleState
import kotlin.test.Test
import kotlin.test.assertEquals

class RulesHubStateDiffTest {
    @Test
    fun compute_countsAddedRemovedDisabled() {
        val baseline = RuleState(
            rules = listOf(
                rule("a", enabled = true),
                rule("b", enabled = true),
            ),
        )
        val current = RuleState(
            rules = listOf(
                rule("a", enabled = false),
                rule("b", enabled = true, deleted = true),
                rule("c", enabled = true),
            ),
        )
        val counts = RulesHubStateDiff.compute(baseline, current)
        assertEquals(1, counts.added)
        assertEquals(1, counts.removed)
        assertEquals(1, counts.disabled)
    }

    @Test
    fun compute_noChanges() {
        val state = RuleState(rules = listOf(rule("a")))
        val counts = RulesHubStateDiff.compute(state, state)
        assertEquals(0, counts.added)
        assertEquals(0, counts.removed)
        assertEquals(0, counts.disabled)
    }

    private fun rule(
        id: String,
        enabled: Boolean = true,
        deleted: Boolean = false,
    ) = RuleItem(
        id = id,
        type = "DOMAIN",
        value = "$id.com",
        policy = "DIRECT",
        enabled = enabled,
        deleted = deleted,
        source = RuleSource.MANUAL,
    )
}
