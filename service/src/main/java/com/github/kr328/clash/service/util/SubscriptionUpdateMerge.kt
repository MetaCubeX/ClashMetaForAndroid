package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import java.io.File

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
        /**
         * User-authored entries from the top-level `listeners:` block.
         * Carried across subscription refreshes so an advanced user who
         * configured e.g. an authenticated local SOCKS doesn't lose it on
         * every fetch. YamlHardener still runs afterwards, so the
         * loopback-only invariants stay enforced even if the preserved
         * entries originally bound to a remote interface.
         */
        val listeners: Any? = null,
        /**
         * User-owned `dns:` / `hosts:` blocks, captured ONLY for profiles the
         * DNS & Hosts editor manages (the per-profile master toggle is ON). On
         * refresh these REPLACE the fetched values (the user owns name
         * resolution for this profile) — unlike the additive merge used for
         * rules/providers.
         */
        val dns: Any? = null,
        val hosts: Any? = null,
        /**
         * User-managed `tunnels:` list, captured ONLY when the Tunnels editor
         * manages this profile (master toggle ON). REPLACES fetched tunnels on
         * refresh, like dns/hosts.
         */
        val tunnels: Any? = null,
    ) {
        fun isEmpty(): Boolean {
            val rpEmpty =
                ruleProviders == null || (ruleProviders is Map<*, *> && ruleProviders.isEmpty())
            val rEmpty = rules == null || (rules is List<*> && rules.isEmpty())
            val ppEmpty =
                proxyProviders == null || (proxyProviders is Map<*, *> && proxyProviders.isEmpty())
            val pgEmpty =
                proxyGroups == null || (proxyGroups is List<*> && proxyGroups.isEmpty())
            val lEmpty = listeners == null || (listeners is List<*> && listeners.isEmpty())
            val dnsEmpty = dns == null || (dns is Map<*, *> && dns.isEmpty())
            val hostsEmpty = hosts == null || (hosts is Map<*, *> && hosts.isEmpty())
            val tunnelsEmpty = tunnels == null || (tunnels is List<*> && tunnels.isEmpty())
            return rpEmpty && rEmpty && ppEmpty && pgEmpty && lEmpty && dnsEmpty && hostsEmpty && tunnelsEmpty
        }

        companion object {
            val EMPTY = PreservedOverlay(null, null, null, null, null)
        }
    }

    /**
     * Snapshot-based extraction. The caller is responsible for obtaining the
     * snapshot from the engine (typically `Clash.parseProfileSnapshot(profileDir)`)
     * before the fetch overwrites config.yaml. Engine-parsed values are
     * converted to plain Map/List/scalar trees so the existing mergeAfterFetch
     * pipeline (which dumps back via SnakeYAML) keeps working unchanged.
     */
    /**
     * @param includeDnsHosts capture `dns:`/`hosts:` too — pass `true` only for
     * profiles whose DNS & Hosts editor master toggle is ON (`dnsHostsManaged`).
     * Untouched profiles must keep whatever the subscription ships.
     */
    fun extractPreserved(
        snapshot: ProfileSnapshot,
        includeDnsHosts: Boolean = false,
        includeTunnels: Boolean = false,
    ): PreservedOverlay {
        val rp = snapshot.ruleProviders.takeIf { it.isNotEmpty() }
            ?.let { JsonElementToYaml.convertObjectMap(it) }
        val rules = snapshot.rules.takeIf { it.isNotEmpty() }
            ?.let { ArrayList<Any?>(it) }
        val pp = snapshot.proxyProviders.takeIf { it.isNotEmpty() }
            ?.let { JsonElementToYaml.convertObjectMap(it) }
        val pg = snapshot.proxyGroups.takeIf { it.isNotEmpty() }
            ?.let { JsonElementToYaml.convertObjectList(it) }
        val listeners = snapshot.listeners.takeIf { it.isNotEmpty() }
            ?.let { JsonElementToYaml.convertObjectList(it) }
        val dns = if (includeDnsHosts) snapshot.dns?.let { JsonElementToYaml.convertObject(it) } else null
        val hosts = if (includeDnsHosts) snapshot.hosts?.let { JsonElementToYaml.convertObject(it) } else null
        val tunnels = if (includeTunnels) snapshot.tunnels?.let { JsonElementToYaml.convertArray(it) } else null
        if (rp == null && rules == null && pp == null && pg == null && listeners == null &&
            dns == null && hosts == null && tunnels == null
        ) {
            return PreservedOverlay.EMPTY
        }
        return PreservedOverlay(rp, rules, pp, pg, listeners, dns, hosts, tunnels)
    }

    /**
     * @param profileDir directory with [config.yaml] and provider paths after fetch; used to collect
     * proxy `name` entries from provider YAML on disk (not only inline `proxies:`).
     */
    fun mergeAfterFetch(fetchedYaml: String, preserved: PreservedOverlay, profileDir: File? = null): String {
        if (preserved.isEmpty()) return fetchedYaml
        val document = MihomoConfigDocument.parse(fetchedYaml) ?: return fetchedYaml
        val root = document.root
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
            root["proxy-groups"] = mergeProxyGroupsLists(root, preserved, profileDir)
        }
        if (preserved.listeners != null) {
            root["listeners"] = mergeListenersLists(root["listeners"], preserved.listeners)
        }
        // dns/hosts are user-owned for managed profiles: REPLACE the fetched
        // values outright (not an additive merge). A removed user block leaves
        // the subscription's own value in place (preserved.dns is null then).
        if (preserved.dns != null) {
            root["dns"] = preserved.dns
        }
        if (preserved.hosts != null) {
            root["hosts"] = preserved.hosts
        }
        // tunnels are user-owned for managed profiles: REPLACE fetched outright.
        if (preserved.tunnels != null) {
            root["tunnels"] = preserved.tunnels
        }
        // Garbage-collect providers nothing references. After overlaying the
        // pre-fetch state on top of the fresh subscription, a rule-provider
        // may exist that no `RULE-SET,name,...` rule mentions, or a
        // proxy-provider that no proxy-group uses. Mihomo would still refetch
        // them every `interval:` and the UI would list them — both noise.
        // Always safe to drop because nothing in the active config consumes
        // them; if the user wants the provider back, the rule referencing it
        // must be re-added too.
        gcUnusedRuleProviders(root)
        gcUnusedProxyProviders(root)
        return document.renderReplacing(
            "rule-providers",
            "rules",
            "proxy-providers",
            "proxy-groups",
            "listeners",
            "dns",
            "hosts",
            "tunnels",
        )
    }

    /**
     * Union fetched + preserved listener entries on `name` (mihomo's
     * identity key for listeners). Preserved entries override fetched
     * ones — local edits win — but unknown listeners shipped with the
     * subscription are still kept. YamlHardener.hardenProfile runs after
     * the merge so any non-loopback bind ultimately gets sanitised.
     */
    private fun mergeListenersLists(fetched: Any?, preserved: Any?): Any? {
        val fetchedList = fetched as? List<*> ?: emptyList<Any?>()
        val preservedList = preserved as? List<*> ?: emptyList<Any?>()
        if (preservedList.isEmpty()) return fetched
        val byName = LinkedHashMap<String, Any?>()
        val anonymous = mutableListOf<Any?>()
        fun add(entry: Any?) {
            val name = (entry as? Map<*, *>)?.get("name")?.toString()
            if (name.isNullOrBlank()) anonymous.add(entry) else byName[name] = entry
        }
        fetchedList.forEach(::add)
        preservedList.forEach(::add)
        return byName.values.toMutableList<Any?>().apply { addAll(anonymous) }
    }

    /**
     * Drops entries from `rule-providers` whose name does not appear as the
     * second token of any `RULE-SET,<name>,...` rule. Mutates root in place.
     */
    private fun gcUnusedRuleProviders(root: MutableMap<String, Any?>) {
        val providers = root["rule-providers"] as? Map<*, *> ?: return
        if (providers.isEmpty()) return
        val referenced = collectRuleSetTargets(root["rules"])
        val kept = LinkedHashMap<String, Any?>()
        for ((k, v) in providers) {
            val name = k?.toString() ?: continue
            if (name in referenced) kept[name] = v
        }
        if (kept.size != providers.size) {
            root["rule-providers"] = kept
        }
    }

    /**
     * Drops entries from `proxy-providers` no proxy-group references via
     * `use:` and no group declares `include-all-providers: true` /
     * `include-all: true` (those expand the universe to every provider).
     */
    private fun gcUnusedProxyProviders(root: MutableMap<String, Any?>) {
        val providers = root["proxy-providers"] as? Map<*, *> ?: return
        if (providers.isEmpty()) return
        val (referenced, includeAll) = collectProxyProviderUsage(root["proxy-groups"])
        if (includeAll) return // every provider is implicitly used
        val kept = LinkedHashMap<String, Any?>()
        for ((k, v) in providers) {
            val name = k?.toString() ?: continue
            if (name in referenced) kept[name] = v
        }
        if (kept.size != providers.size) {
            root["proxy-providers"] = kept
        }
    }

    /** `RULE-SET,<name>` anywhere in a rule line — incl. nested in logical rules. */
    private val ruleSetRef = Regex("RULE-SET\\s*,\\s*([^,()\\s]+)", RegexOption.IGNORE_CASE)

    private fun collectRuleSetTargets(rulesNode: Any?): Set<String> {
        val list = rulesNode as? List<*> ?: return emptySet()
        val out = HashSet<String>()
        for (raw in list) {
            val line = raw?.toString() ?: continue
            // Match EVERY RULE-SET reference, not just lines that start with it:
            // logical rules like `OR,((RULE-SET,a),(RULE-SET,b)),PROXY` embed
            // RULE-SET inside parentheses, and missing them here would GC the
            // referenced rule-providers and break the next subscription update.
            for (m in ruleSetRef.findAll(line)) {
                m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
            }
        }
        return out
    }

    private fun collectProxyProviderUsage(groupsNode: Any?): Pair<Set<String>, Boolean> {
        val groups = groupsNode as? List<*> ?: return emptySet<String>() to false
        val out = HashSet<String>()
        var includeAll = false
        for (raw in groups) {
            val g = raw as? Map<*, *> ?: continue
            if (yamlTruthy(g["include-all-providers"]) || yamlTruthy(g["include-all"])) {
                includeAll = true
            }
            (g["use"] as? List<*>)?.forEach { p ->
                p?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            }
        }
        return out to includeAll
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
        profileDir: File?,
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
        val knownNames = collectKnownProxyNames(root, preserved.proxyProviders, profileDir)
        val pendingGroups = preservedList
            .mapNotNull { it as? Map<*, *> }
            .filter { (it["name"]?.toString()).isNullOrEmpty().not() }
            .filterNot { it["name"]?.toString() in knownNames }
            .filter { preservedProxyGroupMayResurrect(it) }
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
        sanitizeGlobalProxyList(out, knownNames)
        return out
    }

    /**
     * Subscription refresh must not bring back removed [proxy-groups] names. Only
     * provider-backed groups ([use]) or Mihomo dynamic groups ([include-all-*]) are merged from the
     * pre-fetch file; plain `select`/`url-test` blocks belong to the remote profile.
     */
    private fun preservedProxyGroupMayResurrect(group: Map<*, *>): Boolean {
        if (yamlTruthy(group["include-all-proxies"]) ||
            yamlTruthy(group["include-all"]) ||
            yamlTruthy(group["include-all-providers"])
        ) {
            return true
        }
        val use = (group["use"] as? List<*>).orEmpty()
        return use.isNotEmpty()
    }

    /** Drops stale policy targets (e.g. removed `Automatic`) left inside [GLOBAL]. */
    private fun sanitizeGlobalProxyList(out: MutableList<Any?>, validNames: Set<String>) {
        val idx = out.indexOfFirst { (it as? Map<*, *>)?.get("name")?.toString() == "GLOBAL" }
        if (idx < 0) return
        val global = out[idx] as? Map<*, *> ?: return
        val mutable = LinkedHashMap<String, Any?>()
        global.forEach { (k, v) -> mutable[k.toString()] = v }
        val current = (mutable["proxies"] as? List<*>)?.mapNotNull { it?.toString() } ?: return
        val filtered = current.filter { it in validNames }
        if (filtered.size == current.size) return
        mutable["proxies"] = filtered
        out[idx] = mutable
    }

    private fun collectKnownProxyNames(
        root: Map<String, Any?>,
        preservedProxyProviders: Any?,
        profileDir: File?,
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
        val dir = profileDir ?: return names
        val pp = root["proxy-providers"] as? Map<*, *> ?: return names
        for ((_, v) in pp) {
            val prov = v as? Map<*, *> ?: continue
            val pathStr = prov["path"] as? String ?: continue
            val f = ProxyDialerYamlEdit.resolveProviderPath(dir, pathStr)
            if (!f.isFile) continue
            val pRoot = runCatching { YamlFormatting.parseRootMap(f.readText()) }.getOrNull() ?: continue
            (pRoot["proxies"] as? List<*>)?.mapNotNullTo(names) {
                (it as? Map<*, *>)?.get("name")?.toString()
            }
        }
        return names
    }

    private fun isProxyGroupResolvable(group: Map<*, *>, knownNames: Set<String>): Boolean {
        val proxies = (group["proxies"] as? List<*>).orEmpty().mapNotNull { it?.toString() }
        val providers = (group["use"] as? List<*>).orEmpty().mapNotNull { it?.toString() }
        if (proxies.isEmpty() && providers.isEmpty()) {
            return yamlTruthy(group["include-all-proxies"]) ||
                yamlTruthy(group["include-all"]) ||
                yamlTruthy(group["include-all-providers"])
        }
        return proxies.all { it in knownNames } && providers.all { it in knownNames }
    }

    private fun yamlTruthy(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> {
                // After the YAML-dialect alignment, bareword yes/on/y arrive as
                // strings (not Boolean), so all 1.1 truthy spellings must be here.
                val t = value.trim()
                t.equals("true", ignoreCase = true) ||
                    t == "1" ||
                    t.equals("yes", ignoreCase = true) ||
                    t.equals("on", ignoreCase = true) ||
                    t.equals("y", ignoreCase = true)
            }
            else -> false
        }
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
     * Prepends rules that existed locally but are missing from the fetched list, then appends
     * the full fetched rules list to preserve subscription order for the rest.
     *
     * Two refinements over a naive string diff:
     * 1. Comparison normalizes surrounding quotes and per-token whitespace so a rule that
     *    survived a YAML re-dump under different quoting doesn't get treated as missing.
     * 2. Subscription-owned rule types (RULE-SET / GEOSITE / GEOIP / MATCH / SUB-RULE /
     *    AND / OR / NOT) are never carried over. They are tied to providers/policies the
     *    new subscription controls — if the user updates the subscription and removes a
     *    RULE-SET reference, we must not resurrect it. Manual host/IP entries
     *    (DOMAIN / DOMAIN-SUFFIX / IP-CIDR / ...) are still preserved.
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
        val newNormalized = newList
            .map { normalizeRuleLine(it) }
            .filter { it.isNotEmpty() }
            .toSet()
        val prefix = oldList.filter {
            val normalized = normalizeRuleLine(it)
            if (normalized.isEmpty()) return@filter false
            if (isSubscriptionOwnedRuleType(normalized)) return@filter false
            normalized !in newNormalized
        }
        return prefix + newList
    }

    private fun normalizeRuleLine(value: Any?): String {
        val raw = value?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val unquoted = when {
            raw.length >= 2 && raw.first() == '\'' && raw.last() == '\'' -> raw.substring(1, raw.length - 1)
            raw.length >= 2 && raw.first() == '"' && raw.last() == '"' -> raw.substring(1, raw.length - 1)
            else -> raw
        }
        return unquoted.split(',').joinToString(",") { it.trim() }
    }

    private fun isSubscriptionOwnedRuleType(normalized: String): Boolean {
        val head = normalized.substringBefore(',').trim().uppercase()
        return head in SUBSCRIPTION_OWNED_RULE_TYPES
    }

    private val SUBSCRIPTION_OWNED_RULE_TYPES = setOf(
        "RULE-SET",
        "GEOSITE",
        "GEOIP",
        "MATCH",
        "SUB-RULE",
        "AND",
        "OR",
        "NOT",
    )
}
