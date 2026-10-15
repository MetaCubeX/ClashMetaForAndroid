package com.github.kr328.clash.service.webdav

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
private data class SettingsFile(
    val url: String = WEBDAV_DEFAULT_URL,
    val lanUrl: String = WEBDAV_DEFAULT_LAN_URL,
    val username: String = WEBDAV_DEFAULT_USERNAME,
    val passwordSealed: String = "",
    val remotePath: String = WEBDAV_DEFAULT_REMOTE_PATH,
    val autoSync: Boolean = false,
)

class WebdavSettingsStore(context: Context) {
    private val file = context.applicationContext.filesDir.resolve("webdav/settings.json")

    var url: String?
        get() = read().url.ifBlank { null }
        set(value) = update { it.copy(url = value?.trim().orEmpty()) }

    var lanUrl: String?
        get() = read().lanUrl.ifBlank { null }
        set(value) = update { it.copy(lanUrl = value?.trim().orEmpty()) }

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
        return current.hasEndpoint() && current.username.isNotBlank() && current.password.isNotEmpty()
    }

    private fun update(block: (StoredWebdavSettings) -> StoredWebdavSettings) {
        write(block(read()))
    }

    private fun read(): StoredWebdavSettings {
        if (!file.exists()) return StoredWebdavSettings()
        return try {
            val stored = webdavJson.decodeFromString(SettingsFile.serializer(), file.readText())
            seedWebdavSettings(
                StoredWebdavSettings(
                    url = stored.url.trim(),
                    lanUrl = stored.lanUrl.trim(),
                    username = stored.username.trim(),
                    password = WebdavCipher.open(stored.passwordSealed),
                    remotePath = stored.remotePath.trim(),
                    autoSync = stored.autoSync,
                ),
            )
        } catch (_: Exception) {
            StoredWebdavSettings()
        }
    }

    private fun write(settings: StoredWebdavSettings) {
        file.parentFile?.mkdirs()
        val payload = SettingsFile(
            url = normalizeWebdavUrl(settings.url),
            lanUrl = normalizeWebdavLanUrl(settings.lanUrl),
            username = settings.username.trim().ifBlank { WEBDAV_DEFAULT_USERNAME },
            passwordSealed = WebdavCipher.seal(settings.password),
            remotePath = normalizeWebdavRemotePath(settings.remotePath),
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
    val url: String = WEBDAV_DEFAULT_URL,
    val lanUrl: String = WEBDAV_DEFAULT_LAN_URL,
    val username: String = WEBDAV_DEFAULT_USERNAME,
    val password: String = "",
    val remotePath: String = WEBDAV_DEFAULT_REMOTE_PATH,
    val autoSync: Boolean = false,
) {
    fun hasEndpoint(): Boolean = url.isNotBlank() || lanUrl.isNotBlank()
}

internal fun seedWebdavSettings(settings: StoredWebdavSettings): StoredWebdavSettings {
    val configured = settings.password.isNotEmpty() || settings.autoSync
    if (configured) {
        return settings.copy(
            url = settings.url.trim().trimEnd('/').let { url ->
                if (url == WEBDAV_LEGACY_MISREAD_URL) WEBDAV_DEFAULT_URL else url
            },
            lanUrl = normalizeWebdavLanUrl(settings.lanUrl),
            username = settings.username.trim(),
            remotePath = if (settings.remotePath.trim().trimEnd('/') == WEBDAV_LEGACY_REMOTE_PATH) {
                WEBDAV_DEFAULT_REMOTE_PATH
            } else {
                resolveWebdavRemoteFolder(settings.remotePath)
            },
        )
    }
    return settings.copy(
        url = normalizeWebdavUrl(settings.url),
        lanUrl = normalizeWebdavLanUrl(settings.lanUrl),
        username = settings.username.trim().ifBlank { WEBDAV_DEFAULT_USERNAME },
        remotePath = normalizeWebdavRemotePath(settings.remotePath),
    )
}
