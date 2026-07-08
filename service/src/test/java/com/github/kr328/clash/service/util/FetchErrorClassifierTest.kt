package com.github.kr328.clash.service.util

import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
        assertTrue(out.message!!.contains("[E-11]"), out.message)
        assertSame(original, out.cause)
    }

    @Test fun empty_body_becomes_friendly() {
        val out = FetchErrorClassifier.clarify(dirWith("   \n  "), original)
        assertTrue(out.message!!.contains("empty response"), out.message)
        assertTrue(out.message!!.contains("[E-10]"), out.message)
    }

    @Test fun valid_config_keeps_original() {
        assertSame(original, FetchErrorClassifier.clarify(dirWith("proxies: []\nrules:\n  - MATCH,DIRECT\n"), original))
    }

    @Test fun absent_file_non_network_keeps_original() {
        // No body + a non-network error (e.g. a parse/programmer error) → keep original.
        assertSame(original, FetchErrorClassifier.clarify(dirWith(null), original))
    }

    @Test fun absent_file_network_timeout_becomes_friendly() {
        val netErr = RuntimeException("update failed", SocketTimeoutException("failed to connect to raw.githubusercontent.com"))
        val out = FetchErrorClassifier.clarify(dirWith(null), netErr)
        assertTrue(out.message!!.contains("reach the subscription server"), out.message)
        assertTrue(out.message!!.contains("[E-20]"), out.message)
        assertSame(netErr, out.cause)
    }

    @Test fun absent_file_unknown_host_becomes_friendly() {
        val out = FetchErrorClassifier.clarify(dirWith(null), UnknownHostException("Unable to resolve host \"example.com\""))
        assertTrue(out.message!!.contains("[E-20]"), out.message)
    }

    @Test fun network_failure_detection() {
        assertEquals(true, FetchErrorClassifier.looksLikeNetworkFailure(SocketTimeoutException("timeout")))
        assertEquals(true, FetchErrorClassifier.looksLikeNetworkFailure(RuntimeException("connection reset by peer")))
        assertEquals(false, FetchErrorClassifier.looksLikeNetworkFailure(RuntimeException("proxy 'X' not found")))
    }

    @Test fun html_detection() {
        assertEquals(true, FetchErrorClassifier.looksLikeHtml("<html><head><title>x</title>"))
        assertEquals(true, FetchErrorClassifier.looksLikeHtml("  <!doctype html>"))
        assertEquals(false, FetchErrorClassifier.looksLikeHtml("proxies:\n  - {}"))
        assertEquals(false, FetchErrorClassifier.looksLikeHtml("mixed-port: 7890"))
    }
}
