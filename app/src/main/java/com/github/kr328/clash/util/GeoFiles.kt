package com.github.kr328.clash.util

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

internal val userGeoFileNames: Set<String> = setOf(
    "geoip.metadb",
    "geosite.dat",
    "ASN.mmdb",
)

internal fun shouldInstallGeoAsset(
    exists: Boolean,
    lastModified: Long,
    lastUpdateTime: Long,
    userMarked: Boolean,
): Boolean {
    if (userMarked) return false
    if (!exists) return true
    return lastModified < lastUpdateTime
}

internal fun geoUserSidecar(clashDir: File): File {
    val parent = clashDir.parentFile ?: throw IOException("missing parent")
    return File(parent, "clash-user")
}

internal fun isUserGeoMarked(clashDir: File, name: String): Boolean {
    return File(geoUserSidecar(clashDir), name).isFile
}

internal fun markUserGeoFile(clashDir: File, name: String) {
    if (name !in userGeoFileNames) return
    val dir = geoUserSidecar(clashDir)
    if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) {
        throw IOException("failed to create ${dir.path}")
    }
    val marker = File(dir, name)
    if (!marker.exists()) {
        marker.createNewFile()
    }
    if (!marker.isFile) {
        throw IOException("failed to mark $name")
    }
}

internal fun clearUserGeoMark(clashDir: File, name: String) {
    File(geoUserSidecar(clashDir), name).delete()
}

internal fun replaceFileFromStream(target: File, input: InputStream) {
    val parent = target.parentFile ?: throw IOException("missing parent")
    if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
        throw IOException("failed to create ${parent.path}")
    }
    val tmp = File.createTempFile(".${target.name}.", ".partial", parent)
    var published = false
    try {
        FileOutputStream(tmp).use { output ->
            input.copyTo(output)
        }
        published = tmp.renameTo(target)
        if (!published) {
            throw IOException("failed to replace ${target.name}")
        }
    } finally {
        if (!published && tmp.exists()) tmp.delete()
    }
}

internal fun installBundledGeoFile(
    clashDir: File,
    name: String,
    lastUpdateTime: Long,
    userManaged: Boolean,
    open: () -> InputStream,
) {
    val target = File(clashDir, name)
    val managed = userManaged && name in userGeoFileNames
    withGeoInstallLock(clashDir, name) {
        if (managed && !target.exists()) {
            clearUserGeoMark(clashDir, name)
            if (isUserGeoMarked(clashDir, name)) {
                throw IOException("failed to clear mark $name")
            }
        }
        val marked = managed && target.exists() && isUserGeoMarked(clashDir, name)
        if (!shouldInstallGeoAsset(target.exists(), target.lastModified(), lastUpdateTime, marked)) {
            return@withGeoInstallLock
        }
        open().use { input ->
            replaceFileFromStream(target, input)
        }
    }
}

internal fun importReplacingGeoFile(clashDir: File, name: String, input: InputStream) {
    val target = File(clashDir, name)
    withGeoInstallLock(clashDir, name) {
        replaceFileFromStream(target, input)
        markUserGeoFile(clashDir, name)
    }
}

private fun <T> withGeoInstallLock(clashDir: File, name: String, block: () -> T): T {
    val dir = geoUserSidecar(clashDir)
    if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) {
        throw IOException("failed to create ${dir.path}")
    }
    val lockFile = File(dir, ".$name.lock")
    return FileOutputStream(lockFile).use { stream ->
        val lock = stream.channel.lock()
        try {
            block()
        } finally {
            if (lock.isValid) lock.release()
        }
    }
}
