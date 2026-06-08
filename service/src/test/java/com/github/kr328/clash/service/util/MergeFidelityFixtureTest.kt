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
              - AND,((RULE-SET,used),(DST-PORT,443)),G
              - MATCH,Pick
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
