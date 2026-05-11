package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.service.model.ProxyGroupPreviewRow
import org.yaml.snakeyaml.Yaml

/** Reads [proxy-groups] from a Clash config.yaml without loading the engine. */
object ProxyGroupsYamlPreview {
    @Volatile
    private var lastTextRef: String? = null
    @Volatile
    private var lastResult: Map<String, ProxyGroupPreviewRow> = emptyMap()

    private fun truthyHidden(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> {
                val t = value.trim()
                t.equals("true", ignoreCase = true) ||
                    t == "1" ||
                    t.equals("yes", ignoreCase = true)
            }
            else -> false
        }
    }

    private fun yamlGroupType(g: Map<*, *>): Proxy.Type {
        val raw = g["type"] ?: return Proxy.Type.Selector
        val s = raw.toString().trim().lowercase()
        return when (s) {
            "select" -> Proxy.Type.Selector
            "fallback" -> Proxy.Type.Fallback
            "url-test" -> Proxy.Type.URLTest
            "load-balance" -> Proxy.Type.LoadBalance
            "relay" -> Proxy.Type.Relay
            else -> Proxy.Type.Selector
        }
    }

    /**
     * Group name → type + members for UI preview (offline / non-active profile).
     * - Skips `hidden: true` (matches mihomo UI list).
     * - Static members come from [proxies].
     * - Provider-backed groups (`use` only) return an **empty** member list: keys like sub1 are
     *   **not** leaf proxy names. Listing them made users tap “sub2”, then [patchSelector] failed and
     *   [Selector] fell back to the first real node (often the first subscription).
     */
    fun parseProxyGroupsPreview(text: String): Map<String, ProxyGroupPreviewRow> {
        if (text === lastTextRef || text == lastTextRef) return lastResult

        val root = try {
            Yaml().load<Map<String, Any?>>(text) ?: return emptyMap()
        } catch (_: Exception) {
            return emptyMap()
        }
        val groups = root["proxy-groups"] as? List<*> ?: return emptyMap()
        val out = linkedMapOf<String, ProxyGroupPreviewRow>()
        for (raw in groups) {
            val g = raw as? Map<*, *> ?: continue
            if (truthyHidden(g["hidden"])) continue
            val name = g["name"] as? String ?: continue
            val type = yamlGroupType(g)
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
                fromProxies.isNotEmpty() -> out[name] = ProxyGroupPreviewRow(type, fromProxies)
                useList.isNotEmpty() -> out[name] = ProxyGroupPreviewRow(type, emptyList())
                else -> Unit
            }
        }
        lastTextRef = text
        lastResult = out
        return out
    }

    /** @see parseProxyGroupsPreview — members only (compatibility). */
    fun parseProxyNamesByGroup(text: String): Map<String, List<String>> =
        parseProxyGroupsPreview(text).mapValues { it.value.members }

    /**
     * Every [proxy-groups] `name` in the config (for rule policy validation / pickers).
     * Prefer this over [parseProxyGroupsPreview].keys when **hidden** groups must still appear
     * (e.g. rule targets) — groups with empty or unusual `proxies`/`use` are still listed here. Includes **hidden** names (valid rule targets).
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
