package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Layer 2 (producer half) of the subscription-update-fidelity net. For an
 * adversarial corpus of mihomo rule/provider/group forms, emit the fetched config
 * and its `mergeAfterFetch` result as fixtures. The Go oracle
 * (merge_fidelity_oracle_test.go) then asserts SNAPSHOT CONTAINMENT — every
 * proxy/group/rule/provider the fetched subscription shipped survives the merge.
 *
 * Run order: this test first (emits), then `go test ./native/snapshot`.
 */
class MergeFidelityFixtureTest {
    // Each case is a complete, engine-parseable fetched subscription whose
    // providers could be wrongly GC'd by the merge.
    private val corpus = mapOf(
        "logical_ruleset" to """
            proxies:
              - {name: n1, type: socks5, server: 127.0.0.1, port: 1080}
            rule-providers:
              waSet: {type: http, behavior: domain, url: https://e/wa.yaml, path: ./wa.yaml}
            proxy-groups:
              - {name: G, type: select, proxies: [n1]}
            rules:
              - OR,((RULE-SET,waSet),(NETWORK,UDP)),DIRECT
              - MATCH,G
        """.trimIndent() + "\n",
        "provider_include_all" to """
            proxies:
              - {name: n1, type: socks5, server: 127.0.0.1, port: 1080}
            proxy-providers:
              provA: {type: http, url: https://e/a.yaml, path: ./a.yaml}
              provB: {type: http, url: https://e/b.yaml, path: ./b.yaml}
            proxy-groups:
              - {name: G, type: select, include-all-providers: true}
            rules:
              - MATCH,G
        """.trimIndent() + "\n",
        "provider_via_use" to """
            proxies:
              - {name: n1, type: socks5, server: 127.0.0.1, port: 1080}
            proxy-providers:
              used: {type: http, url: https://e/u.yaml, path: ./u.yaml}
            proxy-groups:
              - {name: G, type: select, use: [used]}
              - {name: Pick, type: select, proxies: [n1, G]}
            rules:
              - MATCH,Pick
        """.trimIndent() + "\n",
        // The actual 2026-06-22 user bug: a rule-provider whose NAME contains a space.
        // The GC's capture regex stopped at whitespace → "Ad Block" dropped → engine
        // rejects merged with `rule set [Ad Block] not found`. The Go oracle catches it
        // BOTH ways: engine rejects the merged fixture AND the containment check finds
        // the lost rule-provider.
        "ruleset_name_with_spaces" to """
            proxies:
              - {name: n1, type: socks5, server: 127.0.0.1, port: 1080}
            rule-providers:
              Ad Block: {type: http, behavior: domain, url: https://e/ab.yaml, path: ./ab.yaml}
            proxy-groups:
              - {name: G, type: select, proxies: [n1]}
            rules:
              - RULE-SET,Ad Block,REJECT
              - MATCH,G
        """.trimIndent() + "\n",
        "ruleset_spaces_nested_in_logical" to """
            proxies:
              - {name: n1, type: socks5, server: 127.0.0.1, port: 1080}
            rule-providers:
              Media List: {type: http, behavior: domain, url: https://e/ml.yaml, path: ./ml.yaml}
            proxy-groups:
              - {name: G, type: select, proxies: [n1]}
            rules:
              - AND,((RULE-SET,Media List),(NETWORK,UDP)),DIRECT
              - MATCH,G
        """.trimIndent() + "\n",
        "ruleset_name_unicode" to """
            proxies:
              - {name: n1, type: socks5, server: 127.0.0.1, port: 1080}
            rule-providers:
              Реклама: {type: http, behavior: domain, url: https://e/rk.yaml, path: ./rk.yaml}
            proxy-groups:
              - {name: G, type: select, proxies: [n1]}
            rules:
              - RULE-SET,Реклама,REJECT
              - MATCH,G
        """.trimIndent() + "\n",
        "ruleset_deeply_nested" to """
            proxies:
              - {name: n1, type: socks5, server: 127.0.0.1, port: 1080}
            rule-providers:
              deepSet: {type: http, behavior: domain, url: https://e/d.yaml, path: ./d.yaml}
            proxy-groups:
              - {name: G, type: select, proxies: [n1]}
            rules:
              - OR,((AND,((RULE-SET,deepSet),(DST-PORT,80))),(DOMAIN,a.com)),REJECT
              - MATCH,G
        """.trimIndent() + "\n",
    )

    @Test
    fun emit_merge_fidelity_fixtures() {
        val dir = listOf(
            "../core/src/main/golang/native/snapshot/testdata/merge",
            "core/src/main/golang/native/snapshot/testdata/merge",
        ).map(::File).first { it.parentFile.parentFile.parentFile.exists() }
        dir.mkdirs()

        // Non-empty overlay so the merge (and its GC) actually runs.
        val preserved = SubscriptionUpdateMerge.extractPreserved(
            ProfileSnapshot(
                rules = listOf("DOMAIN,keep.local,DIRECT"),
                ruleProviders = emptyMap(),
                proxyProviders = emptyMap(),
                proxyGroups = emptyList(),
                listeners = emptyList(),
            ),
        )

        for ((name, fetched) in corpus) {
            File(dir, "${name}__fetched.yaml").writeText(fetched)
            File(dir, "${name}__merged.yaml")
                .writeText(SubscriptionUpdateMerge.mergeAfterFetch(fetched, preserved))
        }
        assertTrue(corpus.size >= 3)
    }
}
