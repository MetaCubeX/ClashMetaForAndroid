package com.github.kr328.clash.service.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DnsHostsConfigTest {
    private fun obj(json: String): JsonObject =
        Json.parseToJsonElement(json) as JsonObject

    @Test
    fun reads_dns_and_hosts_from_snapshot_objects() {
        val dns = obj(
            """
            {"enable":true,"enhanced-mode":"fake-ip","listen":"127.0.0.1:0",
             "cache-algorithm":"arc",
             "nameserver":["https://1.1.1.1/dns-query"],
             "direct-nameserver":["https://dns.yandex/dns-query"]}
            """.trimIndent(),
        )
        val hosts = obj("""{"example.com":"1.2.3.4","router.lan":"192.168.1.1"}""")

        val c = DnsHostsConfig.from(dns, hosts)

        assertEquals(true, c.enable)
        assertEquals("fake-ip", c.enhancedMode)
        assertEquals("arc", c.cacheAlgorithm)
        assertEquals(listOf("https://1.1.1.1/dns-query"), c.nameserver)
        assertEquals(listOf("https://dns.yandex/dns-query"), c.directNameserver)
        assertEquals("1.2.3.4", c.hosts["example.com"])
    }

    @Test
    fun serializes_only_set_fields_and_omits_empties() {
        val c = DnsHostsConfig(
            enable = true,
            enhancedMode = "fake-ip",
            nameserver = listOf(" https://1.1.1.1/dns-query ", "  "),
        )
        val dns = c.toDnsBlock()!!
        assertEquals(true, dns["enable"])
        assertEquals("fake-ip", dns["enhanced-mode"])
        assertEquals(listOf("https://1.1.1.1/dns-query"), dns["nameserver"]) // trimmed + blank dropped
        assertTrue(!dns.containsKey("listen"))
        assertTrue(!dns.containsKey("cache-algorithm"))
        assertNull(c.toHostsBlock()) // no hosts
    }

    @Test
    fun empty_model_writes_nothing() {
        val c = DnsHostsConfig()
        assertNull(c.toDnsBlock())
        assertNull(c.toHostsBlock())
        assertTrue(c.isEmpty())
    }

    @Test
    fun hosts_block_drops_blank_entries_or_is_null() {
        val c = DnsHostsConfig(
            hosts = linkedMapOf("a.com" to "1.1.1.1", "" to "2.2.2.2", "b.com" to "  "),
        )
        val hosts = c.toHostsBlock()!!
        assertEquals(mapOf("a.com" to "1.1.1.1"), hosts)
    }

    @Test
    fun listen_validator_allows_loopback_or_empty_only() {
        assertNull(DnsHostsValidator.listenError(null))
        assertNull(DnsHostsValidator.listenError(""))
        assertNull(DnsHostsValidator.listenError("127.0.0.1:0"))
        assertNull(DnsHostsValidator.listenError("[::1]:53"))
        assertNull(DnsHostsValidator.listenError("localhost:53"))
        assertNull(DnsHostsValidator.listenError(":1053")) // empty-host default form
        assertNull(DnsHostsValidator.listenError(":53"))
        assertEquals(
            DnsHostsValidator.Error.LISTEN_NOT_LOOPBACK,
            DnsHostsValidator.listenError("0.0.0.0:53"),
        )
        assertEquals(
            DnsHostsValidator.Error.LISTEN_NOT_LOOPBACK,
            DnsHostsValidator.listenError("192.168.1.5:53"),
        )
    }

    @Test
    fun respect_rules_requires_proxy_server_nameserver() {
        assertNull(DnsHostsValidator.proxyServerNameserverError(false, emptyList()))
        assertNull(DnsHostsValidator.proxyServerNameserverError(true, listOf("https://1.1.1.1/dns-query")))
        assertEquals(
            DnsHostsValidator.Error.RESPECT_RULES_NEEDS_PROXY_NAMESERVER,
            DnsHostsValidator.proxyServerNameserverError(true, listOf("  ")),
        )
    }
}
