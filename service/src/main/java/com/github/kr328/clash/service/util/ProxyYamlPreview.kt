package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Extract a single `proxies:` entry as readable YAML (for UI). */
object ProxyYamlPreview {
    fun extractProxyEntry(snapshot: ProfileSnapshot, proxyName: String): String? {
        val match = snapshot.proxies.firstOrNull { entry ->
            entry.proxyName() == proxyName
        } ?: return null
        val yaml = YamlFormatting.blockYaml()
        return yaml.dump(JsonElementToYaml.convertObject(match)).trimEnd()
    }

    private fun JsonObject.proxyName(): String? = runCatching {
        this["name"]?.jsonPrimitive?.content
    }.getOrNull()
}
