package com.github.kr328.clash.companion.protocol

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/**
 * Byte-for-byte conformance against the clash-companion golden vectors (PROTOCOL.md §11).
 * A one-byte divergence here means ClashFest and the Go reference are not interoperable.
 */
class VectorConformanceTest {

    @Test
    fun canonicalJson() {
        val cases = Vectors.json("canonical_json.json").jsonObject["cases"]!!.jsonArray
        for (case in cases) {
            val obj = case.jsonObject
            val name = obj["name"]!!.jsonPrimitive.content
            val input = obj["input"]!!
            val expected = obj["canonical"]!!.jsonPrimitive.content
            assertEquals("canonical_json case: $name", expected, CanonicalJson.encode(input))
        }
    }

    @Test
    fun ids() {
        val root = Vectors.json("ids.json").jsonObject

        val device = root["deviceId"]!!.jsonObject
        assertEquals(
            device["encoded"]!!.jsonPrimitive.content,
            Base64Url.encode(Hex.decode(device["rawHex"]!!.jsonPrimitive.content)),
        )

        val token = root["token"]!!.jsonObject
        assertEquals(
            token["encoded"]!!.jsonPrimitive.content,
            Base64Url.encode(Hex.decode(token["rawHex"]!!.jsonPrimitive.content)),
        )

        val hash = root["tokenHash"]!!.jsonObject
        assertEquals(
            hash["sha256"]!!.jsonPrimitive.content,
            Ids.tokenHash(hash["token"]!!.jsonPrimitive.content),
        )
    }

    @Test
    fun base64UrlRoundTrips() {
        val device = Vectors.json("ids.json").jsonObject["deviceId"]!!.jsonObject
        val raw = Hex.decode(device["rawHex"]!!.jsonPrimitive.content)
        assertEquals(
            device["rawHex"]!!.jsonPrimitive.content,
            Hex.encode(Base64Url.decode(Base64Url.encode(raw))),
        )
    }

    @Test
    fun fingerprint() {
        val root = Vectors.json("fingerprint.json").jsonObject
        val der = Base64.getDecoder().decode(root["certDer"]!!.jsonPrimitive.content)
        assertEquals(root["fp"]!!.jsonPrimitive.content, Fingerprint.of(der))
    }

    @Test
    fun discoveryTxt() {
        val root = Vectors.json("discovery_txt.json").jsonObject
        val fields = root["fields"]!!.jsonObject
        val txt = DiscoveryTxt(
            app = fields["App"]!!.jsonPrimitive.content,
            id = fields["ID"]!!.jsonPrimitive.content,
            name = fields["Name"]!!.jsonPrimitive.content,
            ver = fields["Ver"]!!.jsonPrimitive.int,
            fp = fields["FP"]!!.jsonPrimitive.content,
        )

        val expected = root["encoded"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(expected, txt.encode())

        // decode tolerates any order
        assertEquals(txt, DiscoveryTxt.decode(expected.shuffled()))
    }

    @Test
    fun pairing() {
        val cases = Vectors.json("pairing.json").jsonObject["cases"]!!.jsonArray
        for (case in cases) {
            val obj = case.jsonObject
            val name = obj["name"]!!.jsonPrimitive.content
            val uri = obj["uri"]!!.jsonPrimitive.content
            val decodes = obj["decodes"]!!.jsonPrimitive.boolean
            val fields = obj["fields"]?.jsonObject

            if (fields != null) {
                val payload = PairingPayload(
                    ip = fields["ip"]!!.jsonPrimitive.content,
                    port = fields["port"]!!.jsonPrimitive.int,
                    id = fields["id"]!!.jsonPrimitive.content,
                    fp = fields["fp"]!!.jsonPrimitive.content,
                    token = fields["token"]!!.jsonPrimitive.content,
                    name = fields["name"]?.jsonPrimitive?.content,
                    app = fields["app"]?.jsonPrimitive?.content,
                )
                // Encode(fields) must equal uri exactly.
                assertEquals("pairing encode: $name", uri, payload.toUri())
                // Decode(uri) must reproduce fields.
                assertEquals("pairing decode: $name", payload, PairingPayload.parse(uri))
            } else if (decodes) {
                assertNotNull("pairing should decode: $name", PairingPayload.parse(uri))
            } else {
                assertNull("pairing should reject: $name", PairingPayload.parse(uri))
            }
        }
    }
}
