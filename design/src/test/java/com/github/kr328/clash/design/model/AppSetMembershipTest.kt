package com.github.kr328.clash.design.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSetMembershipTest {
    @Test
    fun emptySetStaysUnchecked() {
        val selected = mutableSetOf("com.example.keep")
        val membership = appSetMembership(emptySet(), selected)

        assertEquals(AppSetCheckState.Unchecked, membership.state)
        applyAppSetToggle(emptySet(), selected)
        assertEquals(setOf("com.example.keep"), selected)
    }

    @Test
    fun partialSelectionBecomesFullySelected() {
        val members = setOf("com.tencent.mm", "com.eg.android.AlipayGphone")
        val selected = mutableSetOf("com.tencent.mm", "com.example.other")

        assertEquals(AppSetCheckState.Indeterminate, appSetMembership(members, selected).state)
        applyAppSetToggle(members, selected)

        assertEquals(
            setOf("com.tencent.mm", "com.eg.android.AlipayGphone", "com.example.other"),
            selected,
        )
        assertEquals(AppSetCheckState.Checked, appSetMembership(members, selected).state)
    }

    @Test
    fun fullSelectionClearsOnlyMembers() {
        val members = setOf("com.android.settings", "com.android.phone")
        val selected = mutableSetOf("com.android.settings", "com.android.phone", "com.tencent.mm")

        assertEquals(2, appSetMembership(members, selected).selectedCount)
        applyAppSetToggle(members, selected)

        assertEquals(setOf("com.tencent.mm"), selected)
        assertEquals(AppSetCheckState.Unchecked, appSetMembership(members, selected).state)
    }
}
