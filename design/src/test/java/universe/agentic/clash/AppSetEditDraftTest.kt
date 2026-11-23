package universe.agentic.clash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AppSetEditDraftTest {
    @Test
    fun changingTargetDropsTheDraftAndTheDeleteConfirmation() {
        val alpha = AppSetEditData(
            id = "a",
            name = "Alpha",
            packages = setOf("pkg.a"),
            isBuiltin = false,
            description = "about-a",
        )
        val edited = appSetEditDraft(alpha).copy(
            name = "renamed",
            description = "edited",
            query = "qq",
            packages = setOf("only.a"),
            confirmDelete = true,
        )
        assertEquals("a", appSetEditDeleteId(edited))

        val beta = AppSetEditData(
            id = "b",
            name = "Beta",
            packages = setOf("pkg.b"),
            isBuiltin = false,
            description = "about-b",
            membershipFrozen = true,
        )
        val next = reconcileAppSetEditDraft(edited, beta)

        assertEquals("b", next.id)
        assertEquals("Beta", next.name)
        assertEquals("about-b", next.description)
        assertEquals("", next.query)
        assertEquals(setOf("pkg.b"), next.packages)
        assertEquals(false, next.confirmDelete)
        assertEquals(true, next.membershipFrozen)
        assertNull(appSetEditDeleteId(next))

        val saved = appSetEditSavePayload(next, "  ", next.description, next.packages)
        assertEquals("b", saved.id)
        assertEquals("", saved.name)
        assertEquals("about-b", saved.description)
        assertEquals(listOf("pkg.b"), saved.packages)
        assertEquals(true, saved.membershipFrozen)
    }

    @Test
    fun sameTargetKeepsTheDraftAndItsDeleteId() {
        val alpha = AppSetEditData(
            id = "a",
            name = "Alpha",
            packages = setOf("pkg.a"),
            isBuiltin = false,
            description = "about-a",
        )
        val edited = appSetEditDraft(alpha).copy(
            description = "edited",
            query = "q",
            packages = setOf("kept"),
            confirmDelete = true,
        )
        val refreshed = alpha.copy(name = "Alpha 2", packages = setOf("other"), description = "other")

        assertSame(edited, reconcileAppSetEditDraft(edited, refreshed))
        assertEquals("a", appSetEditDeleteId(edited))

        val saved = appSetEditSavePayload(edited, "   ", "  note  ", edited.packages)
        assertEquals("a", saved.id)
        assertEquals("", saved.name)
        assertEquals("note", saved.description)
        assertEquals(listOf("kept"), saved.packages)
    }

    @Test
    fun newSetKeepsItsDraftWhenTheIdStaysUnset() {
        val created = appSetEditDraft(
            AppSetEditData(
                id = null,
                name = "",
                packages = emptySet(),
                isBuiltin = false,
            ),
        ).copy(name = "Mine", packages = setOf("pkg.new"))
        val again = AppSetEditData(
            id = null,
            name = "",
            packages = setOf("other"),
            isBuiltin = false,
        )

        assertSame(created, reconcileAppSetEditDraft(created, again))
        val saved = appSetEditSavePayload(created, created.name, "", created.packages)
        assertNull(saved.id)
        assertEquals("Mine", saved.name)
        assertEquals(listOf("pkg.new"), saved.packages)
        assertNull(appSetEditDeleteId(created.copy(confirmDelete = true)))
    }
}
