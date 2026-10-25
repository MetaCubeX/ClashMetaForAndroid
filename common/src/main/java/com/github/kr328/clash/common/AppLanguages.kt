package com.github.kr328.clash.common

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.os.LocaleListCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.constants.Permissions
import java.util.Locale

/**
 * Tags follow `app/src/main/res/xml/locales_config.xml`, in the same order.
 * An empty tag follows the system, including an Android 13 per-app locale.
 */
object AppLanguages {
    const val PREFERENCE_NAME = "ui"
    const val KEY = "app_language"
    const val FOLLOW_SYSTEM = ""

    data class Option(val tag: String, val nativeName: String)

    val supported: List<Option> = listOf(
        Option("en", "English"),
        Option("ja-JP", "日本語"),
        Option("ko-KR", "한국어"),
        Option("ru", "Русский"),
        Option("vi", "Tiếng Việt"),
        Option("zh", "简体中文"),
        Option("zh-HK", "繁體中文（香港）"),
        Option("zh-TW", "繁體中文（台灣）"),
    )

    private val tags = supported.map { it.tag }.toSet()
    private var baseline: LocaleListCompat? = null

    fun canonicalize(raw: String?): String {
        val tag = raw?.trim().orEmpty()
        return if (tag in tags) tag else FOLLOW_SYSTEM
    }

    fun read(context: Context): String {
        return canonicalize(
            context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                .getString(KEY, FOLLOW_SYSTEM)
        )
    }

    fun write(context: Context, raw: String): String {
        val tag = canonicalize(raw)
        context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, tag)
            .commit()
        return tag
    }

    /** Call with the process base context before [wrap]. */
    fun rememberBaseline(base: Context) {
        if (baseline != null) return
        val config = base.resources.configuration
        val current = if (Build.VERSION.SDK_INT >= 24) {
            LocaleListCompat.wrap(config.locales)
        } else {
            @Suppress("DEPRECATION")
            val locale = config.locale
            if (locale == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.create(locale)
        }
        baseline = if (current.isEmpty) LocaleListCompat.getDefault() else current
    }

    fun wrap(context: Context): Context {
        val tag = read(context)
        if (tag.isEmpty()) return context
        val config = Configuration(context.resources.configuration)
        applyTag(config, tag)
        return context.createConfigurationContext(config)
    }

    fun apply(context: Context, raw: String?) {
        val tag = canonicalize(raw)
        val resources = context.applicationContext.resources
        val config = Configuration(resources.configuration)
        if (tag.isEmpty()) {
            if (baseline == null) return
            restoreBaseline(config)
        } else {
            applyTag(config, tag)
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun broadcast(context: Context, tag: String) {
        context.sendBroadcast(
            Intent(Intents.ACTION_APP_LANGUAGE_CHANGED)
                .putExtra(Intents.EXTRA_APP_LANGUAGE, canonicalize(tag))
                .setPackage(context.packageName),
            Permissions.RECEIVE_SELF_BROADCASTS,
        )
    }

    private fun applyTag(config: Configuration, tag: String) {
        val locale = Locale.forLanguageTag(tag)
        if (Build.VERSION.SDK_INT >= 24) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        config.setLayoutDirection(locale)
    }

    private fun restoreBaseline(config: Configuration) {
        val locales = baseline ?: LocaleListCompat.getDefault()
        val primary = if (locales.isEmpty) Locale.getDefault() else locales[0] ?: Locale.getDefault()
        if (Build.VERSION.SDK_INT >= 24) {
            config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        } else {
            @Suppress("DEPRECATION")
            config.locale = primary
        }
        config.setLayoutDirection(primary)
    }
}
