package com.github.kr328.clash.agent.settings

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class AgentConfigBackup(
    val profileId: UUID,
    val timestamp: Long,
    val file: File,
    val sha256: String,
)

class AgentBackupStore(context: Context) {
    private val root = context.filesDir.resolve("agent/config-backups").apply { mkdirs() }

    fun create(profileId: UUID, content: String): AgentConfigBackup {
        val directory = root.resolve(profileId.toString()).apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val file = directory.resolve("$timestamp.yaml")
        file.writeText(content)
        prune(directory)
        return AgentConfigBackup(profileId, timestamp, file, sha256(content))
    }

    fun latest(profileId: UUID): AgentConfigBackup? {
        val file = root.resolve(profileId.toString()).listFiles()
            ?.filter { it.isFile && it.extension == "yaml" }
            ?.maxByOrNull { it.nameWithoutExtension.toLongOrNull() ?: 0L }
            ?: return null
        val content = file.readText()
        return AgentConfigBackup(
            profileId,
            file.nameWithoutExtension.toLongOrNull() ?: file.lastModified(),
            file,
            sha256(content),
        )
    }

    fun read(backup: AgentConfigBackup): String = backup.file.readText()

    private fun prune(directory: File) {
        directory.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_BACKUPS_PER_PROFILE)?.forEach { it.delete() }
    }

    companion object {
        private const val MAX_BACKUPS_PER_PROFILE = 20

        fun sha256(content: String): String = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
