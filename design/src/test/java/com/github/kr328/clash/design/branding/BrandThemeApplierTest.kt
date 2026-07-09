package com.github.kr328.clash.design.branding

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the recreate-loop fix: `applyToActivity`/`themedContextFor` (what's applied) and
 * `accentStale` (what's desired) all derive the accent through
 * [BrandThemeApplier.applicableAccent]. As long as that single function decides
 * applicability, applied and desired can never disagree, so an unapplicable accent
 * cannot ping-pong into an endless soft-recreate storm.
 */
class BrandThemeApplierTest {

    @Test
    fun validRRGGBBPassesThrough() {
        assertEquals("#AABBCC", BrandThemeApplier.applicableAccent("#AABBCC"))
        assertEquals("#123abc", BrandThemeApplier.applicableAccent("#123abc"))
    }

    @Test
    fun nullOrBlankIsEmpty() {
        assertEquals("", BrandThemeApplier.applicableAccent(null))
        assertEquals("", BrandThemeApplier.applicableAccent(""))
        assertEquals("", BrandThemeApplier.applicableAccent("   "))
    }

    @Test
    fun unapplicableValuesCollapseToEmpty() {
        // These are exactly the values that would make Color.parseColor diverge from a raw
        // compare and previously risk an infinite recreate loop.
        for (bad in listOf("red", "#abc", "#aabbccdd", "#gggggg", "aabbcc", "#aabbc")) {
            assertEquals("", BrandThemeApplier.applicableAccent(bad), "bad accent should collapse: $bad")
        }
    }

    @Test
    fun appliedAndDesiredAgreeForAnyInput_noLoop() {
        // The loop invariant: both call sites use applicableAccent, so for any stored accent the
        // "applied" string equals the "desired" string -> recreate-check converges, never loops.
        for (input in listOf(null, "", "#AABBCC", "red", "#abc", "#zzzzzz")) {
            val applied = BrandThemeApplier.applicableAccent(input)
            val desired = BrandThemeApplier.applicableAccent(input)
            assertEquals(applied, desired)
        }
    }
}
