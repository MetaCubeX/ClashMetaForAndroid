package com.github.kr328.clash.backup

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupRulesSourceGuardTest {
    @Test
    fun companionBearerStoreIsExcludedFromEveryBackupMode() {
        val root = findRepoRoot()
        val legacy = root.resolve("app/src/main/res/xml/full_backup_content.xml").readText()
        val modern = root.resolve("app/src/main/res/xml/data_extraction_rules.xml").readText()
        val exclusion = Regex(
            """<exclude\s+domain="sharedpref"\s+path="companion_controller\.xml"\s*/>""",
        )

        assertEquals(1, exclusion.findAll(legacy).count(), "Auto Backup must exclude companion tokens")
        assertEquals(2, exclusion.findAll(modern).count(), "Cloud backup and device transfer must exclude companion tokens")
    }

    private fun findRepoRoot(): File {
        var current = File(System.getProperty("user.dir") ?: error("Missing user.dir")).absoluteFile
        while (true) {
            if (current.resolve("settings.gradle.kts").isFile) return current
            current = current.parentFile ?: error("Unable to find repository root")
        }
    }
}
