package com.github.kr328.clash.design.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSetBuiltinWriteTest {
    @Test
    fun defaultUnfrozenSetIsNotStored() {
        assertNull(planBuiltinSetWrite("系统应用", "", "", emptyList(), false))
        assertNull(planBuiltinSetWrite("系统应用", "系统应用", "  ", emptyList(), false))
    }

    @Test
    fun nameAndDescriptionDoNotFreezePackages() {
        val write = planBuiltinSetWrite("系统应用", "我的系统", "备注", listOf("a"), false)
        assertEquals("我的系统", write!!.name)
        assertEquals("备注", write.description)
        assertEquals(emptyList<String>(), write.packages)
        assertFalse(write.membershipFrozen)
    }

    @Test
    fun freezeKeepsPackagesAndBlankNameUsesDefault() {
        val write = planBuiltinSetWrite("浏览器", "  ", "", listOf("a", "b"), true)
        assertEquals("浏览器", write!!.name)
        assertEquals(listOf("a", "b"), write.packages)
        assertTrue(write.membershipFrozen)
    }

    @Test
    fun frozenEmptyListStaysStored() {
        val write = planBuiltinSetWrite("系统应用", "", "", emptyList(), true)
        assertEquals(emptyList<String>(), write!!.packages)
        assertTrue(write.membershipFrozen)
    }

    @Test
    fun unfrozenOverrideFollowsCatalog() {
        assertEquals(
            setOf("live"),
            effectiveBuiltinPackages(setOf("live"), setOf("saved"), frozen = false),
        )
        assertEquals(
            setOf("saved"),
            effectiveBuiltinPackages(setOf("live"), setOf("saved"), frozen = true),
        )
        assertEquals(
            setOf("live"),
            effectiveBuiltinPackages(setOf("live"), null, frozen = false),
        )
    }
}
