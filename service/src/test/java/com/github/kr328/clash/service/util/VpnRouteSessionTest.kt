package com.github.kr328.clash.service.util

import org.junit.Assert.*
import org.junit.Test

class VpnRouteSessionTest {
    @Test
    fun initialEmptyConfigEstablishesOnce() {
        var calls = 0
        val session = VpnRouteSession { calls++ }
        assertEquals(0, calls)
        session.update(emptyList())
        session.update(emptyList())
        assertEquals(1, calls)
    }

    @Test
    fun reloadChangesAndRemovalReplaceRoutes() {
        val applied = mutableListOf<List<String>>()
        val session = VpnRouteSession { applied.add(it.toList()) }
        val a = listOf("192.0.2.0/24")
        val b = listOf("2001:db8::/32")
        session.update(a)
        session.update(a)
        session.update(b)
        session.update(emptyList())
        assertEquals(listOf(a, b, emptyList<String>()), applied)
    }

    @Test
    fun failedApplyIsNotRemembered() {
        var calls = 0
        val session = VpnRouteSession { if (++calls == 1) error("Builder rejected") }
        assertThrows(IllegalStateException::class.java) { session.update(emptyList()) }
        session.update(emptyList())
        assertEquals(2, calls)
    }
}
