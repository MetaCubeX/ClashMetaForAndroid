package com.github.kr328.clash.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SubscriptionNameGuesserTest {
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
}
