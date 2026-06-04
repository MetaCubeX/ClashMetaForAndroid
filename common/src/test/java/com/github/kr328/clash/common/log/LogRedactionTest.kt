package com.github.kr328.clash.common.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coverage for log/UI redaction so URLs, tokens and credentials never leak into logs. */
class LogRedactionTest {

    @Test
    fun redactsHttpAndHttpsUrls() {
        assertEquals("see [redacted]", LogRedaction.redactSuspicious("see https://sub.example.com/path?token=abc"))
        assertEquals("see [redacted]", LogRedaction.redactSuspicious("see http://example.com/x"))
        // Case-insensitive scheme.
        assertEquals("[redacted]", LogRedaction.redactSuspicious("HTTPS://EXAMPLE.COM/a"))
    }

    @Test
    fun redactsBearerTokenAndCredentials() {
        assertEquals("auth [redacted]", LogRedaction.redactSuspicious("auth Bearer eyJhbGciOi.payload.sig"))
        assertEquals("q [redacted]", LogRedaction.redactSuspicious("q token=deadbeef"))
        assertEquals("q [redacted]", LogRedaction.redactSuspicious("q password=hunter2"))
    }

    @Test
    fun redactsMultipleSecretsInOneMessage() {
        val out = LogRedaction.redactSuspicious("GET https://a.b/c failed with token=xyz")
        assertFalse("url leaked: $out", out.contains("a.b"))
        assertFalse("token leaked: $out", out.contains("xyz"))
        assertTrue(out.contains("[redacted]"))
    }

    @Test
    fun leavesPlainTextUntouched() {
        val msg = "connection reset by peer"
        assertEquals(msg, LogRedaction.redactSuspicious(msg))
    }

    @Test
    fun throwableMessage_usesMessageAndRedacts() {
        val out = LogRedaction.throwableMessage(IllegalStateException("failed https://secret.example.com"))
        assertFalse(out.contains("secret.example.com"))
        assertTrue(out.contains("[redacted]"))
    }

    @Test
    fun throwableMessage_fallsBackToClassNameWhenNoMessage() {
        assertEquals("IllegalStateException", LogRedaction.throwableMessage(IllegalStateException()))
    }
}
