package com.github.kr328.clash.service.util

import org.yaml.snakeyaml.Yaml

/** Reads [proxy-groups] from a Clash config.yaml without loading the engine. */
object ProxyGroupsYamlPreview {
    @Volatile
    private var lastTextRef: String? = null
    @Volatile
    private var lastResult: Map<String, List<String>> = emptyMap()

    /**
     * Group name → member names for UI preview (offline / non-active profile).
     * - Static members come from [proxies].
     * - Provider-backed groups (`use` only) return an **empty** member list: keys like sub1 are
     *   **not** leaf proxy names. Listing them made users tap “sub2”, then [patchSelector] failed and
     *   [Selector] fell back to the first real node (often the first subscription).
     */
    fun parseProxyNamesByGroup(text: String): Map<String, List<String>> {
        if (text === lastTextRef || text == lastTextRef) return lastResult

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
            val proxies = g["proxies"] as? List<*>
            val use = g["use"] as? List<*>
            val fromProxies = proxies?.mapNotNull { p ->
                when (p) {
                    is String -> p.trim().takeIf { it.isNotEmpty() }
                    else -> p?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                }
            }.orEmpty()
            val useList = use?.mapNotNull { u -> u?.toString()?.trim()?.takeIf { it.isNotEmpty() } }.orEmpty()
            when {
                fromProxies.isNotEmpty() -> out[name] = fromProxies
                useList.isNotEmpty() -> out[name] = emptyList()
                else -> Unit
            }
        }
        lastTextRef = text
        lastResult = out
        return out
    }

    /**
     * Every [proxy-groups] `name` in the config (for rule policy validation / pickers).
     * Prefer this over [parseProxyNamesByGroup].keys — groups with empty or unusual `proxies`/`use`
     * are still listed here.
     */
    fun listProxyGroupNames(text: String): List<String> {
        val root = try {
            Yaml().load<Map<String, Any?>>(text) ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        val groups = root["proxy-groups"] as? List<*> ?: return emptyList()
        return groups.mapNotNull { raw ->
            (raw as? Map<*, *>)?.get("name")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    /** Names of [proxy-groups] that reference proxy-providers via [use] (merged-style). */
    fun listGroupNamesWithUse(text: String): List<String> {
        val root = try {
            Yaml().load<Map<String, Any?>>(text) ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        val groups = root["proxy-groups"] as? List<*> ?: return emptyList()
        val out = ArrayList<String>()
        for (raw in groups) {
            val g = raw as? Map<*, *> ?: continue
            val use = g["use"] as? List<*> ?: continue
            if (use.isEmpty()) continue
            val name = g["name"] as? String ?: continue
            out.add(name)
        }
        return out
    }
}
