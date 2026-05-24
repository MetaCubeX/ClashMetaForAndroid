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
    /**
     * Removes one stale [staleName] from every `proxy-groups` entry’s `proxies` and `use` lists in
     * [profileDir]/config.yaml. Used when Mihomo fails load with `proxy group[n]: …: '…' not found`
     * after subscription nodes were dropped but merged groups still reference old names.
     *
     * @return true if config.yaml was modified
     */
    fun removeStaleNameFromAllProxyGroups(profileDir: File, staleName: String): Boolean {
        val trimmed = staleName.trim()
        if (trimmed.isEmpty()) return false
        val configFile = File(profileDir, "config.yaml")
        if (!configFile.isFile) return false
        val configText = configFile.readText()
        val document = MihomoConfigDocument.parse(configText) ?: return false
        val root = document.root
        @Suppress("UNCHECKED_CAST")
        val groups = root["proxy-groups"] as? MutableList<Any?> ?: return false
        var changed = false
        for (raw in groups) {
            val g = raw as? MutableMap<String, Any?> ?: continue
            for (key in listOf("proxies", "use")) {
                val listRaw = g[key] as? List<*> ?: continue
                val names = listRaw.mapNotNull { it?.toString() }
                val filtered = names.filter { it != trimmed }
                if (filtered.size == names.size) continue
                changed = true
                if (filtered.isEmpty()) {
                    g.remove(key)
                } else {
                    g[key] = filtered.toMutableList()
                }
            }
            val proxiesLeft = (g["proxies"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
            val useLeft = (g["use"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
            if (proxiesLeft.isEmpty() && useLeft.isEmpty()) {
                g["proxies"] = mutableListOf("DIRECT")
                changed = true
            }
        }
        if (!changed) return false
        root["proxy-groups"] = groups
        configFile.writeText(document.renderReplacing("proxy-groups"))
        return true
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
