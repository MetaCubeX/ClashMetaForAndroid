package com.github.kr328.clash.agent.settings

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.github.kr328.clash.agent.authorization.AgentAuthorizationMode
import com.github.kr328.clash.agent.model.AgentApiFormat
import com.github.kr328.clash.agent.model.AgentProviderSettings
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AgentSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): AgentProviderSettings = AgentProviderSettings(
        baseUrl = preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty(),
        model = preferences.getString(KEY_MODEL, "").orEmpty(),
        apiKey = decrypt(preferences.getString(KEY_API_KEY, "").orEmpty()),
        apiFormat = runCatching {
            AgentApiFormat.valueOf(
                preferences.getString(KEY_API_FORMAT, AgentApiFormat.CHAT_COMPLETIONS.name).orEmpty()
            )
        }.getOrDefault(AgentApiFormat.CHAT_COMPLETIONS),
        authorizationMode = runCatching {
            AgentAuthorizationMode.valueOf(
                preferences.getString(KEY_AUTHORIZATION, AgentAuthorizationMode.BALANCED.name).orEmpty()
            )
        }.getOrDefault(AgentAuthorizationMode.BALANCED),
        maxToolRounds = preferences.getInt(KEY_MAX_ROUNDS, 12).coerceIn(4, 24),
    )

    fun save(settings: AgentProviderSettings) {
        preferences.edit()
            .putString(KEY_BASE_URL, normalizeBaseUrl(settings.baseUrl))
            .putString(KEY_MODEL, settings.model.trim())
            .putString(KEY_API_KEY, encrypt(settings.apiKey.trim()))
            .putString(KEY_API_FORMAT, settings.apiFormat.name)
            .putString(KEY_AUTHORIZATION, settings.authorizationMode.name)
            .putInt(KEY_MAX_ROUNDS, settings.maxToolRounds.coerceIn(4, 24))
            .apply()
    }

    private fun normalizeBaseUrl(value: String): String = value.trim().trimEnd('/').ifBlank {
        DEFAULT_BASE_URL
    }

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "plain:$value"

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            "gcm:${Base64.encodeToString(cipher.iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
        } catch (error: Throwable) {
            throw IllegalStateException("无法使用 Android 安全存储保存 API Key", error)
        }
    }

    private fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        if (value.startsWith("plain:")) return value.removePrefix("plain:")
        if (!value.startsWith("gcm:") || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return ""

        return runCatching {
            val parts = value.split(':', limit = 3)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        private const val PREFERENCES = "agent_settings"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_FORMAT = "api_format"
        private const val KEY_MODEL = "model"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_AUTHORIZATION = "authorization"
        private const val KEY_MAX_ROUNDS = "max_rounds"
        private const val KEY_ALIAS = "cmfa_agent_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    }
}
