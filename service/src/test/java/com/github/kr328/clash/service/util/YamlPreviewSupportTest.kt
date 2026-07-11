package com.github.kr328.clash.service.util

import org.junit.Assert.assertTrue
import org.junit.Test

class YamlPreviewSupportTest {
    @Test
    fun unifiedDiffShowsAddedAndRemovedLinesWithContext() {
        val current = """
            port: 7890
            rules:
              - MATCH,DIRECT
        """.trimIndent()
        val proposed = """
            port: 7890
            rules:
              - GEOSITE,openai,Proxy
              - MATCH,DIRECT
        """.trimIndent()

        val diff = YamlPreviewSupport.unifiedDiff(current, proposed)

        assertTrue(diff.contains("+++ proposed"))
        assertTrue(diff.contains("+  - GEOSITE,openai,Proxy"))
        assertTrue(diff.contains("   - MATCH,DIRECT"))
    }

    @Test
    fun unifiedDiffIsLinearAndSummarizedForLargeInputs() {
        val current = (0 until 10_000).joinToString("\n") { "rule-$it: DIRECT" }
        val proposed = (0 until 10_000).joinToString("\n") { "rule-$it: PROXY" }

        val diff = YamlPreviewSupport.unifiedDiff(current, proposed)

        assertTrue(diff.length <= 32 * 1024)
        assertTrue(diff.contains("lines omitted") || diff.contains("diff truncated"))
        assertTrue(diff.startsWith("--- current\n+++ proposed"))
    }

    @Test
    fun previewPayloadTextIsBoundedButKeepsBothEnds() {
        val text = "HEAD" + "x".repeat(200_000) + "TAIL"
        val bounded = YamlPreviewSupport.boundedPreviewText(text)

        assertTrue(bounded.length <= 64 * 1024)
        assertTrue(bounded.startsWith("HEAD"))
        assertTrue(bounded.endsWith("TAIL"))
        assertTrue(bounded.contains("characters omitted"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateConfigYamlRejectsWrongRuleProviderShape() {
        YamlPreviewSupport.validateConfigYaml(
            """
                rule-providers:
                  - bad
            """.trimIndent(),
        )
    }
}
