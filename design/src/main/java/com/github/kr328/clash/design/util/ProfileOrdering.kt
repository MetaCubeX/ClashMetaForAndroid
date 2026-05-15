package com.github.kr328.clash.design.util

import com.github.kr328.clash.design.model.ProfileSortMode

object ProfileOrdering {
    fun <T> sortForDisplay(
        profiles: List<T>,
        mode: ProfileSortMode,
        active: (T) -> Boolean,
        name: (T) -> String,
        updatedAt: (T) -> Long,
    ): List<T> =
        when (mode) {
            ProfileSortMode.Manual -> profiles
            ProfileSortMode.ActiveFirst -> profiles.stableSortedWith(
                compareByDescending<IndexedValue<T>> { active(it.value) }
            )
            ProfileSortMode.Name -> profiles.stableSortedWith(
                Comparator { left, right ->
                    String.CASE_INSENSITIVE_ORDER.compare(name(left.value), name(right.value))
                }
            )
            ProfileSortMode.LastUpdated -> profiles.stableSortedWith(
                compareByDescending<IndexedValue<T>> { updatedAt(it.value) }
            )
        }

    fun normalizeOrderIds(orderedIds: List<String>, knownIds: List<String>): List<String> {
        val known = knownIds.toSet()
        val result = orderedIds
            .filter { it in known }
            .distinct()
            .toMutableList()
        result.addAll(knownIds.filterNot { it in result })
        return result
    }

    private fun <T> List<T>.stableSortedWith(
        comparator: Comparator<IndexedValue<T>>,
    ): List<T> =
        withIndex()
            .sortedWith(comparator.thenBy { it.index })
            .map { it.value }
}
