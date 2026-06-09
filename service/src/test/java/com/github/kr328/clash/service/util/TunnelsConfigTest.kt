package com.github.kr328.clash.service.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TunnelsConfigTest {
    private fun entry() = TunnelEntry(
        network = listOf("tcp", "udp"),
        address = "127.0.0.1:6553",
        target = "1.1.1.1:53",
        proxy = "G",
    )

    // --- validator ---
    @Test fun valid_loopback_entry_passes() {
        assertNull(TunnelsValidator.validate(entry()))
    }

    @Test fun non_loopback_address_rejected() {
        assertEquals(TunnelsValidator.Error.ADDRESS_INVALID,
            TunnelsValidator.validate(entry().copy(address = "0.0.0.0:6553")))
    }

    @Test fun bare_port_rejected_for_tunnel() {
        assertEquals(TunnelsValidator.Error.ADDRESS_INVALID,
            TunnelsValidator.validate(entry().copy(address = ":6553")))
    }

    @Test fun missing_port_rejected() {
        assertEquals(TunnelsValidator.Error.ADDRESS_INVALID,
            TunnelsValidator.validate(entry().copy(address = "127.0.0.1")))
    }

    @Test fun bad_network_rejected() {
        assertEquals(TunnelsValidator.Error.NETWORK_INVALID,
            TunnelsValidator.validate(entry().copy(network = listOf("icmp"))))
        assertEquals(TunnelsValidator.Error.NETWORK_INVALID,
            TunnelsValidator.validate(entry().copy(network = emptyList())))
    }

    @Test fun empty_target_and_proxy_rejected() {
        assertEquals(TunnelsValidator.Error.TARGET_EMPTY,
            TunnelsValidator.validate(entry().copy(target = "  ")))
        assertEquals(TunnelsValidator.Error.PROXY_EMPTY,
            TunnelsValidator.validate(entry().copy(proxy = "")))
    }

    // --- model ---
    @Test fun toBlock_omits_incomplete_entries() {
        val cfg = TunnelsConfig(listOf(entry(), TunnelEntry(address = "127.0.0.1:1", target = "", proxy = "")))
        val block = cfg.toTunnelsBlock()!!
        assertEquals(1, block.size)
        assertEquals("127.0.0.1:6553", block[0]["address"])
        assertEquals(listOf("tcp", "udp"), block[0]["network"])
    }

    @Test fun empty_config_isEmpty() {
        assertTrue(TunnelsConfig(emptyList()).isEmpty())
    }

    @Test fun from_snapshot_json_parses() {
        val arr = Json.parseToJsonElement(
            """[{"network":["tcp"],"address":"127.0.0.1:7777","target":"t.com:80","proxy":"P"}]""",
        ) as JsonArray
        val cfg = TunnelsConfig.from(arr)
        assertEquals(1, cfg.entries.size)
        assertEquals("127.0.0.1:7777", cfg.entries[0].address)
        assertEquals(listOf("tcp"), cfg.entries[0].network)
        // sanity: the parsed object is an object
        assertTrue(arr[0].jsonObject.containsKey("proxy"))
    }

    // --- yaml round-trip ---
    @Test fun render_adds_block_and_reparses() {
        val base = "proxies:\n  - {name: G, type: socks5, server: 127.0.0.1, port: 1080}\nrules:\n  - MATCH,G\n"
        val out = TunnelsYamlEdit.render(base, TunnelsConfig(listOf(entry())))
        assertTrue(out.contains("tunnels:"), out)
        // re-parse round-trips with the engine-aligned resolver
        val root = YamlFormatting.parseRootMap(out)!!
        @Suppress("UNCHECKED_CAST")
        val tunnels = root["tunnels"] as List<Map<String, Any?>>
        assertEquals("127.0.0.1:6553", tunnels[0]["address"])
        assertEquals(listOf("tcp", "udp"), tunnels[0]["network"])
    }

    @Test fun render_empty_removes_block() {
        val withBlock = "tunnels:\n  - {network: [tcp], address: 127.0.0.1:1, target: t, proxy: P}\nrules:\n  - MATCH,DIRECT\n"
        val out = TunnelsYamlEdit.render(withBlock, TunnelsConfig(emptyList()))
        assertTrue(!out.contains("tunnels:"), "tunnels block should be removed: $out")
    }
}
