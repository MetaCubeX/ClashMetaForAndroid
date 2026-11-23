package com.github.kr328.clash.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class ProfileOrderTest {
    @Test
    fun emptySavedOrderKeepsTheExistingList() {
        val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val second = UUID.fromString("00000000-0000-0000-0000-000000000002")
        assertEquals(listOf(first, second), mergeProfileOrder(emptyList(), listOf(first, second)))
    }

    @Test
    fun savedOrderWinsAndNewProfilesFollow() {
        val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val second = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val third = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val gone = UUID.fromString("00000000-0000-0000-0000-000000000009")
        assertEquals(
            listOf(second, first, third),
            mergeProfileOrder(listOf(second, gone, first, second), listOf(first, third, second)),
        )
    }
}
