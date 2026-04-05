package com.github.kr328.clash.service.util

import org.yaml.snakeyaml.Yaml

/** Extract a single `proxies:` entry as readable YAML (for UI). */
object ProxyYamlPreview {
    fun extractProxyEntry(text: String, proxyName: String): String? {
        val root = YamlFormatting.parseRootMap(text) ?: return null
        val proxies = root["proxies"] as? List<*> ?: return null
        val yaml = YamlFormatting.blockYaml()
        for (raw in proxies) {
            val m = raw as? Map<*, *> ?: continue
            val name = m["name"] as? String ?: continue
            if (name != proxyName) continue
            @Suppress("UNCHECKED_CAST")
            return yaml.dump(m as Map<String, Any?>).trimEnd()
        }
        return null
    }
}
