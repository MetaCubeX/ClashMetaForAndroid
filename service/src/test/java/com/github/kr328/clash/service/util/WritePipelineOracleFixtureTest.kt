package com.github.kr328.clash.service.util

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.github.kr328.clash.service.model.ProxyHardeningMode

/**
 * Layer 2 (producer half) — emits round-tripped fixtures for the engine-oracle
 * Go test in `native/snapshot` (roundtrip_oracle_test.go). For a curated,
 * engine-valid config carrying the dialect-sensitive cases, we write the
 * original plus a per-top-level-block re-dump. The Go side then asserts
 * `snapshot(original) == snapshot(block re-dump)` — the engine certifying our
 * re-serialization preserves semantics.
 *
 * Run order: this test first (writes fixtures), then `go test ./native/snapshot`.
 * The Go oracle skips itself if the fixtures are absent.
 */
class WritePipelineOracleFixtureTest {
    private val curated = """
        mixed-port: 7890
        proxies:
          - name: "🇩🇪 Berlin"
            type: socks5
            server: 127.0.0.1
            port: 1080
            udp: off
          - name: 香港 01
            type: socks5
            server: 127.0.0.2
            port: 1081
            udp: true
        proxy-groups:
          - name: G
            type: select
            proxies:
              - "🇩🇪 Berlin"
              - 香港 01
        rules:
          - MATCH,G
        dns:
          enable: true
          enhanced-mode: fake-ip
          listen: ":1053"
          nameserver:
            - https://1.1.1.1/dns-query
        hosts:
          example.com: 1.2.3.4
    """.trimIndent() + "\n"

    @Test
    fun emit_roundtrip_fixtures_for_go_oracle() {
        val dir = listOf(
            "../core/src/main/golang/native/snapshot/testdata/roundtrip",
            "core/src/main/golang/native/snapshot/testdata/roundtrip",
        ).map(::File).first { it.parentFile.parentFile.parentFile.exists() }
        dir.mkdirs()

        File(dir, "original.yaml").writeText(curated)

        val root = MihomoConfigDocument.parseOrThrow(curated).root
        var n = 0
        for (key in root.keys) {
            val rendered = MihomoConfigDocument.parseOrThrow(curated).renderReplacing(key)
            File(dir, "block_$key.yaml").writeText(rendered)
            n++
        }
        assertTrue(n >= 5, "expected several blocks, emitted $n")
    }

    @Test
    fun emit_hardened_dns_listener_fixture_for_go_oracle() {
        val dir = listOf(
            "../core/src/main/golang/native/snapshot/testdata/hardening",
            "core/src/main/golang/native/snapshot/testdata/hardening",
        ).map(::File).first { it.parentFile.parentFile.parentFile.exists() }
        dir.mkdirs()

        val unsafe = curated.replace("listen: \":1053\"", "listen: \"[::]:1053\"")
        val hardened = YamlHardener.hardenYaml(unsafe, ProxyHardeningMode.Strict)
        assertNotNull(hardened)
        val dns = MihomoConfigDocument.parseOrThrow(hardened).root["dns"] as Map<*, *>
        assertEquals("127.0.0.1:1053", dns["listen"])
        File(dir, "dns_listener.yaml").writeText(hardened)
    }
}
