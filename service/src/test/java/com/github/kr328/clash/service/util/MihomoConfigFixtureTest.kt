package com.github.kr328.clash.service.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MihomoConfigFixtureTest {
    @Test
    fun canonicalMihomoConfigFixtureParsesAsSupportedShape() {
        val fixture = findRepoRoot().resolve("docs/config.yaml")
        assertTrue("docs/config.yaml fixture must exist", fixture.isFile)

        val text = fixture.readText()
        val root = YamlFormatting.parseRootMap(text)
        assertNotNull("docs/config.yaml must parse as a YAML root map", root)
        root ?: return

        YamlPreviewSupport.validateConfigYaml(text)

        assertTrue("proxies must be a list", root["proxies"] is List<*>)
        assertTrue("proxy-groups must be a list", root["proxy-groups"] is List<*>)
        assertTrue("rules must be a list", root["rules"] is List<*>)
        assertTrue("proxy-providers must be a map", root["proxy-providers"] is Map<*, *>)
        assertTrue("rule-providers must be a map", root["rule-providers"] is Map<*, *>)
        root["listeners"]?.let {
            assertTrue("listeners must be a list when present", it is List<*>)
        }
    }

    private fun findRepoRoot(): File {
        var current = File(System.getProperty("user.dir") ?: error("user.dir is not set")).absoluteFile
        while (true) {
            if (File(current, "settings.gradle.kts").isFile && File(current, "docs").isDirectory) {
                return current
            }
            current = current.parentFile ?: error("Unable to locate repository root from user.dir")
        }
    }
}
