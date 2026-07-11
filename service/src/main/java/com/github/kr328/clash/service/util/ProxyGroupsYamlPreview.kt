package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.service.model.ProxyGroupPreviewRow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Reads [proxy-groups] from a mihomo-parsed snapshot. The snapshot already
 * went through the engine's YAML parser, so this layer only does shape
 * projection — no string splitting, no quoting tricks.
 *
 * Provider YAML files on disk are still read through SnakeYAML because
 * they're standalone provider documents (just a `proxies:` list), not
 * full mihomo configs, so the engine snapshot does not load them.
 */
object ProxyGroupsYamlPreview {
    private const val MAX_FILTER_PATTERN_CHARS = 256

    private fun truthyHidden(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> {
            // Dialect alignment: bareword yes/on/y now arrive as strings.
            val t = value.trim()
            t.equals("true", ignoreCase = true) ||
                t == "1" ||
                t.equals("yes", ignoreCase = true) ||
                t.equals("on", ignoreCase = true) ||
                t.equals("y", ignoreCase = true)
        }
        else -> false
    }

    private fun JsonObject.booleanFlag(key: String): Boolean {
        val prim = (this[key] as? JsonPrimitive) ?: return false
        if (prim.content == "1") return true
        return prim.content.equals("true", ignoreCase = true) ||
            prim.content.equals("yes", ignoreCase = true) ||
            prim.content.equals("on", ignoreCase = true) ||
            prim.content.equals("y", ignoreCase = true)
    }

    private fun JsonObject.stringField(key: String): String? =
        runCatching { (this[key] as? JsonPrimitive)?.content }.getOrNull()

    private fun JsonObject.stringList(key: String): List<String> {
        val arr = this[key] as? JsonArray ?: return emptyList()
        return arr.asSequence()
            .mapNotNull { runCatching { it.jsonPrimitive.content.trim() }.getOrNull() }
            .filter { it.isNotEmpty() && it.length <= PreviewResourceLimits.MAX_NAME_CHARS }
            .take(PreviewResourceLimits.MAX_MEMBERS_PER_GROUP)
            .toList()
    }

    /** Mihomo expands membership at runtime (`include-all-proxies`, etc.). */
    private fun hasDynamicMembership(g: JsonObject): Boolean {
        return g.booleanFlag("include-all-proxies") ||
            g.booleanFlag("include-all") ||
            g.booleanFlag("include-all-providers")
    }

    /**
     * Inline `proxies:` names plus, when [profileDir] points at the profile root,
     * leaf names from each proxy-provider file on disk. Matches what mihomo would
     * resolve for `include-all-providers` / `include-all-proxies` / `include-all`.
     */
    private fun collectAllLeafProxyNames(
        snapshot: ProfileSnapshot,
        profileDir: File?,
    ): LinkedHashSet<String> {
        val names = LinkedHashSet<String>()
        for (entry in snapshot.proxies.take(PreviewResourceLimits.MAX_PROXY_ENTRIES_SCANNED)) {
            if (names.size >= PreviewResourceLimits.MAX_TOTAL_MEMBERS) break
            entry.stringField("name")?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= PreviewResourceLimits.MAX_NAME_CHARS }
                ?.let { names.add(it) }
        }
        val dir = profileDir?.takeIf { it.isDirectory } ?: return names
        val readBudget = ProviderFileReadBudget()
        var remainingEntries = PreviewResourceLimits.MAX_PROXY_ENTRIES_SCANNED
        for ((_, prov) in snapshot.proxyProviders.entries.take(PreviewResourceLimits.MAX_PROVIDER_FILES)) {
            if (names.size >= PreviewResourceLimits.MAX_TOTAL_MEMBERS || remainingEntries <= 0) break
            val pathStr = prov.stringField("path")
            val providerUrl = prov.stringField("url")
            val providerFile = if (!pathStr.isNullOrBlank()) {
                ProxyDialerYamlEdit.resolveProviderPath(dir, pathStr)
            } else {
                resolveDefaultProviderFile(dir, providerUrl) ?: continue
            } ?: continue
            if (!providerFile.isFile) continue
            val providerText = readBudget.readUtf8(providerFile) ?: continue
            val pRoot = runCatching { YamlFormatting.parseRootMap(providerText) }
                .getOrNull() ?: continue
            for (pr in (pRoot["proxies"] as? List<*>).orEmpty().take(remainingEntries)) {
                remainingEntries--
                if (names.size >= PreviewResourceLimits.MAX_TOTAL_MEMBERS) break
                val n = (pr as? Map<*, *>)?.get("name")?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() && it.length <= PreviewResourceLimits.MAX_NAME_CHARS }
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
     * Mihomo splits [filter] on `` ` `` and ORs patterns ([outboundgroup.ParseProxyGroup]).
     */
    /** null means the filter cannot be evaluated safely in the offline JVM preview. */
    private fun compileFilterPatterns(filterRaw: String): List<Regex>? {
        val parts = filterRaw.split('`').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val compiled = ArrayList<Regex>(parts.size)
        for (pattern in parts) {
            if (!isSafeFilterPattern(pattern)) return null
            compiled += runCatching { Regex(pattern) }.getOrNull() ?: return null
        }
        return compiled
    }

