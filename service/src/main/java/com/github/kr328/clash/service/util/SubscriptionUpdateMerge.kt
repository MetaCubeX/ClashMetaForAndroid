package com.github.kr328.clash.service.util

/**
 * After a subscription fetch, native code replaces [config.yaml] with the remote file.
 * Local additions under [rule-providers], [rules], [proxy-providers], and extra [proxy-groups]
 * must be merged back so a refresh does not wipe them.
 */
object SubscriptionUpdateMerge {

    data class PreservedOverlay(
        val ruleProviders: Any?,
        val rules: Any?,
        val proxyProviders: Any?,
        val proxyGroups: Any?,
    ) {
        fun isEmpty(): Boolean {
            val rpEmpty =
                ruleProviders == null || (ruleProviders is Map<*, *> && ruleProviders.isEmpty())
            val rEmpty = rules == null || (rules is List<*> && rules.isEmpty())
            val ppEmpty =
                proxyProviders == null || (proxyProviders is Map<*, *> && proxyProviders.isEmpty())
            val pgEmpty =
                proxyGroups == null || (proxyGroups is List<*> && proxyGroups.isEmpty())
            return rpEmpty && rEmpty && ppEmpty && pgEmpty
        }

        companion object {
            val EMPTY = PreservedOverlay(null, null, null, null)
        }
    }

    fun extractPreserved(configYaml: String): PreservedOverlay {
        val root = YamlFormatting.parseRootMap(configYaml) ?: return PreservedOverlay.EMPTY
        val rp = root["rule-providers"]
        val rules = root["rules"]
        val pp = root["proxy-providers"]
        val pg = root["proxy-groups"]
        if (rp == null && rules == null && pp == null && pg == null) return PreservedOverlay.EMPTY
        return PreservedOverlay(rp, rules, pp, pg)
    }

    fun mergeAfterFetch(fetchedYaml: String, preserved: PreservedOverlay): String {
        if (preserved.isEmpty()) return fetchedYaml
        val root = YamlFormatting.parseRootMap(fetchedYaml) ?: return fetchedYaml
        if (preserved.ruleProviders != null) {
            root["rule-providers"] = mergeRuleProviderMaps(root["rule-providers"], preserved.ruleProviders)
        }
        if (preserved.rules != null) {
            root["rules"] = mergeRulesLists(preserved.rules, root["rules"])
        }
        if (preserved.proxyProviders != null) {
            root["proxy-providers"] =
                mergeProxyProviderMaps(root["proxy-providers"], preserved.proxyProviders)
        }
        if (preserved.proxyGroups != null) {
            root["proxy-groups"] = mergeProxyGroupsLists(root, preserved)
        }
        return YamlFormatting.blockYaml().dump(root)
    }

    /** Start from fetched map, overlay preserved entries (local wins on same key). */
    private fun mergeRuleProviderMaps(fetched: Any?, preserved: Any?): Any? {
        if (preserved == null) return fetched
        val merged = LinkedHashMap<String, Any?>()
        (fetched as? Map<*, *>)?.forEach { (k, v) -> merged[k.toString()] = v }
        (preserved as? Map<*, *>)?.forEach { (k, v) -> merged[k.toString()] = v }
        return merged
    }

    private fun mergeProxyProviderMaps(fetched: Any?, preserved: Any?): Any? {
        if (preserved == null) return fetched
        val merged = LinkedHashMap<String, Any?>()
        (fetched as? Map<*, *>)?.forEach { (k, v) -> merged[k.toString()] = v }
        (preserved as? Map<*, *>)?.forEach { (k, v) -> merged[k.toString()] = v }
        return merged
    }

