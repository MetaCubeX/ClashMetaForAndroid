package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleState
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserLayerStoreTest {
    private val dir: File = Files.createTempDirectory("user-layer").toFile()
    private val store = UserLayerStore(dir)

    @AfterTest fun cleanup() {
        dir.deleteRecursively()
    }

    @Test fun load_missing_returns_empty() {
        val layer = store.load(UUID.randomUUID())
        assertTrue(layer.isEmpty())
    }

    @Test fun save_then_load_roundtrips() {
        val uuid = UUID.randomUUID()
        val layer = UserLayer(
            rules = RuleState(rules = listOf(RuleItem(id = "r1", type = "DOMAIN", value = "example.com", policy = "DIRECT"))),
            dnsHosts = DnsHostsConfig(enable = true, hosts = mapOf("a.test" to "1.2.3.4")),
            proxyProviders = "mine:\n  type: http\n  url: https://x/y",
            proxyChain = mapOf("US-1" to "JP-2"),
        )
        store.save(uuid, layer)
        assertTrue(store.exists(uuid))

        val back = store.load(uuid)
        assertEquals(layer, back)
        assertFalse(back.isEmpty())
    }

    @Test fun saving_empty_layer_removes_file() {
        val uuid = UUID.randomUUID()
        store.save(uuid, UserLayer(dnsHosts = DnsHostsConfig(enable = true)))
        assertTrue(store.exists(uuid))

        store.save(uuid, UserLayer()) // empty → file removed
        assertFalse(store.exists(uuid))
        assertTrue(store.load(uuid).isEmpty())
    }

    @Test fun update_mutates_single_section() {
        val uuid = UUID.randomUUID()
        store.update(uuid) { it.copy(proxyChain = mapOf("US-1" to "JP-2")) }
        store.update(uuid) { it.copy(tunnels = TunnelsConfig()) }
        val back = store.load(uuid)
        assertEquals(mapOf("US-1" to "JP-2"), back.proxyChain)
        assertEquals(TunnelsConfig(), back.tunnels)
    }
}
