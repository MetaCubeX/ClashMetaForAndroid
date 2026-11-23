package com.github.kr328.clash.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ProfileDirectoryTest {
    @Test
    fun failedDirectoryReplaceLeavesTheImportedTree() {
        withTrees { imported, processing ->
            try {
                commitProfileDirectory(File(imported.parentFile, "missing-source"), imported)
                throw AssertionError("missing source should fail")
            } catch (_: IOException) {
            }
            assertOld(imported)
            processing.deleteRecursively()
            assertOld(imported)
        }

        withTrees { imported, processing ->
            stageProfileDirectory(processing, profileCommitStaging(imported))
            assertOld(imported)
            assertTrue(profileTreesMatch(processing, profileCommitStaging(imported)))
            processing.deleteRecursively()
            assertOld(imported)
        }

        withTrees { imported, processing ->
            try {
                replaceProfileDirectory(File(imported.parentFile, "missing-staging"), imported)
                throw AssertionError("missing staging should fail")
            } catch (_: IOException) {
            }
            assertOld(imported)
            processing.deleteRecursively()
            assertOld(imported)
        }

        withTrees { imported, processing ->
            val staging = profileCommitStaging(imported)
            stageProfileDirectory(processing, staging)
            try {
                replaceProfileDirectory(
                    staging,
                    imported,
                    move = { from, target ->
                        if (from.name.endsWith(PROFILE_COMMIT_STAGING_SUFFIX)) false else from.renameTo(target)
                    },
                )
                throw AssertionError("failed place should fail")
            } catch (err: IOException) {
                assertEquals("profile replace did not land", err.message)
            }
            assertOld(imported)
            processing.deleteRecursively()
            assertOld(imported)
        }
    }

    @Test
    fun interruptedSwapIsRestoredBeforeProcessingIsCleared() {
        withTrees { imported, processing ->
            assertTrue(imported.renameTo(profileCommitBackup(imported)))
            assertFalse(imported.exists())
            recoverImportedDirectory(imported)
            processing.deleteRecursively()
            assertOld(imported)
        }
    }

    @Test
    fun completeDirectoryReplacesTheImportedTree() {
        withTrees { imported, processing ->
            commitProfileDirectory(processing, imported)
            assertEquals("new", File(imported, "config.yaml").readText())
            assertEquals("new-provider", File(imported, "providers/extra.yaml").readText())
            assertFalse(File(imported, "old-only.txt").exists())
            assertFalse(profileCommitStaging(imported).exists())
            assertFalse(profileCommitBackup(imported).exists())
            processing.deleteRecursively()
            assertEquals("new", File(imported, "config.yaml").readText())
            assertEquals("new-provider", File(imported, "providers/extra.yaml").readText())
        }
    }

    private fun assertOld(imported: File) {
        assertEquals("old", File(imported, "config.yaml").readText())
        assertEquals("old-provider", File(imported, "providers/extra.yaml").readText())
        assertEquals("keep", File(imported, "old-only.txt").readText())
    }

    private fun withTrees(block: (imported: File, processing: File) -> Unit) {
        val root = Files.createTempDirectory("profile-commit").toFile()
        try {
            val imported = File(root, "imported/profile")
            val processing = File(root, "processing")
            writeTree(imported, "old", "old-provider")
            File(imported, "old-only.txt").writeText("keep")
            writeTree(processing, "new", "new-provider")
            block(imported, processing)
        } finally {
            root.walkBottomUp().forEach { it.setWritable(true) }
            root.deleteRecursively()
        }
    }

    private fun writeTree(root: File, config: String, provider: String) {
        File(root, "providers").mkdirs()
        File(root, "config.yaml").writeText(config)
        File(root, "providers/extra.yaml").writeText(provider)
    }
}
