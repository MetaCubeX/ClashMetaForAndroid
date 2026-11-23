package com.github.kr328.clash.service

import java.io.File
import java.util.UUID

/**
 * Local display order for the profile list. `createdAt` stays the creation time.
 * The file is one UUID per line. Missing profiles are dropped. Profiles that are
 * not listed yet keep their previous relative order and go after the saved ones.
 */
internal fun mergeProfileOrder(saved: List<UUID>, existing: List<UUID>): List<UUID> {
    if (saved.isEmpty()) return existing
    val present = existing.toSet()
    val ordered = saved.filter { it in present }.distinct()
    val seen = ordered.toSet()
    return ordered + existing.filter { it !in seen }
}

internal class ProfileOrderStore(private val file: File) {
    private val gate = Any()

    fun read(): List<UUID> {
        synchronized(gate) {
            if (!file.exists()) return emptyList()
            return file.readLines().mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) null else runCatching { UUID.fromString(trimmed) }.getOrNull()
            }
        }
    }

    fun write(uuids: List<UUID>) {
        synchronized(gate) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(uuids.joinToString("\n"))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    fun remove(uuid: UUID) {
        synchronized(gate) {
            val current = readUnlocked()
            if (uuid !in current) return
            writeUnlocked(current.filter { it != uuid })
        }
    }

    private fun readUnlocked(): List<UUID> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) null else runCatching { UUID.fromString(trimmed) }.getOrNull()
        }
    }

    private fun writeUnlocked(uuids: List<UUID>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(uuids.joinToString("\n"))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
