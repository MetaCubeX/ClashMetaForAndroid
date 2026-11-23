package com.github.kr328.clash.design.preference

import com.github.kr328.clash.design.dialog.confirmedModelText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSessionCommitTest {
    @Test
    fun unparsedPortTextIsNotWritten() {
        val bad = listOf("abc", "80 ", "80\n", "2147483648", " 80")
        for (text in bad) {
            assertTrue(isUnparsedPortText(text))
            assertFalse(acceptsEmptyOrInt(text))
            assertNull(text.toIntOrNull())
            val commit = commitNullableText("80", text, NullableTextAdapter.Port)
            assertEquals(EditCommit.Skip, commit)
            assertNotEquals(0 as Any?, NullableTextAdapter.Port.to(text) as Any?)
        }
    }

    @Test
    fun emptyPortTextIsWrittenAsZeroAndResetNullIsWritten() {
        assertEquals(
            EditCommit.Write<Int?>(0),
            commitNullableText("80", "", NullableTextAdapter.Port),
        )
        assertEquals(
            EditCommit.Write<Int?>(0),
            commitNullableText(null, "", NullableTextAdapter.Port),
        )
        assertEquals(0 as Any?, NullableTextAdapter.Port.to("") as Any?)
        assertEquals(
            EditCommit.Write<Int?>(null),
            commitNullableText("80", null, NullableTextAdapter.Port),
        )
        assertEquals(
            EditCommit.Write<Int?>(null),
            commitNullableText("", null, NullableTextAdapter.Port),
        )
        assertNull(NullableTextAdapter.Port.to(null))
    }

    @Test
    fun portValidatorAcceptsEmptyOrIntegerWithoutTrimming() {
        assertTrue(acceptsEmptyOrInt(""))
        assertTrue(acceptsEmptyOrInt("0"))
        assertTrue(acceptsEmptyOrInt("80"))
        assertTrue(acceptsEmptyOrInt("080"))
        assertTrue(acceptsEmptyOrInt("-1"))
        assertEquals(
            EditCommit.Write<Int?>(80),
            commitNullableText("80", "080", NullableTextAdapter.Port),
        )
        assertEquals(
            EditCommit.Write<Int?>(-1),
            commitNullableText("80", "-1", NullableTextAdapter.Port),
        )
    }

    @Test
    fun unchangedTextSkipsWriteAndNullClearsASetValue() {
        assertEquals(
            EditCommit.Skip,
            commitNullableText("80", "80", NullableTextAdapter.Port),
        )
        assertEquals(
            EditCommit.Skip,
            commitNullableText(null, null, NullableTextAdapter.Port),
        )
        assertEquals(
            EditCommit.Skip,
            commitNullableText("https://example", "https://example", NullableTextAdapter.String),
        )
        assertEquals(
            EditCommit.Write<String?>("abc"),
            commitNullableText("x", "abc", NullableTextAdapter.String),
        )
        assertEquals(
            EditCommit.Write<String?>(null),
            commitNullableText("https://example", null, NullableTextAdapter.String),
        )
    }

    @Test
    fun listAndMapCancelSkipWhileResetWritesNull() {
        val names = listOf("dns.google")
        assertEquals(EditCommit.Skip, commitIfChanged(names, names))
        assertEquals(EditCommit.Skip, commitIfChanged(names, listOf("dns.google")))
        assertEquals(EditCommit.Write<List<String>?>(null), commitIfChanged(names, null))
        assertEquals(
            EditCommit.Write(listOf("dns.google", "1.1.1.1")),
            commitIfChanged(names, listOf("dns.google", "1.1.1.1")),
        )

        val hosts = mapOf("example.com" to "1.1.1.1")
        assertEquals(EditCommit.Skip, commitIfChanged(hosts, hosts))
        assertEquals(EditCommit.Write<Map<String, String>?>(null), commitIfChanged(hosts, null))
        assertEquals(
            EditCommit.Write(mapOf("example.com" to "8.8.8.8")),
            commitIfChanged(hosts, mapOf("example.com" to "8.8.8.8")),
        )
    }

    @Test
    fun sameFieldSecondEntryDoesNotReadOrCoverTheSavedValue() {
        val sessions = FieldEditSessions()
        val field = "http-port"
        var stored = "old"
        var reads = 0

        assertTrue(sessions.tryEnter(field))
        val initial = stored.also { reads++ }
        val saved = commitIfChanged(initial, "new")
        check(saved is EditCommit.Write)
        stored = saved.value

        var secondReads = 0
        if (sessions.tryEnter(field)) {
            secondReads++
            stored = initial
        }
        assertEquals(0, secondReads)
        assertEquals(1, reads)
        assertEquals("new", stored)

        assertTrue(sessions.tryEnter("socks-port"))
        sessions.exit(field)
        assertTrue(sessions.tryEnter(field))
        stored.also { reads++ }
        assertEquals(2, reads)
    }

    @Test
    fun uneditedNullWebdavConfirmsAsNullNotEmptyString() {
        val confirmed = confirmedModelText(
            initial = null,
            text = "",
            accepted = true,
            preserveUnedited = true,
        )
        assertNull(confirmed)
        assertNull(NullableTextAdapter.String.to(confirmed))
        assertEquals("", NullableTextAdapter.String.to(""))
        assertEquals(
            EditCommit.Skip,
            commitNullableText(null, confirmed, NullableTextAdapter.String),
        )

        val writtenAsEmpty = confirmedModelText(
            initial = null,
            text = "",
            accepted = true,
            preserveUnedited = false,
        )
        assertEquals("", writtenAsEmpty)
    }

    @Test
    fun uneditedWebdavUrlStaysAndClearingWritesEmptyAndResetWritesNull() {
        val kept = confirmedModelText(
            initial = "https://example",
            text = "https://example",
            accepted = true,
            preserveUnedited = true,
        )
        assertEquals("https://example", kept)
        assertEquals(
            EditCommit.Skip,
            commitNullableText("https://example", kept, NullableTextAdapter.String),
        )

        val cleared = confirmedModelText(
            initial = "https://example",
            text = "",
            accepted = true,
            preserveUnedited = true,
        )
        assertEquals("", cleared)
        assertEquals("", NullableTextAdapter.String.to(cleared))
        assertEquals(
            EditCommit.Write<String?>(""),
            commitNullableText("https://example", cleared, NullableTextAdapter.String),
        )
        assertEquals(
            EditCommit.Write<String?>(null),
            commitNullableText("https://example", null, NullableTextAdapter.String),
        )
    }

    @Test
    fun rejectedPortTextConfirmsAsInitialAndIsNotWritten() {
        val confirmed = confirmedModelText(
            initial = "80",
            text = "abc",
            accepted = false,
            preserveUnedited = true,
        )
        assertEquals("80", confirmed)
        assertEquals(
            EditCommit.Skip,
            commitNullableText("80", confirmed, NullableTextAdapter.Port),
        )
        assertEquals(
            EditCommit.Skip,
            commitNullableText("80", "abc", NullableTextAdapter.Port),
        )
    }
}
