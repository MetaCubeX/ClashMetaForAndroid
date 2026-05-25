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
    ): JsonObject {
        val fields = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        fields["name"] = JsonPrimitive(name)
        fields["type"] = JsonPrimitive(type)
        if (proxies.isNotEmpty()) fields["proxies"] = JsonArray(proxies.map { JsonPrimitive(it) })
        if (use.isNotEmpty()) fields["use"] = JsonArray(use.map { JsonPrimitive(it) })
        if (hidden) fields["hidden"] = JsonPrimitive(true)
        if (includeAllProxies) fields["include-all-proxies"] = JsonPrimitive(true)
        if (filter != null) fields["filter"] = JsonPrimitive(filter)
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
}
