package com.github.kr328.clash.service.clash.module

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConfigurationModuleSourceGuardTest {
    @Test
    fun sessionOverrideIsPatchedBeforeProfileLoad() {
        val source = findRepoRoot()
            .resolve("service/src/main/java/com/github/kr328/clash/service/clash/module/ConfigurationModule.kt")
            .readText()

        val overrideIndex = source.indexOf("applySessionOverrideBeforeLoad()\n                        Clash.load(profileDir).await()")
        val loadIndex = source.indexOf("Clash.load(profileDir).await()")

        assertTrue("ConfigurationModule must patch the session override immediately before Clash.load", overrideIndex >= 0)
        assertTrue("ConfigurationModule must load the profile", loadIndex >= 0)
        assertTrue(
            "Runtime auth/session hardening must be present before Mihomo parses and applies the profile",
            overrideIndex < loadIndex,
        )
    }

    private fun findRepoRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            if (current.resolve("settings.gradle.kts").isFile) return current
            current = current.parentFile ?: error("Unable to find repository root")
        }
    }
}
