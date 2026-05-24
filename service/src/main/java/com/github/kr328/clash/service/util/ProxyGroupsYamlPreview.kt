package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.service.model.ProxyGroupPreviewRow
import java.io.File

/** Reads [proxy-groups] from a Clash config.yaml without loading the engine. */
object ProxyGroupsYamlPreview {
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
     * Inline `proxies:` names plus, when [profileDir] points at the profile root,
     * leaf names from each proxy-provider file on disk. Matches what mihomo would
     * resolve for `include-all-providers` / `include-all-proxies` / `include-all`.
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
            val pathStr = prov["path"] as? String
            val providerUrl = prov["url"] as? String
            val providerFile = if (!pathStr.isNullOrBlank()) {
                ProxyDialerYamlEdit.resolveProviderPath(dir, pathStr)
            } else {
                resolveDefaultProviderFile(dir, providerUrl) ?: continue
            }
            if (!providerFile.isFile) continue
            val pRoot = runCatching { YamlFormatting.parseRootMap(providerFile.readText()) }
                .getOrNull() ?: continue
            (pRoot["proxies"] as? List<*>)?.forEach { pr ->
                val n = (pr as? Map<*, *>)?.get("name")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                if (n != null) names.add(n)
            }
        }
        return names
    }

    /**
     * When a proxy-provider entry has no `path:` field, ClashFest's native config
     * pipeline rewrites it to `<profileDir>/providers/proxies/<md5(URL)>` before
     * mihomo loads the config (see core/src/main/golang/native/config/process.go).
     * Reproduce that path here so the offline parse finds the same file.
     */
    private fun resolveDefaultProviderFile(profileDir: File, providerUrl: String?): File? {
        if (providerUrl.isNullOrBlank()) return null
        return File(profileDir, "providers/proxies/${md5Hex(providerUrl)}")
    }

    private fun md5Hex(input: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append(HEX_CHARS[v ushr 4])
                append(HEX_CHARS[v and 0x0F])
            }
        }
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    /**
     * `include-all-proxies` / `include-all` / `include-all-providers` all ask mihomo
     * to expand the group to the full leaf-node universe at runtime. We treat all
     * three the same way at preview time — distinguishing them isn't useful for the
     * UI list, the actual routing semantics stay with the engine.
     */
    private fun expandsInlineProxyUniverseForFilter(g: Map<*, *>): Boolean {
        return truthyHidden(g["include-all-proxies"]) ||
            truthyHidden(g["include-all"]) ||
            truthyHidden(g["include-all-providers"])
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
     * - Combines declared `proxies:` with the leaf-node universe when a group
     *   enables `include-all-proxies` / `include-all-providers` / `include-all`,
     *   so the picker matches what mihomo actually routes through.
     * - Provider-backed groups (`use:` only, no include-all-*) keep an empty
     *   member list — provider keys like `sub1` are not dialable proxy names;
     *   live engine data fills these in on the engine path.
     */
    fun parseProxyGroupsPreview(text: String, profileDir: File? = null): Map<String, ProxyGroupPreviewRow> {
        val document = MihomoConfigDocument.parse(text) ?: return emptyMap()
        val root = document.root
        val groups = document.proxyGroups ?: return emptyMap()
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
            // mihomo treats declared `proxies:` and include-all-* as additive, so a
            // group with both should expose every leaf node alongside the static
            // members. Union via LinkedHashSet to keep declared order first.
            val dynamicMembers = if (hasDynamicMembershipFromYaml(g)) {
                membersForIncludeAllProxiesPreview(root, g, profileDir)
            } else {
                emptyList()
            }
            val combined = LinkedHashSet<String>().apply {
                addAll(fromProxies)
                addAll(dynamicMembers)
            }.toList()
            when {
                combined.isNotEmpty() -> out[name] = ProxyGroupPreviewRow(type, combined)
                useList.isNotEmpty() -> out[name] = ProxyGroupPreviewRow(type, emptyList())
                else -> Unit
            }
        }
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
        val groups = MihomoConfigDocument.parse(text)?.proxyGroups ?: return emptyList()
        return groups.mapNotNull { raw ->
            (raw as? Map<*, *>)?.get("name")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    /** Names of [proxy-groups] that reference proxy-providers via [use] (merged-style). */
    fun listGroupNamesWithUse(text: String): List<String> {
        val groups = MihomoConfigDocument.parse(text)?.proxyGroups ?: return emptyList()
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
