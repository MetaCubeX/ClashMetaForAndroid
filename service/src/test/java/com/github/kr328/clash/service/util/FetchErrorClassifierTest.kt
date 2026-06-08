package com.github.kr328.clash.service.util

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FetchErrorClassifierTest {
    private fun dirWith(config: String?): File {
        val dir = Files.createTempDirectory("fec").toFile()
        if (config != null) File(dir, "config.yaml").writeText(config)
        return dir
    }

    private val original = RuntimeException("yaml: line 7: could not find expected ':'")

    @Test fun html_body_becomes_friendly() {
        val out = FetchErrorClassifier.clarify(dirWith("<!DOCTYPE html><html><body>429</body></html>"), original)
        assertTrue(out.message!!.contains("web page"), out.message)
        assertSame(original, out.cause)
    }

    @Test fun empty_body_becomes_friendly() {
        val out = FetchErrorClassifier.clarify(dirWith("   \n  "), original)
        assertTrue(out.message!!.contains("empty response"), out.message)
    }

    @Test fun valid_config_keeps_original() {
        assertSame(original, FetchErrorClassifier.clarify(dirWith("proxies: []\nrules:\n  - MATCH,DIRECT\n"), original))
    }

    @Test fun absent_file_keeps_original() {
        assertSame(original, FetchErrorClassifier.clarify(dirWith(null), original))
    }

    @Test fun html_detection() {
        assertEquals(true, FetchErrorClassifier.looksLikeHtml("<html><head><title>x</title>"))
        assertEquals(true, FetchErrorClassifier.looksLikeHtml("  <!doctype html>"))
        assertEquals(false, FetchErrorClassifier.looksLikeHtml("proxies:\n  - {}"))
        assertEquals(false, FetchErrorClassifier.looksLikeHtml("mixed-port: 7890"))
    }
}
