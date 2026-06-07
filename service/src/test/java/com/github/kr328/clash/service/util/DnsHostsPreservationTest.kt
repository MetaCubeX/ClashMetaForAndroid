package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DnsHostsPreservationTest {
    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    private val userSnapshot = ProfileSnapshot(
        dns = obj("""{"enable":true,"nameserver":["1.1.1.1"]}"""),
        hosts = obj("""{"a.com":"9.9.9.9"}"""),
    )

    // The fresh subscription ships its own (different) dns/hosts.
    private val fetched = """
        mixed-port: 7890
        proxies:
          - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}
        dns:
          enable: false
          nameserver:
            - 8.8.8.8
        hosts:
          a.com: 1.1.1.1
    """.trimIndent() + "\n"

    @Test
    fun managed_profile_replaces_fetched_dns_hosts() {
        val preserved = SubscriptionUpdateMerge.extractPreserved(userSnapshot, includeDnsHosts = true)
        assertNotNull(preserved.dns)
        assertNotNull(preserved.hosts)

        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved)

        assertTrue(merged.contains("1.1.1.1"), "user nameserver kept")
        assertFalse(merged.contains("8.8.8.8"), "fetched nameserver replaced")
        assertTrue(merged.contains("9.9.9.9"), "user host kept")
        assertTrue(merged.contains("proxies:"), "unrelated preserved")
    }

    @Test
    fun unmanaged_profile_keeps_subscription_dns_hosts() {
        val preserved = SubscriptionUpdateMerge.extractPreserved(userSnapshot, includeDnsHosts = false)
        assertNull(preserved.dns)
        assertNull(preserved.hosts)

        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved)

        assertTrue(merged.contains("8.8.8.8"), "subscription nameserver kept")
        assertFalse(merged.contains("1.1.1.1") && !merged.contains("8.8.8.8"))
    }
}
