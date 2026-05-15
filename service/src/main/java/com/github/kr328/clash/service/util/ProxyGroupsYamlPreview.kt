package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.service.model.ProxyGroupPreviewRow
import org.yaml.snakeyaml.Yaml
import java.io.File

/** Reads [proxy-groups] from a Clash config.yaml without loading the engine. */
object ProxyGroupsYamlPreview {
    @Volatile
    private var lastTextRef: String? = null
    @Volatile
    private var lastProfileDirKey: String? = null
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

    /** Mihomo expands membership at runtime (`include-all-proxies`, etc.). */
    private fun hasDynamicMembershipFromYaml(g: Map<*, *>): Boolean {
        return truthyHidden(g["include-all-proxies"]) ||
            truthyHidden(g["include-all"]) ||
            truthyHidden(g["include-all-providers"])
    }

    /**
     * Same notion as mihomo [github.com/metacubex/mihomo/config.parseProxies] `AllProxies`:
     * inline `proxies:` names plus, when [profileDir] is set, leaf names from each
     * `proxy-providers` file on disk (matches include-all-proxies filter semantics at parse time).
     */
    private fun collectAllLeafProxyNames(root: Map<String, Any?>, profileDir: File?): LinkedHashSet<String> {
        val names = LinkedHashSet<String>()
        (root["proxies"] as? List<*>)?.forEach { raw ->
            val n = (raw as? Map<*, *>)?.get("name")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            if (n != null) names.add(n)
        }
        val dir = profileDir?.takeIf { it.isDirectory } ?: return names
        val pp = root["proxy-providers"] as? Map<*, *> ?: return names
        for ((_, v) in pp) {
            val prov = v as? Map<*, *> ?: continue
            val pathStr = prov["path"] as? String ?: continue
            val f = ProxyDialerYamlEdit.resolveProviderPath(dir, pathStr)
            if (!f.isFile) continue
            val pRoot = runCatching { YamlFormatting.parseRootMap(f.readText()) }.getOrNull() ?: continue
            (pRoot["proxies"] as? List<*>)?.forEach { pr ->
                val n = (pr as? Map<*, *>)?.get("name")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                if (n != null) names.add(n)
            }
        }
        return names
    }

    /** [include-all-proxies] or YAML [include-all] (mihomo expands both into the inline proxy set). */
    private fun expandsInlineProxyUniverseForFilter(g: Map<*, *>): Boolean {
        return truthyHidden(g["include-all-proxies"]) || truthyHidden(g["include-all"])
    }

    /**
     * Mihomo splits [filter] on `` ` `` and ORs patterns ([outboundgroup.ParseProxyGroup]).
     */
    private fun compileFilterPatterns(filterRaw: String): List<Regex> {
        val parts = filterRaw.split('`').map { it.trim() }.filter { it.isNotEmpty() }
        return parts.mapNotNull { runCatching { Regex(it) }.getOrNull() }
    }

    private fun membersForIncludeAllProxiesPreview(
        root: Map<String, Any?>,
        g: Map<*, *>,
        profileDir: File?,
    ): List<String> {
        if (!expandsInlineProxyUniverseForFilter(g)) return emptyList()
        val universe = collectAllLeafProxyNames(root, profileDir).sorted()
        val filterRaw = g["filter"]?.toString()?.trim().orEmpty()
        if (filterRaw.isEmpty()) return universe
        val patterns = compileFilterPatterns(filterRaw)
        if (patterns.isEmpty()) return emptyList()
        return universe.filter { name -> patterns.any { it.containsMatchIn(name) } }
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
     * - `include-all-proxies` / `include-all`: member names are derived from inline `proxies:` plus
     *   optional [profileDir] proxy-provider files, then [filter] (same rules as mihomo parse).
     * - `include-all-providers` only (no include-all / include-all-proxies): still **empty** members
     *   (provider keys are not dialable proxy names in the UI).
     * - Provider-backed groups (`use` only) return an **empty** member list: keys like sub1 are
     *   **not** leaf proxy names. Listing them made users tap “sub2”, then [patchSelector] failed and
     *   [Selector] fell back to the first real node (often the first subscription).
     */
    fun parseProxyGroupsPreview(text: String, profileDir: File? = null): Map<String, ProxyGroupPreviewRow> {
        val dirKey = runCatching { profileDir?.canonicalPath }.getOrNull()
            ?: profileDir?.absolutePath
        if (text == lastTextRef && dirKey == lastProfileDirKey) return lastResult

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
                hasDynamicMembershipFromYaml(g) -> {
                    val members = membersForIncludeAllProxiesPreview(root, g, profileDir)
                    out[name] = ProxyGroupPreviewRow(type, members)
                }
                else -> Unit
            }
        }
        lastTextRef = text
        lastProfileDirKey = dirKey
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
