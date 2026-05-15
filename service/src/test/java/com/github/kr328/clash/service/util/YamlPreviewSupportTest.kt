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
