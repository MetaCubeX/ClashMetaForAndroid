package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fidelity net for the subscription-update merge/GC (openspec change
 * `subscription-update-fidelity`, Scope MIN). The invariant under test:
 *
 *   the merge may ADD preserved overlays, but it must NEVER DROP a rule-/proxy-
 *   provider the config still references.
 *
 * `gcUnusedRuleProviders` / `gcUnusedProxyProviders` decide "referenced" with a
 * hand-rolled Kotlin scanner that has diverged from mihomo twice (RULE-SET nested
 * in logical rules; provider names with spaces), each time silently dropping a
 * live provider so the next update died with `rule set [X] not found`.
 *
 * The oracle here is deliberately INDEPENDENT of production's capture regex: it is
 * driven by the *known* declared provider key (matches the full key — spaces and
 * all — followed by a field delimiter), so it cannot inherit the same bug. If the
 * production scanner regresses, the matrix below drops a referenced provider and
 * this net fails before it can ship.
 *
 * The mihomo engine is the ultimate oracle, but `Clash.*` is JNI and unavailable
 * in pure-JVM unit tests; an on-engine assertion belongs in the Go `native/snapshot`
 * layer (see tasks.md §3). This Layer-1 net is the always-on CI guard.
 */
class SubscriptionMergeFidelityTest {

    /** A non-empty overlay forces the merge/GC path (mergeAfterFetch returns verbatim when empty). */
    private fun trigger() = SubscriptionUpdateMerge.extractPreserved(
        ProfileSnapshot(rules = listOf("DOMAIN,overlay.local,DIRECT")),
    )

    private fun keysOf(yaml: String, block: String): Set<String> =
        ((MihomoConfigDocument.parseOrEmpty(yaml).root[block] as? Map<*, *>)?.keys ?: emptySet<Any?>())
            .mapNotNull { it?.toString() }.toSet()

    private fun ruleLines(yaml: String): List<String> =
        (MihomoConfigDocument.parseOrEmpty(yaml).root["rules"] as? List<*>).orEmpty()
            .mapNotNull { it?.toString() }

    /**
     * Independent reference oracle: is [key] used by a `RULE-SET,<key>` field anywhere
     * (incl. nested in AND/OR/NOT)? Searches for the FULL known key, so a name with
     * spaces ("Ad Block") is matched correctly regardless of how production captures it.
     */
    private fun referencedByRuleSet(rules: List<String>, key: String): Boolean {
        val needle = "RULE-SET,$key"
        return rules.any { line ->
            var i = line.indexOf(needle, ignoreCase = true)
            while (i >= 0) {
                val after = i + needle.length
                if (after >= line.length || line[after] == ',' || line[after] == ')') return@any true
                i = line.indexOf(needle, i + 1, ignoreCase = true)
            }
            false
        }
    }

    @Test
    fun ruleProviders_referencedSurvive_unreferencedDropped_acrossMatrix() {
        // One config exercising every reference form the GC must recognise, plus a
        // genuinely-unused provider that MUST still be collected.
        val fetched = """
            proxies:
              - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}
            rule-providers:
              setPlain:   {type: http, behavior: domain, url: https://e/x.yaml, path: ./a.yaml}
              setAnd:     {type: http, behavior: domain, url: https://e/x.yaml, path: ./b.yaml}
              setOr:      {type: http, behavior: domain, url: https://e/x.yaml, path: ./c.yaml}
              setNot:     {type: http, behavior: ipcidr, url: https://e/x.yaml, path: ./d.yaml}
              setDeep:    {type: http, behavior: domain, url: https://e/x.yaml, path: ./e.yaml}
              Ad Block:   {type: http, behavior: domain, url: https://e/x.yaml, path: ./f.yaml}
              Media List: {type: http, behavior: domain, url: https://e/x.yaml, path: ./g.yaml}
              Реклама:    {type: http, behavior: domain, url: https://e/x.yaml, path: ./h.yaml}
              unusedSet:  {type: http, behavior: domain, url: https://e/x.yaml, path: ./z.yaml}
            rules:
              - RULE-SET,setPlain,DIRECT
              - AND,((RULE-SET,setAnd),(NETWORK,UDP)),DIRECT
              - OR,((RULE-SET,setOr),(DST-PORT,443)),PROXY
              - NOT,((RULE-SET,setNot)),REJECT
              - OR,((AND,((RULE-SET,setDeep),(DST-PORT,80))),(DOMAIN,a.com)),PROXY
              - RULE-SET,Ad Block,REJECT
              - AND,((RULE-SET,Media List),(NETWORK,UDP)),DIRECT
              - RULE-SET,Реклама,REJECT
              - MATCH,p1
        """.trimIndent() + "\n"

        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, trigger())
        val declared = keysOf(fetched, "rule-providers")
        val survived = keysOf(merged, "rule-providers")
        val rules = ruleLines(fetched)

        for (key in declared) {
            if (referencedByRuleSet(rules, key)) {
                assertTrue("referenced rule-provider '$key' must survive the merge GC", key in survived)
            } else {
                assertTrue("unreferenced rule-provider '$key' must be GC'd", key !in survived)
            }
        }
        // Guard the guard: both arms must actually be exercised by the matrix.
        assertTrue("matrix must keep referenced providers", "Ad Block" in survived && "setDeep" in survived)
        assertTrue("matrix must drop the unused provider", "unusedSet" !in survived)
    }

    @Test
    fun proxyProviders_referencedViaUse_survive_unreferencedDropped() {
        val fetched = """
            proxies:
              - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}
            proxy-providers:
              provUsed:   {type: http, url: https://e/u.yaml, path: ./pu.yaml, interval: 3600}
              provUnused: {type: http, url: https://e/z.yaml, path: ./pz.yaml, interval: 3600}
            proxy-groups:
              - {name: G, type: select, use: [provUsed], proxies: [p1]}
            rules:
              - MATCH,G
        """.trimIndent() + "\n"

        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, trigger())
        val survived = keysOf(merged, "proxy-providers")
        assertTrue("proxy-provider referenced via 'use:' must survive", "provUsed" in survived)
        assertTrue("unreferenced proxy-provider must be GC'd", "provUnused" !in survived)
    }

    @Test
    fun proxyProviders_includeAll_keepsEveryProvider() {
        val fetched = """
            proxies:
              - {name: p1, type: socks5, server: 127.0.0.1, port: 1080}
            proxy-providers:
              provA: {type: http, url: https://e/a.yaml, path: ./pa.yaml, interval: 3600}
              provB: {type: http, url: https://e/b.yaml, path: ./pb.yaml, interval: 3600}
            proxy-groups:
              - {name: G, type: select, include-all-providers: true, proxies: [p1]}
            rules:
              - MATCH,G
        """.trimIndent() + "\n"

        val merged = SubscriptionUpdateMerge.mergeAfterFetch(fetched, trigger())
        val survived = keysOf(merged, "proxy-providers")
        assertTrue("include-all-providers must keep provA", "provA" in survived)
        assertTrue("include-all-providers must keep provB", "provB" in survived)
    }
}
