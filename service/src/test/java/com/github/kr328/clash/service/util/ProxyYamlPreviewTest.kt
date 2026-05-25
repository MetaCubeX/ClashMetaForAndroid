package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyYamlPreviewTest {

    private fun proxy(name: String, type: String, vararg extra: Pair<String, String>): JsonObject {
        val map = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        map["name"] = JsonPrimitive(name)
        map["type"] = JsonPrimitive(type)
        for ((k, v) in extra) map[k] = JsonPrimitive(v)
        return JsonObject(map)
    }

    @Test
    fun extractProxyEntry_returnsYamlForMatchingName() {
        val snapshot = ProfileSnapshot(
            proxies = listOf(
                proxy("node-a", "ss", "server" to "1.2.3.4", "port" to "8388"),
                proxy("node-b", "vmess", "server" to "5.6.7.8"),
            ),
        )
        val yaml = ProxyYamlPreview.extractProxyEntry(snapshot, "node-a")
        assertTrue("entry must contain proxy name, was: $yaml", yaml.orEmpty().contains("node-a"))
        assertTrue("entry must contain type, was: $yaml", yaml.orEmpty().contains("ss"))
        assertTrue("entry must contain server, was: $yaml", yaml.orEmpty().contains("1.2.3.4"))
    }

    @Test
    fun extractProxyEntry_returnsNullForUnknownName() {
        val snapshot = ProfileSnapshot(
            proxies = listOf(proxy("node-a", "ss")),
        )
        assertNull(ProxyYamlPreview.extractProxyEntry(snapshot, "missing"))
    }

    @Test
    fun extractProxyEntry_returnsNullOnEmptySnapshot() {
        assertNull(ProxyYamlPreview.extractProxyEntry(ProfileSnapshot(), "anything"))
    }
}
