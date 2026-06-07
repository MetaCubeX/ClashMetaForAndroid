package com.github.kr328.clash.service.util

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Layer 1 of the write-pipeline robustness net (pure JVM, no device).
 *
 * Corpus = mihomo's own canonical `docs/config.yaml` (so it tracks the pinned
 * engine version). For each top-level block we re-dump it via the block patcher
 * and assert the re-parsed config deep-equals the original — i.e. our
 * re-serialization is round-trip-stable, no scalar/structure divergence.
 *
 * Because our load AND dump now share the engine-aligned resolver, the round
 * trip is idempotent for every scalar the engine resolves the same way.
 */
class ConfigWritePipelineRoundTripTest {
    private fun corpus(): String? {
        val candidates = listOf(
            "../core/src/foss/golang/clash/docs/config.yaml",
            "core/src/foss/golang/clash/docs/config.yaml",
        )
        return candidates.map(::File).firstOrNull { it.isFile }?.readText()
    }

    @Test
    fun mihomo_canonical_config_round_trips_per_block() {
        val text = corpus()
        assertNotNull(text, "mihomo docs/config.yaml corpus not found")

        val original = MihomoConfigDocument.parse(text)
        assertNotNull(original, "corpus failed to parse with our SnakeYAML")
        val originalRoot = LinkedHashMap(original.root)
        assertTrue(originalRoot.isNotEmpty())

        var checkedBlocks = 0
        for (key in originalRoot.keys) {
            val rendered = MihomoConfigDocument.parseOrThrow(text).renderReplacing(key)
            val reparsed = MihomoConfigDocument.parse(rendered)
            assertNotNull(reparsed, "re-dump of `$key` failed to re-parse")
            assertEquals(
                originalRoot,
                LinkedHashMap(reparsed.root),
                "re-dumping block `$key` changed the parsed config",
            )
            checkedBlocks++
        }
        assertTrue(checkedBlocks > 5, "expected several top-level blocks, got $checkedBlocks")
    }

    @Test
    fun provider_file_full_redump_preserves_edge_scalars_and_names() {
        // Standalone provider doc (just a proxies list) — exercises the full
        // dumpYaml.dump(root) path with the dialect-sensitive cases.
        val provider = """
            proxies:
              - name: "🇩🇪 Berlin"
                type: ss
                server: example.com
                port: 8388
                cipher: aes-128-gcm
                password: "0test"
                udp: off
                tfo: on
              - name: 香港 01
                type: vmess
                server: hk.example.com
                port: 443
                udp: true
        """.trimIndent() + "\n"

        val root = YamlFormatting.parseRootMap(provider)!!
        val dumped = YamlFormatting.blockYaml().dump(root)

        // Names with emoji/CJK preserved verbatim.
        assertTrue(dumped.contains("🇩🇪 Berlin"), "emoji name lost: $dumped")
        assertTrue(dumped.contains("香港 01"), "CJK name lost: $dumped")
        // off/on stay strings (engine semantics), not flipped to false/true.
        assertTrue(Regex("udp:\\s*off").containsMatchIn(dumped), "udp: off flipped: $dumped")
        assertTrue(Regex("tfo:\\s*on").containsMatchIn(dumped), "tfo: on flipped: $dumped")
        // a real bool stays a bool.
        assertTrue(Regex("udp:\\s*true").containsMatchIn(dumped), "udp: true lost: $dumped")

        // And it re-parses to the same structure.
        assertEquals(root, YamlFormatting.parseRootMap(dumped)!!)
    }
}
