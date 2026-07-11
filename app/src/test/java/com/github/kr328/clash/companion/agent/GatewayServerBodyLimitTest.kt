package com.github.kr328.clash.companion.agent

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GatewayServerBodyLimitTest {
    @Test
    fun contentLengthMustBePresentNumericAndBounded() {
        assertFalse(GatewayServer.isAcceptedContentLength(null, 16))
        assertFalse(GatewayServer.isAcceptedContentLength("", 16))
        assertFalse(GatewayServer.isAcceptedContentLength("chunked", 16))
        assertFalse(GatewayServer.isAcceptedContentLength("-1", 16))
        assertFalse(GatewayServer.isAcceptedContentLength("17", 16))
        assertTrue(GatewayServer.isAcceptedContentLength("0", 16))
        assertTrue(GatewayServer.isAcceptedContentLength(" 16 ", 16))
    }

    @Test
    fun extractedJsonIsBoundedByUtf8Bytes() {
        assertTrue(GatewayServer.isAcceptedBodyText("1234", 4))
        assertFalse(GatewayServer.isAcceptedBodyText("12345", 4))
        assertTrue(GatewayServer.isAcceptedBodyText("я", 2))
        assertFalse(GatewayServer.isAcceptedBodyText("яя", 2))
    }
}
