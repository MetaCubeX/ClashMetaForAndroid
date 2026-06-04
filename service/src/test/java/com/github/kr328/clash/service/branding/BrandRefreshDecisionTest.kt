package com.github.kr328.clash.service.branding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Debounced clearing of a brand when confirmed (HTTP 2xx) subscription responses stop carrying
 * X-Brand-* headers — so a broken operator panel eventually resets the brand instead of sticking
 * forever, while a one-off header drop does not.
 */
class BrandRefreshDecisionTest {

    private val threshold = BrandRefresh.EMPTY_STREAK_TO_CLEAR // 3

    @Test
    fun unconfirmedEmpty_neverClears_andDoesNotCount() {
        val a = BrandRefresh.decideOnEmpty(confirmed = false, isActive = true, streak = 2, threshold = threshold)
        assertFalse(a.clear)
        assertEquals(2, a.newStreak)
        assertFalse(a.streakChanged)
    }

    @Test
    fun confirmedButNoActiveBrand_resetsStreak() {
        val reset = BrandRefresh.decideOnEmpty(confirmed = true, isActive = false, streak = 2, threshold = threshold)
        assertFalse(reset.clear)
        assertEquals(0, reset.newStreak)
        assertTrue(reset.streakChanged)

        val alreadyZero = BrandRefresh.decideOnEmpty(confirmed = true, isActive = false, streak = 0, threshold = threshold)
        assertFalse(alreadyZero.streakChanged)
    }

    @Test
    fun confirmedActiveEmpty_incrementsBelowThreshold() {
        val first = BrandRefresh.decideOnEmpty(confirmed = true, isActive = true, streak = 0, threshold = threshold)
        assertFalse(first.clear)
        assertEquals(1, first.newStreak)
        assertTrue(first.streakChanged)

        val second = BrandRefresh.decideOnEmpty(confirmed = true, isActive = true, streak = 1, threshold = threshold)
        assertFalse(second.clear)
        assertEquals(2, second.newStreak)
    }

    @Test
    fun confirmedActiveEmpty_clearsAtThreshold() {
        val third = BrandRefresh.decideOnEmpty(confirmed = true, isActive = true, streak = 2, threshold = threshold)
        assertTrue(third.clear)
        assertEquals(0, third.newStreak)
        assertTrue(third.streakChanged)
    }

    @Test
    fun threshold_isThree_soOneOrTwoDropsDoNotClear() {
        assertEquals(3, threshold)
        // Two consecutive confirmed-empties must NOT clear (survives a one-off panel glitch).
        assertFalse(BrandRefresh.decideOnEmpty(true, isActive = true, streak = 0, threshold = threshold).clear)
        assertFalse(BrandRefresh.decideOnEmpty(true, isActive = true, streak = 1, threshold = threshold).clear)
    }
}
