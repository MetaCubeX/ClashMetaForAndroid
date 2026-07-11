package com.github.kr328.clash.service.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [ProxyGroupsYamlEdit.pruneDanglingProxyGroupReferences] must load-repair a config whose groups
 * reference nodes dropped by a subscription update — and do it FAIL-CLOSED (emptied group → REJECT,
 * never DIRECT, which would leak the real IP on a privacy VPN).
 */
class ProxyGroupsYamlEditRepairTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun profileWith(config: String): File {
        val dir = tmp.newFolder()
        File(dir, "config.yaml").writeText(config.trimIndent())
        return dir
    }

    private fun groupsOf(dir: File): List<Map<*, *>> {
        val root = MihomoConfigDocument.parse(File(dir, "config.yaml").readText())!!.root
        @Suppress("UNCHECKED_CAST")
        return (root["proxy-groups"] as List<Any?>).map { it as Map<*, *> }
    }

    @Test
    fun dropsDanglingMemberButKeepsValidOnes() {
        val dir = profileWith(
            """
            proxies:
              - { name: A, type: socks5, server: 127.0.0.1, port: 1 }
            proxy-groups:
              - { name: Pick, type: select, proxies: [A, GhostNode, DIRECT] }
            rules:
              - MATCH,Pick
            """,
        )

        val repair = ProxyGroupsYamlEdit.pruneDanglingProxyGroupReferences(dir)

        assertEquals(1, repair.removedRefs)
        assertTrue(repair.emptiedGroups.isEmpty())
        val proxies = groupsOf(dir).single()["proxies"] as List<*>
        assertEquals(listOf("A", "DIRECT"), proxies)
    }

    @Test
    fun emptiedGroupBecomesRejectNeverDirect() {
        val dir = profileWith(
            """
            proxies:
              - { name: A, type: socks5, server: 127.0.0.1, port: 1 }
            proxy-groups:
              - { name: Dead, type: select, proxies: [GhostA, GhostB] }
            rules:
              - MATCH,Dead
            """,
        )

        val repair = ProxyGroupsYamlEdit.pruneDanglingProxyGroupReferences(dir)

        assertEquals(2, repair.removedRefs)
        assertEquals(listOf("Dead"), repair.emptiedGroups)
        val proxies = groupsOf(dir).single()["proxies"] as List<*>
        assertEquals(listOf("REJECT"), proxies)
        assertFalse("emptied group must never fall back to DIRECT", proxies.contains("DIRECT"))
    }

    @Test
    fun groupToGroupAndBuiltinReferencesSurvive() {
        val dir = profileWith(
            """
            proxies:
              - { name: A, type: socks5, server: 127.0.0.1, port: 1 }
            proxy-groups:
              - { name: Inner, type: select, proxies: [A] }
              - { name: Outer, type: select, proxies: [Inner, REJECT, A] }
            rules:
              - MATCH,Outer
            """,
        )

        val repair = ProxyGroupsYamlEdit.pruneDanglingProxyGroupReferences(dir)

        assertEquals(0, repair.removedRefs)
        assertTrue(repair.emptiedGroups.isEmpty())
    }

    @Test
    fun providerBackedMembersViaUseAreNotTouched() {
        val dir = profileWith(
            """
            proxy-providers:
              sub1: { type: http, url: https://example.com, path: ./providers/sub1.yaml }
            proxy-groups:
              - { name: G, type: select, use: [sub1], proxies: [GhostNode] }
            rules:
              - MATCH,G
            """,
        )

        val repair = ProxyGroupsYamlEdit.pruneDanglingProxyGroupReferences(dir)

        assertEquals(1, repair.removedRefs)
        assertTrue("group still has `use`, so it must not become REJECT", repair.emptiedGroups.isEmpty())
        val g = groupsOf(dir).single()
        assertEquals(listOf("sub1"), g["use"] as List<*>)
        assertFalse("empty proxies with surviving use should be removed, not REJECT", (g["proxies"] as? List<*>)?.contains("REJECT") == true)
    }

    @Test
    fun cleanConfigIsUnchanged() {
        val dir = profileWith(
            """
            proxies:
              - { name: A, type: socks5, server: 127.0.0.1, port: 1 }
            proxy-groups:
              - { name: Pick, type: select, proxies: [A, DIRECT] }
            rules:
              - MATCH,Pick
            """,
        )
        val before = File(dir, "config.yaml").readText()

        val repair = ProxyGroupsYamlEdit.pruneDanglingProxyGroupReferences(dir)

        assertEquals(0, repair.removedRefs)
        assertEquals("clean config must not be rewritten", before, File(dir, "config.yaml").readText())
    }
}
