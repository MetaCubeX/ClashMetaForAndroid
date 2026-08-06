package com.github.kr328.clash.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamingTextPatchTest {
    @Test
    fun identicalFramesDoNoWork() {
        assertNull(StreamingTextPatchPlanner.calculate("same", "same"))
    }

    @Test
    fun onlyActiveBlockIsReplaced() {
        val current = "First paragraph.\n\nSecond **par"
        val next = "First paragraph.\n\nSecond paragraph"
        val patch = assertNotNull(StreamingTextPatchPlanner.calculate(current, next))
        assertEquals("First paragraph.\n\n".length, patch.start)
        assertEquals(next, apply(current, next, patch))
    }

    @Test
    fun completedHistoryNeverChangesDuringLongBurstyStream() {
        val stable = (1..120).joinToString("\n\n") { "Stable block $it." } + "\n\n"
        var visible = stable
        var totalMutatedCharacters = 0L
        repeat(4_000) { index ->
            val next = stable + "Live block " + "token ".repeat(index + 1)
            val patch = assertNotNull(StreamingTextPatchPlanner.calculate(visible, next))
            assertEquals(stable.length, patch.start)
            assertEquals(next, apply(visible, next, patch))
            totalMutatedCharacters += patch.oldEnd - patch.start + patch.newEnd - patch.start
            visible = next
        }
        assertTrue(totalMutatedCharacters < visible.length.toLong() * 4_100)
    }

    @Test
    fun surrogatePairIsNeverSplit() {
        val current = "Stable.\n\nHello 👨‍👩‍👧"
        val next = "Stable.\n\nHello 👨‍👩‍👧‍👦!"
        val patch = assertNotNull(StreamingTextPatchPlanner.calculate(current, next))
        assertTrue(patch.start == 0 || !Character.isLowSurrogate(current[patch.start]))
        assertEquals(next, apply(current, next, patch))
    }

    private fun apply(current: String, next: String, patch: StreamingTextPatch): String =
        current.substring(0, patch.start) + next.substring(patch.start, patch.newEnd)
}
