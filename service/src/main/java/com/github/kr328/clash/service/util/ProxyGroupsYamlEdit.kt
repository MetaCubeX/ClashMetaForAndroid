package com.github.kr328.clash.service.util

import java.io.File

/**
 * Append proxy-groups entries. Provider keys (sub1, sub2) use the use field, not the proxies field.
 * Merged groups use **select** with `use` so manual picks match real traffic.
 *
 * Rule mode: policy is the group name in rules. Global mode: tunnel uses the GLOBAL adapter, so new
 * merged names are also added to GLOBAL proxies so they appear in the Global selector.
 */
object ProxyGroupsYamlEdit {
    /** Outcome of [pruneDanglingProxyGroupReferences]. */
    data class ProxyGroupRepair(val removedRefs: Int, val emptiedGroups: List<String>)

    /** Proxy names mihomo resolves without a `proxies:`/`proxy-groups:` entry. */
    private val BUILTIN_PROXIES = setOf(
        "DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE", "GLOBAL",
    )

    /**
     * Structurally remove dangling member references from every `proxy-groups` entry in
     * [profileDir]/config.yaml — a member listed in a group's `proxies:` that is neither a real
     * proxy, another group, nor a built-in. Happens when a subscription update drops a node that a
     * composed group still names; mihomo then rejects the WHOLE config (`proxy group[n]: '…' not
     * found`) and the VPN won't load at all.
     *
     * Deliberately structural (no parsing of the engine's error string — that heuristic was
     * fragile and version-dependent) and **fail-closed**: a group emptied by the prune becomes
     * `proxies: [REJECT]`, never `DIRECT`. An emptied group turned into DIRECT would silently send
     * traffic meant for a proxy straight out the clear — a real-IP leak on a privacy VPN. Blocking
     * is safe; leaking is not. `use:` (provider keys) is never touched.
     *
     * @return how many references were dropped and which groups were emptied → REJECT.
     */
    fun pruneDanglingProxyGroupReferences(profileDir: File): ProxyGroupRepair {
        val configFile = File(profileDir, "config.yaml")
        if (!configFile.isFile) return ProxyGroupRepair(0, emptyList())
        val document = MihomoConfigDocument.parse(configFile.readText()) ?: return ProxyGroupRepair(0, emptyList())
        val root = document.root
        @Suppress("UNCHECKED_CAST")
        val groups = root["proxy-groups"] as? MutableList<Any?> ?: return ProxyGroupRepair(0, emptyList())

        val known = HashSet<String>(BUILTIN_PROXIES)
        (root["proxies"] as? List<*>)?.forEach { raw ->
            (raw as? Map<*, *>)?.get("name")?.toString()?.let { known.add(it) }
        }
        // Groups may reference other groups by name, so every group name is itself resolvable.
        for (raw in groups) {
            (raw as? Map<*, *>)?.get("name")?.toString()?.let { known.add(it) }
        }

        var removed = 0
        val emptied = mutableListOf<String>()
        for (raw in groups) {
            val g = raw as? MutableMap<String, Any?> ?: continue
            val proxies = (g["proxies"] as? List<*>)?.mapNotNull { it?.toString() } ?: continue
            val kept = proxies.filter { it in known }
            val dropped = proxies.size - kept.size
            if (dropped == 0) continue
            removed += dropped
            val hasUse = (g["use"] as? List<*>)?.any { it != null } == true
            when {
                kept.isNotEmpty() -> g["proxies"] = kept.toMutableList()
                hasUse -> g.remove("proxies") // provider-backed members survive on their own
                else -> {
                    // Fail-closed: block, do NOT fall back to DIRECT (would leak real IP).
                    g["proxies"] = mutableListOf("REJECT")
                    (g["name"] as? String)?.let { emptied.add(it) }
                }
            }
        }
        if (removed == 0) return ProxyGroupRepair(0, emptyList())
        root["proxy-groups"] = groups
        configFile.writeText(document.renderReplacing("proxy-groups"))
        return ProxyGroupRepair(removed, emptied)
    }
    /**
     * Appends a **select** group with `use: [providerKeys]` (manual pick; delays may stay unset until tested).
     * @return updated config, or **null** if a group named [groupName] already exists.
     */
    fun appendSelectGroupUsingProviders(
        configText: String,
        groupName: String,
        providerKeys: List<String>,
    ): String? {
        if (providerKeys.isEmpty()) return null
        val trimmedName = groupName.trim()
        if (trimmedName.isEmpty()) return null
        val document = MihomoConfigDocument.parse(configText) ?: return null
        val root = document.root
        @Suppress("UNCHECKED_CAST")
        val groups = (root["proxy-groups"] as? MutableList<Any?>) ?: mutableListOf()
        for (raw in groups) {
            val g = raw as? Map<*, *> ?: continue
            if ((g["name"] as? String) == trimmedName) return null
        }
        val newGroup = linkedMapOf<String, Any?>(
            "name" to trimmedName,
            "type" to "select",
            "use" to providerKeys,
        )
        groups.add(newGroup)
        root["proxy-groups"] = groups
        ensureGlobalGroupListsMergedName(root, trimmedName)
        return document.renderReplacing("proxy-groups")
    }

    /**
     * Adds [mergedGroupName] to the **GLOBAL** group’s `proxies` list if present, so Global mode can dial
     * through it (`GLOBAL` → merged group → …).
     */
    private fun ensureGlobalGroupListsMergedName(root: MutableMap<String, Any?>, mergedGroupName: String) {
        @Suppress("UNCHECKED_CAST")
        val groups = root["proxy-groups"] as? MutableList<Any?> ?: return
        for (raw in groups) {
            val g = raw as? MutableMap<String, Any?> ?: continue
            if ((g["name"] as? String) != "GLOBAL") continue
            val proxiesRaw = g["proxies"] as? List<*> ?: return
            val names = proxiesRaw.mapNotNull { it?.toString() }.toMutableList()
            if (mergedGroupName in names) return
            names.add(mergedGroupName)
            g["proxies"] = names
            return
        }
    }

    private fun removeNameFromGlobalGroupProxies(root: MutableMap<String, Any?>, removedGroupName: String) {
        @Suppress("UNCHECKED_CAST")
        val groups = root["proxy-groups"] as? MutableList<Any?> ?: return
        for (raw in groups) {
            val g = raw as? MutableMap<String, Any?> ?: continue
            if ((g["name"] as? String) != "GLOBAL") continue
            val proxiesRaw = g["proxies"] as? List<*> ?: return
            val filtered = proxiesRaw.mapNotNull { it?.toString() }.filter { it != removedGroupName }
            if (filtered.size == proxiesRaw.size) return
            g["proxies"] = filtered.toMutableList()
            return
        }
    }

    /** Removes the proxy-group whose [name] matches [groupName]. Returns **null** if not found. */
    fun removeGroupByName(configText: String, groupName: String): String? {
        val trimmed = groupName.trim()
        if (trimmed.isEmpty()) return null
        val document = MihomoConfigDocument.parse(configText) ?: return null
        val root = document.root
        @Suppress("UNCHECKED_CAST")
        val groups = (root["proxy-groups"] as? MutableList<Any?>) ?: return null
        val idx = groups.indexOfFirst { raw ->
            val g = raw as? Map<*, *> ?: return@indexOfFirst false
            (g["name"] as? String) == trimmed
        }
        if (idx < 0) return null
        groups.removeAt(idx)
        root["proxy-groups"] = groups
        removeNameFromGlobalGroupProxies(root, trimmed)
        return document.renderReplacing("proxy-groups")
    }
}
