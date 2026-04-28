package com.github.kr328.clash.common.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Stable device identifiers sent with subscription HTTP requests (panel / Pasar-style HWID contract).
 * Values are non-secret fingerprints; never log raw headers at verbose levels in production.
 */
object SubscriptionDeviceHeaders {

    const val HEADER_HWID = "x-hwid"
    const val HEADER_DEVICE_OS = "x-device-os"
    const val HEADER_VER_OS = "x-ver-os"
    const val HEADER_DEVICE_MODEL = "x-device-model"
    const val HEADER_APP_VERSION = "x-app-version"

    fun headerMap(context: Context): Map<String, String> {
        val pm = context.packageManager
        val pkg = context.packageName
        val ver = try {
            pm.getPackageInfo(pkg, 0).versionName ?: "0"
        } catch (_: Exception) {
            "0"
        }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
        val hwidRaw = "cf|$pkg|$androidId"
        val hwid = sha256Hex(hwidRaw)
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim().replace("\\s+".toRegex(), " ")
        return mapOf(
            HEADER_HWID to hwid,
            HEADER_DEVICE_OS to "Android",
            HEADER_VER_OS to (Build.VERSION.RELEASE ?: ""),
            HEADER_DEVICE_MODEL to model,
            HEADER_APP_VERSION to ver,
        )
    }

    fun toJson(context: Context): String = JSONObject(headerMap(context)).toString()

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
    }
}
