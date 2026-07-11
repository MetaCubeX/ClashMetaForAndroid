package com.github.kr328.clash.service.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MihomoConfigDocumentLinearTest {
    @Test
    fun replaceHandlesLongTopLevelCommentPreambleInOnePass() {
        val comments = (0 until 20_000).joinToString("\n") { "# next block comment $it" }
        val yaml = "proxies:\n" +
            "  - name: local\n" +
            "    type: direct\n" +
            "geox-url:\n" +
            "  geoip: https://untrusted.example/geoip.dat\n" +
            comments + "\n" +
            "rules:\n" +
            "  - MATCH,DIRECT\n"
        val document = MihomoConfigDocument.parseOrThrow(yaml)
        document.root["geox-url"] = mapOf("geoip" to "https://github.com/safe/geoip.dat")

        val rendered = document.renderReplacing("geox-url")

        assertTrue(rendered.contains("geoip: https://github.com/safe/geoip.dat"))
        assertTrue(rendered.contains("# next block comment 0"))
        assertTrue(rendered.indexOf("# next block comment 0") < rendered.indexOf("rules:"))
        assertEquals(20_000, rendered.lineSequence().count { it.startsWith("# next block comment ") })
    }
}
