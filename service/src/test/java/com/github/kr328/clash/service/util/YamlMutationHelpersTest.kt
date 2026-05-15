package com.github.kr328.clash.service.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YamlMutationHelpersTest {
    @Test
    fun proxyProvidersMergeReplacesOnlyProxyProvidersBlock() {
        val current = """
            mixed-port: 7890
            proxy-providers:
              old:
                type: http
                url: "https://old.example/sub.yaml"
            rules:
              - MATCH,DIRECT
        """.trimIndent()
        val edited = """
            proxy-providers:
              sub1:
                type: http
                url: "https://new.example/sub.yaml"
                path: ./proxies/sub1.yaml
                interval: 3600
        """.trimIndent()

        val merged = ProxyProvidersYamlEdit.mergeIntoConfig(current, edited)

        assertTrue(merged.contains("mixed-port: 7890"))
        assertTrue(merged.contains("sub1:"))
        assertTrue(merged.contains("https://new.example/sub.yaml"))
        assertTrue(merged.contains("- MATCH,DIRECT"))
    }

    @Test
    fun appendSelectGroupMatchesRealMutationOutput() {
        val current = """
            proxy-groups:
              - name: GLOBAL
                type: select
                proxies:
                  - DIRECT
        """.trimIndent()

        val proposed = ProxyGroupsYamlEdit.appendSelectGroupUsingProviders(
            current,
            "MainGroup",
            listOf("sub1", "sub2"),
        )

        val root = YamlFormatting.parseRootMap(proposed.orEmpty())
        val groups = root?.get("proxy-groups") as List<*>
        val global = groups[0] as Map<*, *>
        val merged = groups[1] as Map<*, *>
        assertEquals("MainGroup", merged["name"])
        assertEquals(listOf("sub1", "sub2"), merged["use"])
        assertEquals(listOf("DIRECT", "MainGroup"), global["proxies"])
    }
}
