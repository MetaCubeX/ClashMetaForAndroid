package com.github.kr328.clash.service.webdav

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
private data class SettingsFile(
    val url: String = "",
    val username: String = "",
    val passwordSealed: String = "",
    val remotePath: String = WEBDAV_DEFAULT_REMOTE_PATH,
    val autoSync: Boolean = false,
)

class WebdavSettingsStore(context: Context) {
    private val file = context.applicationContext.filesDir.resolve("webdav/settings.json")

    var url: String?
        get() = read().url.ifBlank { null }
        set(value) = update { it.copy(url = value?.trim().orEmpty()) }

    var username: String?
        get() = read().username.ifBlank { null }
        set(value) = update { it.copy(username = value?.trim().orEmpty()) }

    var password: String
        get() = read().password
        set(value) = update { it.copy(password = value) }

    var remotePath: String?
        get() = read().remotePath.ifBlank { null }
        set(value) = update { it.copy(remotePath = resolveWebdavRemoteFolder(value.orEmpty())) }

    var autoSync: Boolean
        get() = read().autoSync
        set(value) = update { it.copy(autoSync = value) }

    fun load(): StoredWebdavSettings = read()

    fun configured(): Boolean {
        val current = read()
        return current.url.isNotBlank() && current.username.isNotBlank() && current.password.isNotEmpty()
    }

    private fun update(block: (StoredWebdavSettings) -> StoredWebdavSettings) {
        write(block(read()))
    }

    private fun read(): StoredWebdavSettings {
        if (!file.exists()) return StoredWebdavSettings()
        return try {
            val stored = webdavJson.decodeFromString(SettingsFile.serializer(), file.readText())
            StoredWebdavSettings(
                url = stored.url.trim(),
                username = stored.username.trim(),
                password = WebdavCipher.open(stored.passwordSealed),
                remotePath = resolveWebdavRemoteFolder(stored.remotePath),
                autoSync = stored.autoSync,
            )
        } catch (_: Exception) {
            StoredWebdavSettings()
        }
    }

    private fun write(settings: StoredWebdavSettings) {
        file.parentFile?.mkdirs()
        val payload = SettingsFile(
            url = settings.url.trim(),
            username = settings.username.trim(),
            passwordSealed = WebdavCipher.seal(settings.password),
            remotePath = resolveWebdavRemoteFolder(settings.remotePath),
            autoSync = settings.autoSync,
        )
        val tmp = File(file.parentFile, "settings.json.tmp")
        tmp.writeText(webdavJson.encodeToString(SettingsFile.serializer(), payload))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

data class StoredWebdavSettings(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val remotePath: String = WEBDAV_DEFAULT_REMOTE_PATH,
    val autoSync: Boolean = false,
)
