package com.github.kr328.clash.service

import java.io.File
import java.io.IOException

internal const val PROFILE_COMMIT_STAGING_SUFFIX = ".commit-tmp"
internal const val PROFILE_COMMIT_BACKUP_SUFFIX = ".commit-bak"

/**
 * Copies [source] to a sibling of [destination] and swaps that copy into place.
 * [destination] is left as it was when the copy or the swap fails.
 */
internal fun commitProfileDirectory(source: File, destination: File) {
    val parent = destination.parentFile ?: throw IOException("imported profile has no parent")
    if (!parent.isDirectory && !parent.mkdirs()) {
        throw IOException("cannot create imported parent")
    }
    recoverImportedDirectory(destination)
    val staging = profileCommitStaging(destination)
    stageProfileDirectory(source, staging)
    replaceProfileDirectory(staging, destination)
}

internal fun stageProfileDirectory(source: File, staging: File) {
    if (!source.isDirectory) throw IOException("profile source is missing")
    if (profilesOverlap(source, staging)) throw IOException("profile staging overlaps source")
    if (staging.exists() && !staging.deleteRecursively()) {
        throw IOException("cannot clear profile staging")
    }
    try {
        if (!source.copyRecursively(staging, overwrite = true) || !profileTreesMatch(source, staging)) {
            throw IOException("profile staging is incomplete")
        }
    } catch (err: Exception) {
        staging.deleteRecursively()
        throw err
    }
}

internal fun profileTreesMatch(expected: File, actual: File): Boolean {
    if (!expected.isDirectory || !actual.isDirectory) return false
    val expectedPaths = relativeEntries(expected)
    val actualPaths = relativeEntries(actual)
    if (expectedPaths != actualPaths) return false
    return expectedPaths.all { relative ->
        val left = expected.resolve(relative)
        val right = actual.resolve(relative)
        if (left.isDirectory || right.isDirectory) {
            left.isDirectory && right.isDirectory
        } else {
            left.readBytes().contentEquals(right.readBytes())
        }
    }
}

/**
 * Swaps a complete [staging] tree onto [destination].
 * The live directory is not removed until [staging] is complete, and a failed swap puts it back.
 */
internal fun replaceProfileDirectory(
    staging: File,
    destination: File,
    move: (File, File) -> Boolean = File::renameTo,
) {
    if (!staging.isDirectory) throw IOException("profile staging is missing")
    if (profilesOverlap(staging, destination)) throw IOException("profile staging overlaps imported profile")
    recoverImportedDirectory(destination)
    val backup = profileCommitBackup(destination)
    if (profilesOverlap(staging, backup)) throw IOException("profile backup overlaps staging")
    val hadDestination = destination.exists()
    if (hadDestination) {
        if (backup.exists() && !backup.deleteRecursively()) {
            throw IOException("cannot clear profile backup")
        }
        if (!move(destination, backup) || !backup.isDirectory) {
            throw IOException("cannot move imported profile aside")
        }
    }
    try {
        if (!move(staging, destination) || !destination.isDirectory) {
            throw IOException("profile replace did not land")
        }
    } catch (err: Exception) {
        if (destination.exists() && destination.canonicalFile != backup.canonicalFile) {
            destination.deleteRecursively()
        }
        if (hadDestination && backup.exists() && (!move(backup, destination) || !destination.isDirectory)) {
            throw IOException("cannot restore imported profile", err)
        }
        throw err
    }
    if (staging.exists()) staging.deleteRecursively()
    if (hadDestination && backup.exists()) backup.deleteRecursively()
}

/** Puts an interrupted swap back at [destination] when the live directory is missing. */
internal fun recoverImportedDirectory(destination: File) {
    if (destination.exists()) return
    val backup = profileCommitBackup(destination)
    if (!backup.isDirectory) return
    if (!backup.renameTo(destination)) {
        throw IOException("cannot restore imported profile")
    }
}

internal fun profileCommitStaging(destination: File): File {
    val parent = destination.parentFile ?: throw IOException("imported profile has no parent")
    return File(parent, destination.name + PROFILE_COMMIT_STAGING_SUFFIX)
}

internal fun profileCommitBackup(destination: File): File {
    val parent = destination.parentFile ?: throw IOException("imported profile has no parent")
    return File(parent, destination.name + PROFILE_COMMIT_BACKUP_SUFFIX)
}

private fun profilesOverlap(left: File, right: File): Boolean {
    val first = left.canonicalFile
    val second = right.canonicalFile
    return first == second || first.startsWith(second) || second.startsWith(first)
}

private fun relativeEntries(root: File): Set<String> {
    return root.walkTopDown()
        .filter { it != root }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .toSet()
}
