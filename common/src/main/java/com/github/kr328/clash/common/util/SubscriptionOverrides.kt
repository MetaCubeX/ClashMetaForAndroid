package com.github.kr328.clash.common.util

import android.content.Context
import org.json.JSONObject
import java.util.UUID

object SubscriptionOverrides {
    private const val PREFS = "subscription_overrides"
    private const val PREFIX_UA = "ua_"
    private const val PREFIX_UA_STRICT = "ua_strict_"

    fun getUserAgent(context: Context, uuid: UUID): String? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREFIX_UA + uuid.toString(), "")
            ?.trim()
            .orEmpty()
        return value.takeIf { it.isNotBlank() }
    }

    fun setUserAgent(context: Context, uuid: UUID, userAgent: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = PREFIX_UA + uuid.toString()
        val value = userAgent?.trim().orEmpty()
        prefs.edit().apply {
            if (value.isBlank()) remove(key) else putString(key, value)
        }.apply()
    }

    fun isStrictUserAgent(context: Context, uuid: UUID): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREFIX_UA_STRICT + uuid.toString(), false)
    }

    fun setStrictUserAgent(context: Context, uuid: UUID, strict: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREFIX_UA_STRICT + uuid.toString(), strict)
            .apply()
    }
}

object SubscriptionRequestHeaders {
    fun defaultUserAgent(context: Context): String {
        val ver = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (_: Exception) {
            "0"
        }
        return "ClashFest/$ver"
    }

    fun build(context: Context, userAgentOverride: String? = null): Map<String, String> {
        val ua = userAgentOverride?.trim().takeUnless { it.isNullOrBlank() } ?: defaultUserAgent(context)
        return LinkedHashMap<String, String>().apply {
            put("User-Agent", ua)
            putAll(SubscriptionDeviceHeaders.headerMap(context))
        }
    }

    fun toJson(context: Context, userAgentOverride: String? = null): String =
        JSONObject(build(context, userAgentOverride)).toString()

    /**
     * Native fetch already sets its own stable User-Agent in Go layer.
     * Inject UA only when user explicitly overrides it for a subscription.
     */
    fun toNativeFetchJson(context: Context, userAgentOverride: String? = null): String {
        val map = LinkedHashMap<String, String>().apply {
            putAll(SubscriptionDeviceHeaders.headerMap(context))
            userAgentOverride?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { put("User-Agent", it) }
        }
        return JSONObject(map as Map<*, *>).toString()
    }
}