    /**
     * Start from the fetched `proxy-groups` list; append preserved local/custom groups only when
     * every referenced proxy, provider, or child group still resolves after the subscription refresh.
     * This prevents deleted subscription groups from being resurrected with stale members, e.g.
     * `proxy group[n]: ...: 'old country group' not found`.
     *
     * **GLOBAL:** the remote profile almost always defines its own `GLOBAL` entry. We must merge
     * `proxies` with the preserved copy: locally added merged groups (e.g. `MainGroup` appended by
     * [com.github.kr328.clash.service.util.ProxyGroupsYamlEdit]) only exist in the old `GLOBAL`
     * list; without this union, Global mode cannot select them and traffic never uses that group.
     */
    private fun mergeProxyGroupsLists(
        root: MutableMap<String, Any?>,
        preserved: PreservedOverlay,
    ): Any? {
        val fetched = root["proxy-groups"]
        val out = mutableListOf<Any?>()
        when (fetched) {
            is List<*> -> out.addAll(fetched)
            null -> Unit
            else -> out.add(fetched)
        }
        val preservedList: List<*> = when (preserved.proxyGroups) {
            is List<*> -> preserved.proxyGroups
            else -> emptyList<Any?>()
        }
        val knownNames = collectKnownProxyNames(root, preserved.proxyProviders)
        val pendingGroups = preservedList
            .mapNotNull { it as? Map<*, *> }
            .filter { (it["name"]?.toString()).isNullOrEmpty().not() }
            .filterNot { it["name"]?.toString() in knownNames }
            .toMutableList()

        var changed = true
        while (changed) {
            changed = false
            val iter = pendingGroups.iterator()
            while (iter.hasNext()) {
                val group = iter.next()
                val name = group["name"]?.toString() ?: continue
                if (isProxyGroupResolvable(group, knownNames)) {
                    out.add(group)
                    knownNames.add(name)
                    iter.remove()
                    changed = true
                }
            }
        }

        val preservedGlobalProxies = preservedList
            .mapNotNull { it as? Map<*, *> }
            .firstOrNull { it["name"]?.toString() == "GLOBAL" }
            ?.get("proxies") as? List<*>
        if (preservedGlobalProxies != null) {
            mergeExtraProxiesIntoGlobalGroup(out, preservedGlobalProxies, knownNames)
        }
        return out
    }

    private fun collectKnownProxyNames(
        root: Map<String, Any?>,
        preservedProxyProviders: Any?,
    ): MutableSet<String> {
        val names = linkedSetOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE", "GLOBAL")
        (root["proxies"] as? List<*>)?.mapNotNullTo(names) {
            (it as? Map<*, *>)?.get("name")?.toString()
        }
        (root["proxy-groups"] as? List<*>)?.mapNotNullTo(names) {
            (it as? Map<*, *>)?.get("name")?.toString()
        }
        (root["proxy-providers"] as? Map<*, *>)?.keys?.mapNotNullTo(names) { it?.toString() }
        (preservedProxyProviders as? Map<*, *>)?.keys?.mapNotNullTo(names) { it?.toString() }
        return names
    }

    private fun isProxyGroupResolvable(group: Map<*, *>, knownNames: Set<String>): Boolean {
        val proxies = (group["proxies"] as? List<*>).orEmpty().mapNotNull { it?.toString() }
        val providers = (group["use"] as? List<*>).orEmpty().mapNotNull { it?.toString() }
        if (proxies.isEmpty() && providers.isEmpty()) return true
        return proxies.all { it in knownNames } && providers.all { it in knownNames }
    }

    /**
     * Appends proxy names from the pre-fetch GLOBAL group that are missing from the current GLOBAL
     * entry (subscription order preserved; extras keep preserved order after the last fetched name).
     */
    private fun mergeExtraProxiesIntoGlobalGroup(
        out: MutableList<Any?>,
        preservedProxies: List<*>,
        knownNames: Set<String>,
    ) {
        val globalIdx = out.indexOfFirst { (it as? Map<*, *>)?.get("name")?.toString() == "GLOBAL" }
        if (globalIdx < 0) return
        val global = out[globalIdx] as? Map<*, *> ?: return
        val mutable = LinkedHashMap<String, Any?>()
        global.forEach { (k, v) -> mutable[k.toString()] = v }
        val current = (mutable["proxies"] as? List<*>)?.mapNotNull { it?.toString() }?.toMutableList()
            ?: return
        val existing = current.toSet()
        for (p in preservedProxies) {
            val name = p?.toString() ?: continue
            if (name !in existing && name in knownNames) {
                current.add(name)
            }
        }
        mutable["proxies"] = current
        out[globalIdx] = mutable
    }

    /**
     * Prepends rules that existed locally but are missing from the fetched list (string match),
     * then appends the full fetched rules list to preserve subscription order for the rest.
     */
    private fun mergeRulesLists(preservedRules: Any?, fetchedRules: Any?): List<Any?> {
        val newList: List<Any?> = when (fetchedRules) {
            is List<*> -> fetchedRules
            null -> emptyList()
            else -> listOf(fetchedRules)
        }
        val oldList: List<Any?> = when (preservedRules) {
            is List<*> -> preservedRules
            null -> emptyList()
            else -> listOf(preservedRules)
        }
        val newTrimmed = newList.map { it?.toString()?.trim() }.toSet()
        val prefix = oldList.filter {
            val t = it?.toString()?.trim()
            !t.isNullOrEmpty() && t !in newTrimmed
        }
        return prefix + newList
    }
}
