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
        set(value) = update(passwordTouched = true) { it.copy(password = value) }

    /** 密文存在但读不出来时，界面要提示重新输入，不能当成未设置。 */
    val passwordUnreadable: Boolean
        get() = read().passwordUnreadable

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

    private fun update(
        passwordTouched: Boolean = false,
        block: (StoredWebdavSettings) -> StoredWebdavSettings,
    ) {
        val current = read()
        write(block(current), current, passwordTouched)
    }

    private fun read(): StoredWebdavSettings {
        if (!file.exists()) return StoredWebdavSettings()
        return try {
            val stored = webdavJson.decodeFromString(SettingsFile.serializer(), file.readText())
            val plain = WebdavCipher.open(stored.passwordSealed)
            seedWebdavSettings(
                StoredWebdavSettings(
                    url = stored.url.trim(),
                    lanUrl = stored.lanUrl.trim(),
                    username = stored.username.trim(),
                    password = plain.orEmpty(),
                    passwordSealed = stored.passwordSealed,
                    passwordUnreadable = plain == null,
                    remotePath = stored.remotePath.trim(),
                    autoSync = stored.autoSync,
                ),
            )
        } catch (_: Exception) {
            StoredWebdavSettings()
        }
    }

    private fun write(
        settings: StoredWebdavSettings,
        previous: StoredWebdavSettings,
        passwordTouched: Boolean,
    ) {
        file.parentFile?.mkdirs()
        val payload = SettingsFile(
            url = normalizeWebdavUrl(settings.url),
            lanUrl = normalizeWebdavLanUrl(settings.lanUrl),
            username = settings.username.trim().ifBlank { WEBDAV_DEFAULT_USERNAME },
            passwordSealed = sealedForWrite(settings, previous, passwordTouched),
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

/**
 * 密码没被显式改写时沿用原密文。解密失败也保留，避免一次无关写入把它抹掉。
 */
internal fun sealedForWrite(
    settings: StoredWebdavSettings,
    previous: StoredWebdavSettings,
    passwordTouched: Boolean,
): String {
    if (!passwordTouched && settings.password == previous.password) {
        return previous.passwordSealed
    }
    return WebdavCipher.seal(settings.password)
}

data class StoredWebdavSettings(
    val url: String = WEBDAV_DEFAULT_URL,
    val lanUrl: String = WEBDAV_DEFAULT_LAN_URL,
    val username: String = WEBDAV_DEFAULT_USERNAME,
    val password: String = "",
    val passwordSealed: String = "",
    val passwordUnreadable: Boolean = false,
    val remotePath: String = WEBDAV_DEFAULT_REMOTE_PATH,
    val autoSync: Boolean = false,
) {
    fun hasEndpoint(): Boolean = url.isNotBlank() || lanUrl.isNotBlank()
}

internal fun sameWebdavConnection(left: StoredWebdavSettings, right: StoredWebdavSettings): Boolean {
    return left.url.trim().trimEnd('/') == right.url.trim().trimEnd('/') &&
        normalizeWebdavLanUrl(left.lanUrl) == normalizeWebdavLanUrl(right.lanUrl) &&
        left.username.trim() == right.username.trim() &&
        left.password == right.password &&
        normalizeWebdavRemotePath(left.remotePath) == normalizeWebdavRemotePath(right.remotePath)
}

internal fun seedWebdavSettings(settings: StoredWebdavSettings): StoredWebdavSettings {
    // 密文读不出来时仍算已配置，否则地址、用户名和远端路径会被一起重置成默认值。
    val configured = settings.password.isNotEmpty() ||
        settings.passwordSealed.isNotEmpty() ||
        settings.autoSync
    if (configured) {
        return settings.copy(
            url = settings.url.trim().trimEnd('/'),
            lanUrl = normalizeWebdavLanUrl(settings.lanUrl),
            username = settings.username.trim(),
            remotePath = resolveWebdavRemoteFolder(settings.remotePath),
        )
    }
    return settings.copy(
        url = normalizeWebdavUrl(settings.url),
        lanUrl = normalizeWebdavLanUrl(settings.lanUrl),
        username = settings.username.trim().ifBlank { WEBDAV_DEFAULT_USERNAME },
        remotePath = normalizeWebdavRemotePath(settings.remotePath),
    )
}
