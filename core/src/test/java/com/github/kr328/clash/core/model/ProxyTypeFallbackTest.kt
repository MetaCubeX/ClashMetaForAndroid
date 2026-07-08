package com.github.kr328.clash.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Proxy.Type.FallbackSerializer] guards the JNI JSON decode path
 * ([com.github.kr328.clash.core.Clash.queryGroup]): the Go side sends
 * `p.Type().String()` verbatim, so every mihomo outbound type the enum does
 * not list yet must decode to [Proxy.Type.Unknown] instead of throwing a
 * SerializationException and blanking the proxy screen (a group with a single
 * Rematch/OpenVPN/Tailscale member used to fail the whole group decode).
 */
class ProxyTypeFallbackTest {

    private fun decodeProxy(type: String): Proxy = Json.decodeFromString(
        Proxy.serializer(),
        """{"name":"n","title":"t","subtitle":"s","type":"$type","delay":0}""",
    )

    @Test
    fun known_types_decode_exactly() {
        assertEquals(Proxy.Type.Selector, decodeProxy("Selector").type)
        assertEquals(Proxy.Type.Vless, decodeProxy("Vless").type)
        assertEquals(Proxy.Type.Rematch, decodeProxy("Rematch").type)
        assertEquals(Proxy.Type.PassRule, decodeProxy("PassRule").type)
        assertEquals(Proxy.Type.OpenVPN, decodeProxy("OpenVPN").type)
        assertEquals(Proxy.Type.Tailscale, decodeProxy("Tailscale").type)
        assertEquals(Proxy.Type.GostRelay, decodeProxy("GostRelay").type)
    }

    @Test
    fun unknown_type_falls_back_instead_of_throwing() {
        assertEquals(Proxy.Type.Unknown, decodeProxy("SomeFutureMihomoType").type)
    }

    @Test
    fun decode_is_case_insensitive() {
        // mihomo's String() casing is stable today, but a fallback path should
        // not depend on it.
        assertEquals(Proxy.Type.URLTest, decodeProxy("urltest").type)
    }

    @Test
    fun encode_uses_enum_name() {
        val json = Json.encodeToString(Proxy.serializer(), Proxy("n", "t", "s", Proxy.Type.Rematch, 0))
        assertEquals(true, json.contains("\"Rematch\""))
    }
}
