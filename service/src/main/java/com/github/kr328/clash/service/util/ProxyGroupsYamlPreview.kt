package com.github.kr328.clash.service.util

import org.yaml.snakeyaml.Yaml

/** Reads [proxy-groups] from a Clash config.yaml without loading the engine. */
object ProxyGroupsYamlPreview {
    @Volatile
    private var lastHash: Int = 0
    @Volatile
    private var lastResult: Map<String, List<String>> = emptyMap()

    fun parseProxyNamesByGroup(text: String): Map<String, List<String>> {
        val hash = text.hashCode()
        if (hash == lastHash) return lastResult

        val root = try {
            Yaml().load<Map<String, Any?>>(text) ?: return emptyMap()
        } catch (_: Exception) {
            return emptyMap()
        }
        val groups = root["proxy-groups"] as? List<*> ?: return emptyMap()
        val out = linkedMapOf<String, List<String>>()
        for (raw in groups) {
            val g = raw as? Map<*, *> ?: continue
            val name = g["name"] as? String ?: continue
            val proxies = g["proxies"] as? List<*> ?: continue
            val names = proxies.mapNotNull { p ->
                when (p) {
                    is String -> p
                    else -> p?.toString()
                }
            }
            out[name] = names
        }
        lastHash = hash
        lastResult = out
        return out
    }
}
