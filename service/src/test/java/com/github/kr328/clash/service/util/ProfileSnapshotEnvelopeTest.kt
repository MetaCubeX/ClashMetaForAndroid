package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshotEnvelope
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin coverage for the wire format between Go's
 * `MarshalProfileSnapshotJSON` and Clash.parseProfileSnapshot. We don't
 * load native bridge here — we feed canned envelopes and check that
 * decoding behaves as expected. End-to-end (Go -> JNI -> Kotlin) is
 * covered by Go tests in core/src/main/golang/native/config/snapshot_test.go.
 */
class ProfileSnapshotEnvelopeTest {
    // Same Json config as Clash.parseProfileSnapshot uses. coerceInputValues
    // is required because mihomo marshals absent sections as JSON `null`, not
    // missing keys, and kotlinx.serialization will throw "Expected start of
    // object '{', but had 'n' instead" without it.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun decodesSuccessEnvelopeWithLogicalRules() {
        val raw = """
            {
              "ok": true,
              "snapshot": {
                "rules": [
                  "DOMAIN,example.com,DIRECT",
                  "AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT",
                  "MATCH,GLOBAL"
                ],
                "proxies": [{"name":"node-a","type":"ss"}],
                "proxy-groups": [{"name":"GLOBAL","type":"select"}],
                "proxy-providers": {"sub1":{"type":"http"}},
                "rule-providers": {"myrules":{"type":"http"}}
              }
            }
        """.trimIndent()

        val envelope = json.decodeFromString(ProfileSnapshotEnvelope.serializer(), raw)

        assertTrue(envelope.ok)
        assertNull(envelope.error)
        val snapshot = envelope.snapshot
        assertNotNull("snapshot must be present", snapshot)
        snapshot!!
        assertEquals(3, snapshot.rules.size)
        // Самое важное: logical rule приходит ОДНОЙ строкой со всеми скобками.
        assertEquals(
            "AND,((NETWORK,UDP),(DST-PORT,53)),DIRECT",
            snapshot.rules[1],
        )
        assertEquals(1, snapshot.proxies.size)
        assertEquals(1, snapshot.proxyGroups.size)
        assertTrue(snapshot.proxyProviders.containsKey("sub1"))
        assertTrue(snapshot.ruleProviders.containsKey("myrules"))
    }

    @Test
    fun decodesErrorEnvelopeWithoutSnapshot() {
        val raw = """
            {
              "ok": false,
              "error": "yaml: line 3: did not find expected key"
            }
        """.trimIndent()

        val envelope = json.decodeFromString(ProfileSnapshotEnvelope.serializer(), raw)

        assertEquals(false, envelope.ok)
        assertEquals("yaml: line 3: did not find expected key", envelope.error)
        assertNull(envelope.snapshot)
    }

    @Test
    fun nullOptionalSectionsCoerceToDefaults() {
        // Regression: mihomo's json.Marshal of a RawConfig with no
        // proxy-providers / proxy-groups / etc emits literal `null` for
        // those keys, not "omit". Without coerceInputValues kotlinx.serialization
        // crashed the rules screen with:
        //   "Expected start of the object '{', but had 'n' instead
        //    at path: $.snapshot.proxy-providers"
        // Cover this shape so the regression cannot creep back in.
        val raw = """
            {
              "ok": true,
              "snapshot": {
                "rules": ["MATCH,DIRECT"],
                "proxies": null,
                "proxy-groups": null,
                "proxy-providers": null,
                "rule-providers": null,
                "sub-rules": null,
                "listeners": null
              }
            }
        """.trimIndent()

        val envelope = json.decodeFromString(ProfileSnapshotEnvelope.serializer(), raw)

        assertTrue(envelope.ok)
        val snapshot = envelope.snapshot!!
        assertEquals(1, snapshot.rules.size)
        assertTrue(snapshot.proxies.isEmpty())
        assertTrue(snapshot.proxyGroups.isEmpty())
        assertTrue(snapshot.proxyProviders.isEmpty())
        assertTrue(snapshot.ruleProviders.isEmpty())
        assertTrue(snapshot.subRules.isEmpty())
        assertTrue(snapshot.listeners.isEmpty())
    }

    @Test
    fun missingOptionalSectionsDefaultToEmpty() {
        val raw = """
            {
              "ok": true,
              "snapshot": {
                "rules": ["MATCH,DIRECT"]
              }
            }
        """.trimIndent()

        val envelope = json.decodeFromString(ProfileSnapshotEnvelope.serializer(), raw)

        assertTrue(envelope.ok)
        val snapshot = envelope.snapshot!!
        assertEquals(1, snapshot.rules.size)
        assertTrue(snapshot.proxies.isEmpty())
        assertTrue(snapshot.proxyGroups.isEmpty())
        assertTrue(snapshot.proxyProviders.isEmpty())
        assertTrue(snapshot.ruleProviders.isEmpty())
        assertTrue(snapshot.subRules.isEmpty())
        assertTrue(snapshot.listeners.isEmpty())
    }
}
