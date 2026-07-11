package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import com.github.kr328.clash.core.model.Proxy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyGroupsYamlPreviewTest {

    private fun group(
        name: String,
        type: String = "select",
        proxies: List<String> = emptyList(),
        use: List<String> = emptyList(),
        hidden: Boolean = false,
        includeAllProxies: Boolean = false,
        filter: String? = null,
        excludeFilter: String? = null,
    ): JsonObject {
        val fields = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        fields["name"] = JsonPrimitive(name)
        fields["type"] = JsonPrimitive(type)
        if (proxies.isNotEmpty()) fields["proxies"] = JsonArray(proxies.map { JsonPrimitive(it) })
        if (use.isNotEmpty()) fields["use"] = JsonArray(use.map { JsonPrimitive(it) })
        if (hidden) fields["hidden"] = JsonPrimitive(true)
        if (includeAllProxies) fields["include-all-proxies"] = JsonPrimitive(true)
        if (filter != null) fields["filter"] = JsonPrimitive(filter)
        if (excludeFilter != null) fields["exclude-filter"] = JsonPrimitive(excludeFilter)
        return JsonObject(fields)
    }

    private fun proxy(name: String): JsonObject = JsonObject(
        mapOf("name" to JsonPrimitive(name), "type" to JsonPrimitive("ss")),
    )

    @Test
    fun listProxyGroupNames_returnsAllNamesIncludingHidden() {
        val snapshot = ProfileSnapshot(
            proxyGroups = listOf(
                group("GLOBAL"),
                group("Main"),
                group("Auto", hidden = true),
            ),
        )
        assertEquals(
            listOf("GLOBAL", "Main", "Auto"),
            ProxyGroupsYamlPreview.listProxyGroupNames(snapshot),
        )
    }

    @Test
    fun listGroupNamesWithUse_onlyMergedStyleGroups() {
        val snapshot = ProfileSnapshot(
            proxyGroups = listOf(
                group("Inline", proxies = listOf("node-a")),
                group("Merged", use = listOf("sub1")),
                group("Mixed", proxies = listOf("node-b"), use = listOf("sub2")),
                group("EmptyUse"), // no `use` at all
            ),
        )
        assertEquals(listOf("Merged", "Mixed"), ProxyGroupsYamlPreview.listGroupNamesWithUse(snapshot))
    }

    @Test
    fun parseProxyGroupsPreview_skipsHiddenGroups() {
        val snapshot = ProfileSnapshot(
            proxyGroups = listOf(
                group("Visible", proxies = listOf("node-a")),
                group("Secret", hidden = true, proxies = listOf("node-b")),
            ),
        )
        val rows = ProxyGroupsYamlPreview.parseProxyGroupsPreview(snapshot)
        assertEquals(setOf("Visible"), rows.keys)
    }

    @Test
    fun parseProxyGroupsPreview_includeHiddenReturnsAllGroups() {
        // Offline preview that backs the expanded-carriage pills must match
        // the live engine's queryAllProxyGroupNamesIncludingHidden, otherwise
        // a kaso-style subscription (visible select root + entire url-test
        // subtree hidden) gets no rows during the warmup race.
        val snapshot = ProfileSnapshot(
            proxyGroups = listOf(
                group("Visible", proxies = listOf("node-a")),
                group("HiddenAuto", hidden = true, type = "url-test", proxies = listOf("node-b")),
                group("HiddenFallback", hidden = true, type = "fallback", proxies = listOf("node-c")),
            ),
        )
        val rows = ProxyGroupsYamlPreview.parseProxyGroupsPreview(snapshot, includeHidden = true)
        assertEquals(setOf("Visible", "HiddenAuto", "HiddenFallback"), rows.keys)
        // Hidden flag must round-trip — the adapter uses it for the 1-hop
        // heuristic that decides which hidden groups to surface as pills.
        assertFalse(rows["Visible"]!!.hidden)
        assertTrue(rows["HiddenAuto"]!!.hidden)
        assertTrue(rows["HiddenFallback"]!!.hidden)
    }

    @Test
    fun parseProxyGroupsPreview_decodesGroupType() {
        val snapshot = ProfileSnapshot(
            proxyGroups = listOf(
                group("S", type = "select", proxies = listOf("a")),
                group("U", type = "url-test", proxies = listOf("a")),
                group("L", type = "load-balance", proxies = listOf("a")),
                group("F", type = "fallback", proxies = listOf("a")),
                group("R", type = "relay", proxies = listOf("a")),
                group("X", type = "unknown-future-type", proxies = listOf("a")),
            ),
        )
        val rows = ProxyGroupsYamlPreview.parseProxyGroupsPreview(snapshot)
        assertEquals(Proxy.Type.Selector, rows["S"]?.type)
        assertEquals(Proxy.Type.URLTest, rows["U"]?.type)
        assertEquals(Proxy.Type.LoadBalance, rows["L"]?.type)
        assertEquals(Proxy.Type.Fallback, rows["F"]?.type)
        assertEquals(Proxy.Type.Relay, rows["R"]?.type)
        // Unknown types fall back to Selector so picker stays functional.
        assertEquals(Proxy.Type.Selector, rows["X"]?.type)
    }

    @Test
    fun parseProxyGroupsPreview_useOnlyGroupShowsEmptyMembers() {
        // Provider-backed group: `use:` keys are provider names, not dialable
        // proxy names. Preview returns the group with an empty member list -
        // live engine fills these in at runtime.
        val snapshot = ProfileSnapshot(
            proxyGroups = listOf(group("Auto", type = "url-test", use = listOf("sub1"))),
        )
        val rows = ProxyGroupsYamlPreview.parseProxyGroupsPreview(snapshot)
        assertNotNull(rows["Auto"])
        assertTrue(rows["Auto"]?.members.orEmpty().isEmpty())
    }

    @Test
    fun parseProxyGroupsPreview_includeAllProxiesUnionsDeclaredAndUniverse() {
        // include-all-proxies expands to every inline proxy at runtime.
        // Preview should reflect that.
        val snapshot = ProfileSnapshot(
            proxies = listOf(proxy("node-a"), proxy("node-b"), proxy("node-c")),
            proxyGroups = listOf(
                group("All", includeAllProxies = true),
                group("Mixed", proxies = listOf("manual-x"), includeAllProxies = true),
            ),
        )
        val rows = ProxyGroupsYamlPreview.parseProxyGroupsPreview(snapshot)
        // "All" gets just the universe (sorted by mihomo semantics).
        assertEquals(listOf("node-a", "node-b", "node-c"), rows["All"]?.members)
        // "Mixed" gets declared first, then universe.
        assertEquals(listOf("manual-x", "node-a", "node-b", "node-c"), rows["Mixed"]?.members)
        // staticProxies must reflect ONLY the YAML `proxies:` list, never the
        // include-all-* expansion. The 1-hop pill-bar heuristic relies on this
        // to tell a kaso-style dispatch shell from a normal group whose member
        // list happens to be all leaves due to dynamic expansion.
        assertEquals(emptyList<String>(), rows["All"]?.staticProxies)
        assertEquals(listOf("manual-x"), rows["Mixed"]?.staticProxies)
    }

    @Test
    fun parseProxyGroupsPreview_includeAllProxiesWithFilterAppliesRegex() {
        val snapshot = ProfileSnapshot(
            proxies = listOf(proxy("us-east"), proxy("us-west"), proxy("eu-central")),
            proxyGroups = listOf(group("UsOnly", includeAllProxies = true, filter = "us-")),
        )
        val rows = ProxyGroupsYamlPreview.parseProxyGroupsPreview(snapshot)
        assertEquals(listOf("us-east", "us-west"), rows["UsOnly"]?.members)
    }

    @Test
    fun untrustedFilterRejectsBacktrackingAndExtendedRegexShapes() {
        assertFalse(ProxyGroupsYamlPreview.isSafeFilterPattern("(a+)+$"))
        assertFalse(ProxyGroupsYamlPreview.isSafeFilterPattern("(?:a|aa)+$"))
        assertFalse(ProxyGroupsYamlPreview.isSafeFilterPattern("(?=attacker)"))
        assertFalse(ProxyGroupsYamlPreview.isSafeFilterPattern("(.)\\1"))
        assertFalse(ProxyGroupsYamlPreview.isSafeFilterPattern("a".repeat(257)))
        assertTrue(ProxyGroupsYamlPreview.isSafeFilterPattern("^(US|DE)-[0-9]$"))

        val snapshot = ProfileSnapshot(
            proxies = listOf(proxy("a".repeat(400) + "!")),
            proxyGroups = listOf(group("Unsafe", includeAllProxies = true, filter = "(a+)+$")),
        )
        val unsafe = ProxyGroupsYamlPreview.parseProxyGroupsPreview(snapshot)["Unsafe"]
        assertNotNull("unsafe filter must preserve the group as preview-unavailable", unsafe)
        assertTrue(unsafe!!.members.isEmpty())
    }

    @Test
    fun unsupportedCommonFilterPreservesDynamicGroupWithoutRunningJvmRegex() {
        val snapshot = ProfileSnapshot(
            proxies = listOf(proxy("US-1"), proxy("DE-1")),
            proxyGroups = listOf(
                group("InlineFlag", includeAllProxies = true, filter = "(?i)^us-"),
                group("QuantifiedGroup", includeAllProxies = true, filter = "(US|DE)+"),
                group("UnsafeExclude", includeAllProxies = true, excludeFilter = "(a+)+$"),
            ),
        )

        val rows = ProxyGroupsYamlPreview.parseProxyGroupsPreview(snapshot)

        assertEquals(setOf("InlineFlag", "QuantifiedGroup", "UnsafeExclude"), rows.keys)
        assertTrue(rows.values.all { it.members.isEmpty() })
    }

    @Test
    fun parseProxyGroupsPreview_includeAllProxiesWithExcludeFilterDropsMatches() {
        // Repro of the user report: exclude-filter must drop members in the offline preview too,
        // mirroring the engine. Include path was already honored; exclusion was ignored.
        val snapshot = ProfileSnapshot(
            proxies = listOf(
                proxy("🇩🇪 Germany"), proxy("🇫🇮 Finland"), proxy("🇳🇱 Netherlands 1"), proxy("🇸🇪 Sweden"),
            ),
            proxyGroups = listOf(group("Fastest", includeAllProxies = true, excludeFilter = "🇸🇪|SE|Sweden")),
        )
        val members = ProxyGroupsYamlPreview.parseProxyGroupsPreview(snapshot)["Fastest"]?.members.orEmpty()
        assertFalse("Sweden must be excluded from the preview", members.any { "Sweden" in it })
        assertEquals(listOf("🇩🇪 Germany", "🇫🇮 Finland", "🇳🇱 Netherlands 1"), members)
    }

    @Test
    fun parseProxyGroupsPreview_excludeFilterAppliesAfterIncludeFilter() {
        // filter (include) then exclude-filter, both honored — the working `filter` group and the
        // broken `exclude-filter` group must now behave consistently.
        val snapshot = ProfileSnapshot(
            proxies = listOf(proxy("us-east"), proxy("us-west"), proxy("us-lab"), proxy("eu-central")),
            proxyGroups = listOf(
                group("UsNoLab", includeAllProxies = true, filter = "us-", excludeFilter = "lab"),
            ),
        )
        assertEquals(
            listOf("us-east", "us-west"),
            ProxyGroupsYamlPreview.parseProxyGroupsPreview(snapshot)["UsNoLab"]?.members,
        )
    }

    @Test
    fun parseProxyGroupsPreview_skipsGroupsWithoutMembersOrUse() {
        // A bare `name + type` group with neither proxies nor use is dropped -
        // there's nothing meaningful for the picker to show.
        val snapshot = ProfileSnapshot(
            proxyGroups = listOf(group("Empty"), group("HasMembers", proxies = listOf("a"))),
        )
        val rows = ProxyGroupsYamlPreview.parseProxyGroupsPreview(snapshot)
        assertFalse(rows.containsKey("Empty"))
        assertTrue(rows.containsKey("HasMembers"))
    }

    @Test
    fun parseProxyGroupsPreview_boundsNamesGroupsAndMembersForIpc() {
        val longName = "x".repeat(PreviewResourceLimits.MAX_NAME_CHARS + 1)
        val members = (0 until PreviewResourceLimits.MAX_MEMBERS_PER_GROUP + 50).map { "node-$it" }
        val groups = buildList {
            add(group(longName, proxies = listOf("ignored")))
            add(group("bounded", proxies = members))
            repeat(PreviewResourceLimits.MAX_GROUPS + 10) { add(group("group-$it", proxies = listOf("node"))) }
        }

        val rows = ProxyGroupsYamlPreview.parseProxyGroupsPreview(ProfileSnapshot(proxyGroups = groups))

        assertFalse(rows.containsKey(longName))
        assertTrue(rows.size <= PreviewResourceLimits.MAX_GROUPS)
        assertEquals(PreviewResourceLimits.MAX_MEMBERS_PER_GROUP, rows["bounded"]?.members?.size)
        assertTrue(rows.values.sumOf { it.members.size } <= PreviewResourceLimits.MAX_TOTAL_MEMBERS)
        assertTrue(
            rows.entries.sumOf { (name, row) ->
                name.length + row.members.sumOf(String::length) + row.staticProxies.sumOf(String::length)
            } <= PreviewResourceLimits.MAX_OUTPUT_CHARS,
        )
    }
}
