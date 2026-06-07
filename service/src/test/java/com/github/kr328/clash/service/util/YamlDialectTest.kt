package com.github.kr328.clash.service.util

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins the YAML-dialect contract for our WRITE pipeline: a scalar we parse and
 * re-dump must resolve the way the mihomo engine (yaml.v3, YAML 1.2 core) sees
 * it — NOT YAML 1.1. These assert the DESIRED behavior; with the current default
 * SnakeYAML resolver several are expected to FAIL (off→false, 0755→493, …) until
 * the resolver is aligned to 1.2 core.
 */
class YamlDialectTest {
    private fun roundTrip(yaml: String): String =
        YamlFormatting.blockYaml().dump(YamlFormatting.parseRootMap(yaml)!!).trim()

    private fun assertPreserved(input: String, token: String) {
        val out = roundTrip("k: $input")
        assertTrue(out.contains(token), "round-trip of `$input` lost it: got `$out`")
    }

    @Test fun bool_barewords_stay_strings() {
        // 1.1 coerces these to bool; 1.2 (engine) keeps them as strings.
        assertPreserved("off", "off")
        assertPreserved("on", "on")
        assertPreserved("yes", "yes")
        assertPreserved("no", "no")
    }

    @Test fun real_booleans_stay_booleans() {
        assertPreserved("true", "true")
        assertPreserved("false", "false")
    }

    @Test fun sexagesimal_stays_string() {
        // Engine (yaml.v3) keeps `22:00` a string; SnakeYAML 1.1 makes it int 1320.
        assertPreserved("22:00", "22:00")
    }

    @Test fun aligned_integer_forms_unchanged() {
        // octal/underscore: the ENGINE coerces these too (0755->493, 1_000->1000),
        // so our coercion is value-identical — no corruption, leave as-is.
        assertPreserved("0755", "493")
        assertPreserved("1_000", "1000")
        assertPreserved("7890", "7890")
    }

    @Test fun emoji_and_cjk_names_survive() {
        val out = YamlFormatting.blockYaml().dump(
            YamlFormatting.parseRootMap("name: \"🇩🇪 Berlin\"")!!,
        )
        assertTrue(out.contains("🇩🇪"), "emoji escaped/lost: $out")
        val cjk = YamlFormatting.blockYaml().dump(YamlFormatting.parseRootMap("name: 香港")!!)
        assertTrue(cjk.contains("香港"), "CJK escaped/lost: $cjk")
    }
}
