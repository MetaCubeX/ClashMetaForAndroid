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

    @Test
    fun parse_boundsRowsNamesAndMetadataForIpc() {
        val proxies = buildList {
            add(jsonObj("name" to JsonPrimitive("x".repeat(PreviewResourceLimits.MAX_NAME_CHARS + 1))))
            repeat(PreviewResourceLimits.MAX_TRANSPORTS + 50) {
                add(
                    jsonObj(
                        "name" to JsonPrimitive("node-$it"),
                        "type" to JsonPrimitive("t".repeat(500)),
                        "network" to JsonPrimitive("n".repeat(500)),
                    ),
                )
            }
        }

        val out = ProxyTransportYamlPreview.parse(ProfileSnapshot(proxies = proxies))

        assertTrue(out.size <= PreviewResourceLimits.MAX_TRANSPORTS)
        assertFalse(out.containsKey("x".repeat(PreviewResourceLimits.MAX_NAME_CHARS + 1)))
        assertTrue(out.values.all { it.type.length <= 64 && it.network.length <= 64 })
        assertTrue(
            out.entries.sumOf { (name, info) -> name.length + info.type.length + info.network.length } <=
                PreviewResourceLimits.MAX_OUTPUT_CHARS,
        )
    }

    @Test
    fun parse_skipsOversizedProviderFile() {
        val dir = java.nio.file.Files.createTempDirectory("transport-provider-budget").toFile()
        try {
            val provider = dir.resolve("providers/proxies/huge.yaml")
            requireNotNull(provider.parentFile).mkdirs()
            provider.writeText(
                "proxies:\n  - name: hidden\n    type: ss\n#" +
                    "x".repeat(PreviewResourceLimits.MAX_PROVIDER_FILE_BYTES),
            )
            val snapshot = ProfileSnapshot(
                proxyProviders = mapOf(
                    "huge" to jsonObj("path" to JsonPrimitive("./proxies/huge.yaml")),
                ),
            )

            assertTrue(ProxyTransportYamlPreview.parse(snapshot, dir).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
