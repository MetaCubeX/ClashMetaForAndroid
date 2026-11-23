package com.github.kr328.clash.service.webdav

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

@Serializable
data class LocalState(
    val clientId: String = "",
    val tombstones: List<TombstonePayload> = emptyList(),
    val configUpdatedAt: Map<String, Long> = emptyMap(),
    val fingerprint: Map<String, String> = emptyMap(),
    val profileUsage: Map<String, String> = emptyMap(),
    val accessControlUpdatedAt: Long = 0,
    val strategyUpdatedAt: Map<String, Long> = emptyMap(),
    val activeStrategyUpdatedAt: Long = 0,
    val strategyTombstones: List<TombstonePayload> = emptyList(),
    val lastSyncAt: Long = 0,
    val lastPullAt: Long = 0,
    val lastMessage: String = "",
    val contentHash: Map<String, String> = emptyMap(),
    val profileSyncRev: Map<String, Long> = emptyMap(),
    val profileWriterId: Map<String, String> = emptyMap(),
    val remoteEtags: Map<String, String> = emptyMap(),
    val pushedFingerprint: Map<String, String> = emptyMap(),
    val profileUsageUpdatedAt: Map<String, Long> = emptyMap(),
    val profileUsageWriter: Map<String, String> = emptyMap(),
    val strategySyncRev: Map<String, Long> = emptyMap(),
    val strategyWriterId: Map<String, String> = emptyMap(),
    val accessSyncRev: Long = 0,
    val accessWriterId: String = "",
    val activeStrategySyncRev: Long = 0,
    val activeStrategyWriterId: String = "",
    val remoteProfiles: Map<String, ProfilePayload> = emptyMap(),
    val managedProfiles: Set<String> = emptySet(),
    val wireUuidByLocalId: Map<String, String> = emptyMap(),
    val previousExport: Map<String, ProfilePayload> = emptyMap(),
    val pendingPushRevision: Long = 0,
    val completedPushRevision: Long = 0,
)

class WebdavLocalState(context: Context) {
    private val app = context.applicationContext
    private val root = context.applicationContext.filesDir.resolve("webdav")
    private val settings = WebdavSettingsStore(app).load()
    private val scope = webdavStateScope(settings)
    private val directory = File(root, "scopes/$scope")
    private val file = File(directory, "state.json")

    fun ensureScopeCurrent() {
        if (settings != WebdavSettingsStore(app).load()) {
            throw WebdavException("scope_changed", detail = "WebDAV settings changed; retry with the current account and folder")
        }
    }

    fun read(): LocalState {
        return locked {
            val raw = readUnlocked()
            if (raw.clientId.isNotBlank()) return@locked raw
            val next = raw.withClientId()
            writeUnlocked(next)
            next
        }
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
        } catch (error: Exception) {
            throw IllegalStateException("Cannot read WebDAV state; the existing file has been preserved", error)
        }
    }

    private fun writeUnlocked(state: LocalState) {
        ensureScopeCurrent()
        directory.mkdirs()
        val tmp = File(directory, "state.json.tmp")
        tmp.writeText(webdavJson.encodeToString(LocalState.serializer(), state))
        if (!tmp.renameTo(file)) throw java.io.IOException("Cannot commit WebDAV state")
    }

    private fun <T> locked(block: () -> T): T {
        ensureScopeCurrent()
        directory.mkdirs()
        return synchronized(processLock) {
            RandomAccessFile(File(directory, "state.lock"), "rw").use { access ->
                access.channel.lock().use { block() }
            }
        }
    }

    companion object {
        private val processLock = Any()
    }
}

internal fun webdavStateScope(settings: StoredWebdavSettings): String {
    // Route selection must not change the identity of a configured public/LAN pair.
    val server = settings.url.trim().trimEnd('/').ifBlank { settings.lanUrl.trim().trimEnd('/') }
    val identity = listOf(server, settings.username.trim(), normalizeWebdavRemotePath(settings.remotePath)).joinToString("\u0000")
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
