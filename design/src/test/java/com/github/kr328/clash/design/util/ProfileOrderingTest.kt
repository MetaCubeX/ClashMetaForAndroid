package com.github.kr328.clash.design.util

import com.github.kr328.clash.design.model.ProfileSortMode
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileOrderingTest {
    private data class Row(
        val id: String,
        val name: String,
        val active: Boolean = false,
        val updatedAt: Long = 0L,
    )

    private val rows = listOf(
        Row("first", "Zulu", updatedAt = 10L),
        Row("second", "alpha", active = true, updatedAt = 30L),
        Row("third", "Bravo", updatedAt = 20L),
        Row("fourth", "alpha", updatedAt = 40L),
    )

    @Test
    fun manualModeKeepsManualOrder() {
        val sorted = rows.sorted(ProfileSortMode.Manual)

        assertEquals(listOf("first", "second", "third", "fourth"), sorted.map { it.id })
    }

    @Test
    fun activeFirstKeepsRelativeOrderWithinGroups() {
        val sorted = rows.sorted(ProfileSortMode.ActiveFirst)

        assertEquals(listOf("second", "first", "third", "fourth"), sorted.map { it.id })
    }

    @Test
    fun nameSortIsCaseInsensitiveAndStable() {
        val sorted = rows.sorted(ProfileSortMode.Name)

        assertEquals(listOf("second", "fourth", "third", "first"), sorted.map { it.id })
    }

    @Test
    fun lastUpdatedSortShowsNewestFirstAndDoesNotMutateInput() {
        val sorted = rows.sorted(ProfileSortMode.LastUpdated)

        assertEquals(listOf("fourth", "second", "third", "first"), sorted.map { it.id })
        assertEquals(listOf("first", "second", "third", "fourth"), rows.map { it.id })
    }

    @Test
    fun normalizeOrderIdsDropsUnknownDeduplicatesAndAppendsMissingKnownIds() {
        val normalized = ProfileOrdering.normalizeOrderIds(
            orderedIds = listOf("third", "missing", "first", "third"),
            knownIds = listOf("first", "second", "third", "fourth"),
        )

        assertEquals(listOf("third", "first", "second", "fourth"), normalized)
    }

    private fun List<Row>.sorted(mode: ProfileSortMode): List<Row> =
        ProfileOrdering.sortForDisplay(
            profiles = this,
            mode = mode,
            active = Row::active,
            name = Row::name,
            updatedAt = Row::updatedAt,
        )
}
