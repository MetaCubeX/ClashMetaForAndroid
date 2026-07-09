package com.github.kr328.clash.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgeKeyUrlTest {
    private val key = "AGE-SECRET-KEY-1TZXEAXUUKQ8FFVZMLVRDG47D7VCQ2VRPFXU2LX5UCDTWKQMW3Z4SPKUCAT"

    @Test
    fun splits_key_fragment_from_url() {
        val s = AgeKeyUrl.split("https://panel.example.com/sub/token#$key")
        assertEquals("https://panel.example.com/sub/token", s.source)
        assertEquals(key, s.ageSecretKey)
    }

    @Test
    fun lowercase_key_is_normalized_uppercase() {
        val s = AgeKeyUrl.split("https://x.example/s#${key.lowercase()}")
        assertEquals(key, s.ageSecretKey)
    }

    @Test
    fun plain_url_passes_through() {
        val s = AgeKeyUrl.split("https://panel.example.com/sub/token")
        assertEquals("https://panel.example.com/sub/token", s.source)
        assertNull(s.ageSecretKey)
    }

    @Test
    fun non_key_fragment_is_left_alone() {
        // A regular anchor fragment is not ours to strip.
        val s = AgeKeyUrl.split("https://panel.example.com/sub/token#section-2")
        assertEquals("https://panel.example.com/sub/token#section-2", s.source)
        assertNull(s.ageSecretKey)
    }

    @Test
    fun empty_input_passes_through() {
        val s = AgeKeyUrl.split("")
        assertEquals("", s.source)
        assertNull(s.ageSecretKey)
    }
}
