package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyTransportYamlPreviewTest {

    private fun jsonObj(vararg pairs: Pair<String, JsonElement>): JsonObject =
        JsonObject(pairs.toMap())

    @Test
    fun parse_extractsNetworkTlsAndReality() {
        val snapshot = ProfileSnapshot(
            proxies = listOf(
                jsonObj(
                    "name" to JsonPrimitive("ws-tls-node"),
                    "type" to JsonPrimitive("vmess"),
                    "network" to JsonPrimitive("ws"),
                    "tls" to JsonPrimitive(true),
                ),
                jsonObj(
                    "name" to JsonPrimitive("reality-node"),
                    "type" to JsonPrimitive("vless"),
                    "network" to JsonPrimitive("tcp"),
                    "reality-opts" to jsonObj(
                        "public-key" to JsonPrimitive("abc"),
                    ),
                ),
                jsonObj(
                    "name" to JsonPrimitive("plain-node"),
                    "type" to JsonPrimitive("ss"),
                ),
            ),
        )

        val out = ProxyTransportYamlPreview.parse(snapshot)

        val ws = assertNotNull("ws-tls-node should be present", out["ws-tls-node"]).let { out["ws-tls-node"]!! }
        assertEquals("ws", ws.network)
        assertTrue(ws.tls)
        assertFalse(ws.reality)

        val reality = out["reality-node"]!!
        assertEquals("tcp", reality.network)
        assertTrue("reality-opts non-empty → reality=true", reality.reality)

        val plain = out["plain-node"]!!
        assertEquals("", plain.network)
        assertFalse(plain.tls)
        assertFalse(plain.reality)
    }

    @Test
    fun parse_emptyRealityOptsDoesNotEnableReality() {
        val snapshot = ProfileSnapshot(
            proxies = listOf(
                jsonObj(
                    "name" to JsonPrimitive("nope"),
                    "type" to JsonPrimitive("vless"),
                    "reality-opts" to JsonObject(emptyMap()),
                ),
            ),
        )
        val out = ProxyTransportYamlPreview.parse(snapshot)
        assertFalse(out["nope"]!!.reality)
    }

    @Test
    fun parse_skipsProxiesWithoutName() {
        val snapshot = ProfileSnapshot(
            proxies = listOf(
                jsonObj("type" to JsonPrimitive("ss")), // no name
                jsonObj("name" to JsonPrimitive("named"), "type" to JsonPrimitive("ss")),
            ),
        )
        val out = ProxyTransportYamlPreview.parse(snapshot)
        assertEquals(setOf("named"), out.keys)
    }
}
