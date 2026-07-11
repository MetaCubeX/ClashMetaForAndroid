package com.github.kr328.clash.common.util

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SubscriptionNameGuesserTest {
    private class CountingInputStream(private val size: Int) : InputStream() {
        var bytesRead = 0

        override fun read(): Int {
            if (bytesRead >= size) return -1
            bytesRead++
            return 'x'.code
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (bytesRead >= size) return -1
            val count = minOf(length, size - bytesRead)
            buffer.fill('x'.code.toByte(), offset, offset + count)
            bytesRead += count
            return count
        }
    }

    @Test
    fun opaque_token_path_falls_back_to_host_brand() {
        // Regression: the subscription token was used as the profile name.
        val name = SubscriptionNameGuesser.guessFast("https://myvpn.io/gsU8_wQwF814_Eoxyz")
        assertEquals("myvpn", name)
        assertFalse(name.contains("gsU8"), "token must not become the name")
    }

    @Test
    fun readable_path_label_is_kept() {
        assertEquals("premium", SubscriptionNameGuesser.guessFast("https://myvpn.io/premium"))
        assertEquals("My-Plan", SubscriptionNameGuesser.guessFast("https://myvpn.io/My-Plan"))
    }

    @Test
    fun url_fragment_name_wins() {
        assertEquals("HomeVPN", SubscriptionNameGuesser.guessFast("https://myvpn.io/gsU8_wQwF814_Eo#HomeVPN"))
        // Percent-encoded spaces decode: "Home VPN", not "Home%20VPN".
        assertEquals("Home VPN", SubscriptionNameGuesser.guessFast("https://myvpn.io/x#Home%20VPN"))
    }

    // --- titleFromHeaders: shared import/update resolver (E-19) ---

    private fun title(headers: Map<String, String>): String? =
        SubscriptionNameGuesser.titleFromHeaders { key -> headers[key] }

    @Test
    fun title_from_explicit_header() {
        assertEquals("Premium Plan", title(mapOf("Subscription-Title" to "Premium Plan")))
        assertEquals("Premium Plan", title(mapOf("X-Subscription-Title" to "\"Premium Plan\"")))
    }

    // Note: base64 title decoding relies on android.util.Base64 (device-only; stubbed to throw in
    // plain JVM unit tests), so it's exercised on-device via the import path, not asserted here.

    @Test
    fun title_from_content_disposition_rfc5987() {
        assertEquals(
            "Home VPN",
            title(mapOf("Content-Disposition" to "attachment; filename*=UTF-8''Home%20VPN")),
        )
    }

    @Test
    fun title_absent_returns_null() {
        assertEquals(null, title(mapOf("Content-Type" to "text/plain")))
        assertEquals(null, title(emptyMap()))
    }

    @Test
    fun explicit_title_header_beats_content_disposition() {
        // The account-id filename must never win over a real display title.
        assertEquals(
            "Real Name",
            title(
                mapOf(
                    "Subscription-Title" to "Real Name",
                    "Content-Disposition" to "attachment; filename*=UTF-8''BRIDGE_ACCOUNT_ID",
                ),
            ),
        )
    }

    @Test
    fun blank_title_header_falls_through_to_next() {
        assertEquals(
            "Fallback",
            title(mapOf("Subscription-Title" to "   ", "Profile-Title" to "Fallback")),
        )
    }

    @Test
    fun title_header_is_unquoted_and_trimmed() {
        assertEquals("Premium", title(mapOf("Display-Name" to "  \"Premium\"  ")))
    }

    @Test
    fun content_disposition_plain_filename() {
        assertEquals(
            "MyPlan",
            title(mapOf("Content-Disposition" to "attachment; filename=\"MyPlan\"")),
        )
    }

    @Test
    fun url_name_param_fragment_decodes() {
        assertEquals("My VPN", SubscriptionNameGuesser.guessFast("https://myvpn.io/x#name=My%20VPN"))
    }

    @Test
    fun trailing_slash_url_uses_host_brand() {
        assertEquals("myvpn", SubscriptionNameGuesser.guessFast("https://myvpn.io/"))
    }

    // --- looksLikeOpaqueToken: the ONE shared token predicate for import + update (E-17) ---

    @Test
    fun opaque_token_needs_mixed_case_and_digit() {
        assertEquals(true, SubscriptionNameGuesser.looksLikeOpaqueToken("aB3xK9mQ2pLz"))
        assertEquals(true, SubscriptionNameGuesser.looksLikeOpaqueToken("gsU8_wQwF814_Eo"))
    }

    @Test
    fun opaque_token_rejects_human_names() {
        // The E-17 divergence case: all-lowercase "premiumplan1" must NOT be treated as a token,
        // so the update path never overwrites a name import kept.
        assertFalse(SubscriptionNameGuesser.looksLikeOpaqueToken("premiumplan1"))
        assertFalse(SubscriptionNameGuesser.looksLikeOpaqueToken("Premium Plan")) // no digit
        assertFalse(SubscriptionNameGuesser.looksLikeOpaqueToken("PREMIUM123"))   // no lowercase
        assertFalse(SubscriptionNameGuesser.looksLikeOpaqueToken("short1A"))      // core < 8
    }

    @Test
    fun body_read_stops_after_limit_plus_one_byte() {
        val input = CountingInputStream(SubscriptionNameGuesser.MAX_BODY_BYTES * 2)

        assertNull(SubscriptionNameGuesser.readBoundedBody(input, null))
        assertEquals(SubscriptionNameGuesser.MAX_BODY_BYTES + 1, input.bytesRead)
    }

    @Test
    fun oversized_content_length_is_rejected_before_stream_read() {
        val input = CountingInputStream(SubscriptionNameGuesser.MAX_BODY_BYTES * 2)

        assertNull(
            SubscriptionNameGuesser.readBoundedBody(
                input,
                SubscriptionNameGuesser.MAX_BODY_BYTES.toLong() + 1,
            ),
        )
        assertEquals(0, input.bytesRead)
    }

    @Test
    fun lying_content_length_cannot_bypass_stream_limit() {
        val input = CountingInputStream(SubscriptionNameGuesser.MAX_BODY_BYTES * 2)

        assertNull(SubscriptionNameGuesser.readBoundedBody(input, 1))
        assertEquals(SubscriptionNameGuesser.MAX_BODY_BYTES + 1, input.bytesRead)
    }

    @Test
    fun body_at_limit_is_accepted() {
        val expected = ByteArray(SubscriptionNameGuesser.MAX_BODY_BYTES) { 'x'.code.toByte() }

        val actual = SubscriptionNameGuesser.readBoundedBody(
            ByteArrayInputStream(expected),
            expected.size.toLong(),
        )

        assertEquals(expected.size, actual?.size)
    }
}
