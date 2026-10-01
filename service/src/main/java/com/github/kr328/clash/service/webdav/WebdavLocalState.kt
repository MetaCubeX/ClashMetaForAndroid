package com.github.kr328.clash.service.webdav

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File
import java.io.RandomAccessFile

@Serializable
data class LocalState(
    val clientId: String = "",
    val tombstones: List<TombstonePayload> = emptyList(),
    val configUpdatedAt: Map<String, Long> = emptyMap(),
    val fingerprint: Map<String, String> = emptyMap(),
    val accessControlUpdatedAt: Long = 0,
    val strategyUpdatedAt: Map<String, Long> = emptyMap(),
    val activeStrategyUpdatedAt: Long = 0,
    val strategyTombstones: List<TombstonePayload> = emptyList(),
    val lastSyncAt: Long = 0,
    val lastMessage: String = "",
)

class WebdavLocalState(context: Context) {
    private val root = context.applicationContext.filesDir.resolve("webdav")
    private val file = File(root, "state.json")

    fun read(): LocalState {
        return locked { readUnlocked().withClientId() }
    }

    fun update(block: (LocalState) -> LocalState) {
        locked {
            writeUnlocked(block(readUnlocked()).withClientId())
        }
    }

    fun clientId(): String = read().clientId

    fun lastMessage(): String = read().lastMessage

    private fun LocalState.withClientId(): LocalState {
        return if (clientId.isNotBlank()) this else copy(clientId = createWebdavClientId())
    }

    private fun readUnlocked(): LocalState {
        if (!file.exists()) return LocalState()
        return try {
            webdavJson.decodeFromString(LocalState.serializer(), file.readText())
        } catch (_: Exception) {
            LocalState()
        }
    }

    private fun writeUnlocked(state: LocalState) {
        root.mkdirs()
        val tmp = File(root, "state.json.tmp")
        tmp.writeText(webdavJson.encodeToString(LocalState.serializer(), state))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun <T> locked(block: () -> T): T {
        root.mkdirs()
        return RandomAccessFile(File(root, "state.lock"), "rw").use { access ->
            access.channel.lock().use { block() }
        }
    }
}
