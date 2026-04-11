package com.github.kr328.clash.service.util

import org.yaml.snakeyaml.Yaml

/**
 * Append proxy-groups entries. Provider keys (sub1, sub2) use the use field, not the proxies field.
 * Merged groups use **select** with `use` so manual picks match real traffic.
 *
 * Rule mode: policy is the group name in rules. Global mode: tunnel uses the GLOBAL adapter, so new
 * merged names are also added to GLOBAL proxies so they appear in the Global selector.
 */
object ProxyGroupsYamlEdit {
    private val parseYaml = Yaml()
    private val dumpYaml = YamlFormatting.blockYaml()

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
        val root = parseYaml.load<MutableMap<String, Any?>>(configText) ?: return null
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
        return dumpYaml.dump(root)
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
        val root = parseYaml.load<MutableMap<String, Any?>>(configText) ?: return null
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
        return dumpYaml.dump(root)
    }
}
