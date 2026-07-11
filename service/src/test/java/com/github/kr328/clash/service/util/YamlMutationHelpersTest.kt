package com.github.kr328.clash.service.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class YamlMutationHelpersTest {
    @Test
    fun proxyProvidersMergeReplacesOnlyProxyProvidersBlock() {
        val current = """
            # keep header comment
            mixed-port: 7890
            proxy-providers:
              old:
                type: http
                url: "https://old.example/sub.yaml"
            # keep rules comment
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
        assertTrue(merged.contains("# keep header comment"))
        assertTrue(merged.contains("# keep rules comment"))
        assertTrue(merged.contains("sub1:"))
        assertTrue(merged.contains("https://new.example/sub.yaml"))
        assertTrue(!merged.contains("https://old.example/sub.yaml"))
        assertTrue(merged.contains("- MATCH,DIRECT"))
    }

    @Test
    fun ruleProvidersMergePreservesUnrelatedBlocksAndComments() {
        val current = """
            # general comment
            mixed-port: 7890
            rule-providers:
              old:
                type: http
                behavior: classical
                url: "https://old.example/rules.yaml"
            # dns stays byte-visible
            dns:
              enable: true
              nameserver:
                - 1.1.1.1
        """.trimIndent()
        val edited = """
            rule-providers:
              local:
                type: file
                behavior: classical
                path: ./rules/local.yaml
        """.trimIndent()

        val merged = RuleProvidersYamlEdit.mergeIntoConfig(current, edited)

        assertTrue(merged.contains("# general comment"))
        assertTrue(merged.contains("# dns stays byte-visible"))
        assertTrue(merged.contains("dns:"))
        assertTrue(merged.contains("- 1.1.1.1"))
        assertTrue(merged.contains("local:"))
        assertTrue(!merged.contains("old.example"))
    }

    @Test
    fun appendSelectGroupMatchesRealMutationOutput() {
        val current = """
            # top-level comment before groups
            proxy-groups:
              - name: GLOBAL
                type: select
                proxies:
                  - DIRECT
            # rules block must survive
            rules:
              - MATCH,DIRECT
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
        assertTrue(proposed.orEmpty().contains("# top-level comment before groups"))
        assertTrue(proposed.orEmpty().contains("# rules block must survive"))
        assertTrue(proposed.orEmpty().contains("- MATCH,DIRECT"))
        assertEquals("MainGroup", merged["name"])
        assertEquals(listOf("sub1", "sub2"), merged["use"])
        assertEquals(listOf("DIRECT", "MainGroup"), global["proxies"])
    }

    @Test
    fun dialerProxyPatchPreservesUnrelatedConfigBlocks() {
        val dir = Files.createTempDirectory("clashfest-yaml-test").toFile()
        try {
            dir.resolve("config.yaml").writeText(
                """
                    proxies:
                      - name: node-a
                        type: ss
                        server: example.com
                        port: 443
                    # rules comment survives
                    rules:
                      - MATCH,DIRECT
                """.trimIndent(),
            )

            val patch = ProxyDialerYamlEdit.previewDialerProxy(dir, "node-a", "DIRECT")

            val proposed = patch?.proposedYaml.orEmpty()
            assertEquals("config.yaml", patch?.relativePath)
            assertTrue(proposed.contains("dialer-proxy: DIRECT"))
            assertTrue(proposed.contains("# rules comment survives"))
            assertTrue(proposed.contains("- MATCH,DIRECT"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun providerPathResolverConfinesPathsToProviderSandbox() {
        val dir = Files.createTempDirectory("clashfest-provider-path").toFile()
        try {
            val valid = dir.resolve("providers/proxies/sub1.yaml")
            requireNotNull(valid.parentFile).mkdirs()
            valid.writeText("proxies: []")

            assertEquals(
                valid.canonicalFile,
                ProxyDialerYamlEdit.resolveProviderPath(dir, "./proxies/sub1.yaml"),
            )
            assertEquals(
                valid.canonicalFile,
                ProxyDialerYamlEdit.resolveProviderPath(dir, valid.absolutePath),
            )
            assertNull(ProxyDialerYamlEdit.resolveProviderPath(dir, "../config.yaml"))
            assertNull(ProxyDialerYamlEdit.resolveProviderPath(dir, dir.resolve("config.yaml").absolutePath))
            assertNull(
                ProxyDialerYamlEdit.resolveProviderPath(
                    dir,
                    requireNotNull(dir.parentFile).resolve("sibling.yaml").absolutePath,
                ),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun dialerProxyCannotPatchTraversalTargetButCanPatchProviderFile() {
        val root = Files.createTempDirectory("clashfest-provider-edit").toFile()
        val profile = root.resolve("profile").apply { mkdirs() }
        val sibling = root.resolve("sibling.yaml").apply {
            writeText("proxies:\n  - name: escaped\n    type: ss\n")
        }
        try {
            profile.resolve("config.yaml").writeText(
                """
                    proxy-providers:
                      hostile:
                        type: file
                        path: ../../sibling.yaml
                """.trimIndent(),
            )
            assertFalse(ProxyDialerYamlEdit.applyDialerProxy(profile, "escaped", "DIRECT"))
            assertFalse("dialer-proxy" in sibling.readText())

            val provider = profile.resolve("providers/proxies/sub1.yaml")
            requireNotNull(provider.parentFile).mkdirs()
            provider.writeText("proxies:\n  - name: safe\n    type: ss\n")
            profile.resolve("config.yaml").writeText(
                """
                    proxy-providers:
                      safe:
                        type: file
                        path: ./proxies/sub1.yaml
                """.trimIndent(),
            )
            assertTrue(ProxyDialerYamlEdit.applyDialerProxy(profile, "safe", "DIRECT"))
            assertTrue("dialer-proxy: DIRECT" in provider.readText())

            val providerNamedConfig = profile.resolve("providers/config.yaml")
            providerNamedConfig.writeText("proxies:\n  - name: provider-config\n    type: ss\n")
            profile.resolve("config.yaml").writeText(
                """
                    proxy-providers:
                      ambiguous:
                        type: file
                        path: config.yaml
                """.trimIndent(),
            )
            assertTrue(ProxyDialerYamlEdit.applyDialerProxy(profile, "provider-config", "DIRECT"))
            assertTrue("dialer-proxy: DIRECT" in providerNamedConfig.readText())
            assertFalse("dialer-proxy" in profile.resolve("config.yaml").readText())
        } finally {
            root.deleteRecursively()
        }
    }
}