    /**
     * JVM Regex is a backtracking engine. Keep the accepted provider-filter
     * dialect deliberately small: bounded patterns, no backreferences,
     * lookarounds or repetition operators. Literal alternation, anchors and
     * character classes remain supported, and matching time is bounded by
     * pattern length times the already-capped proxy-name length.
     */
    internal fun isSafeFilterPattern(pattern: String): Boolean {
        if (pattern.isEmpty() || pattern.length > MAX_FILTER_PATTERN_CHARS) return false
        var escaped = false
        var inClass = false
        var groupDepth = 0
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (escaped) {
                if (c in '1'..'9') return false
                escaped = false
                i++
                continue
            }
            if (c == '\\') {
                escaped = true
                i++
                continue
            }
            if (inClass) {
                if (c == ']') inClass = false
                i++
                continue
            }
            when (c) {
                '[' -> inClass = true
                '*', '+', '?', '{' -> return false
                '(' -> {
                    if (i + 1 < pattern.length && pattern[i + 1] == '?') {
                        if (!pattern.startsWith("(?:", i)) return false
                    }
                    groupDepth++
                }
                ')' -> {
                    if (groupDepth == 0) return false
                    groupDepth--
                }
            }
            i++
        }
        return !escaped && !inClass && groupDepth == 0
    }

    private fun membersForIncludeAllProxiesPreview(
        snapshot: ProfileSnapshot,
        g: JsonObject,
        profileDir: File?,
    ): List<String>? {
        if (!hasDynamicMembership(g)) return emptyList()
        val universe = collectAllLeafProxyNames(snapshot, profileDir).sorted()
        val filterRaw = g.stringField("filter")?.trim().orEmpty()
        val included = if (filterRaw.isEmpty()) {
            universe
        } else {
            val patterns = compileFilterPatterns(filterRaw) ?: return null
            universe.filter { name -> patterns.any { it.containsMatchIn(name) } }
        }
        // Mirror the engine ([outboundgroup.GroupBase.getProxies]): `exclude-filter` drops any member
        // whose name matches any pattern, applied AFTER the include `filter`. Without this the offline
        // preview kept excluded nodes (e.g. a `🇸🇪|SE|Sweden` exclude still listed Sweden) while the
        // running engine correctly dropped them — the include-only path was already honored, so only
        // exclusions diverged.
        val excludeRaw = g.stringField("exclude-filter")?.trim().orEmpty()
        if (excludeRaw.isEmpty()) return included
        val excludePatterns = compileFilterPatterns(excludeRaw) ?: return null
        return included.filterNot { name -> excludePatterns.any { it.containsMatchIn(name) } }
    }

    private fun yamlGroupType(g: JsonObject): Proxy.Type {
        val raw = g.stringField("type") ?: return Proxy.Type.Selector
        return when (raw.trim().lowercase()) {
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
    fun parseProxyGroupsPreview(
        snapshot: ProfileSnapshot,
        profileDir: File? = null,
        includeHidden: Boolean = false,
    ): Map<String, ProxyGroupPreviewRow> {
        val out = linkedMapOf<String, ProxyGroupPreviewRow>()
        val outputBudget = PreviewOutputBudget()
        var totalMembers = 0
        for (g in snapshot.proxyGroups.take(PreviewResourceLimits.MAX_GROUPS)) {
            val hidden = g.booleanFlag("hidden")
            if (!includeHidden && hidden) continue
            val name = g.stringField("name")?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= PreviewResourceLimits.MAX_NAME_CHARS }
                ?: continue
            val type = yamlGroupType(g)
            val fromProxies = g.stringList("proxies")
            val useList = g.stringList("use")
            // mihomo treats declared `proxies:` and include-all-* as additive, so a
            // group with both should expose every leaf node alongside the static
            // members. Union via LinkedHashSet to keep declared order first.
            val dynamicMembers = if (hasDynamicMembership(g)) {
                membersForIncludeAllProxiesPreview(snapshot, g, profileDir)
            } else {
                emptyList()
            }
            val dynamicPreviewUnavailable = dynamicMembers == null
            val combined = LinkedHashSet<String>().apply {
                addAll(fromProxies)
                addAll(dynamicMembers.orEmpty())
            }
            val remainingMembers = PreviewResourceLimits.MAX_TOTAL_MEMBERS - totalMembers
            val members = ArrayList<String>(minOf(combined.size, remainingMembers))
            if (!outputBudget.accept(name)) break
            for (member in combined) {
                if (
                    members.size >= PreviewResourceLimits.MAX_MEMBERS_PER_GROUP ||
                    members.size >= remainingMembers ||
                    !outputBudget.accept(member)
                ) break
                members.add(member)
            }
            val staticProxies = fromProxies.asSequence()
                .take(PreviewResourceLimits.MAX_MEMBERS_PER_GROUP)
                .filter { outputBudget.accept(it) }
                .toList()
            when {
                members.isNotEmpty() -> {
                    out[name] = ProxyGroupPreviewRow(type, members, hidden, staticProxies)
                    totalMembers += members.size
                }
                dynamicPreviewUnavailable ->
                    out[name] = ProxyGroupPreviewRow(type, emptyList(), hidden, staticProxies)
                useList.isNotEmpty() ->
                    out[name] = ProxyGroupPreviewRow(type, emptyList(), hidden, staticProxies)
                else -> Unit
            }
            if (totalMembers >= PreviewResourceLimits.MAX_TOTAL_MEMBERS) break
        }
        return out
    }

    /** @see parseProxyGroupsPreview — members only (compatibility). */
    fun parseProxyNamesByGroup(snapshot: ProfileSnapshot): Map<String, List<String>> =
        parseProxyGroupsPreview(snapshot).mapValues { it.value.members }

    /**
     * Every [proxy-groups] `name` in the config (for rule policy validation / pickers).
     * Prefer this over [parseProxyGroupsPreview].keys when **hidden** groups must still appear
     * (e.g. rule targets) — groups with empty or unusual `proxies`/`use` are still listed here.
     * Includes **hidden** names (valid rule targets).
     */
    fun listProxyGroupNames(snapshot: ProfileSnapshot): List<String> {
        return snapshot.proxyGroups.mapNotNull { g ->
            g.stringField("name")?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    /** Names of [proxy-groups] that reference proxy-providers via [use] (merged-style). */
    fun listGroupNamesWithUse(snapshot: ProfileSnapshot): List<String> {
        val out = ArrayList<String>()
        for (g in snapshot.proxyGroups) {
            val use = g["use"] as? JsonArray ?: continue
            if (use.isEmpty()) continue
            val name = g.stringField("name") ?: continue
            out.add(name)
        }
        return out
    }
}
