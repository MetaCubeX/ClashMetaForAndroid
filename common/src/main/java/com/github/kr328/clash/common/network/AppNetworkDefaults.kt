package com.github.kr328.clash.common.network

import android.content.Context

/** Shared timeouts and UA defaults for app-initiated HTTP (metadata, GitHub API, helpers). */
object AppNetworkDefaults {
    const val CONNECT_TIMEOUT_MS: Int = 15_000
    const val READ_TIMEOUT_MS: Int = 15_000

    fun userAgentClashFest(context: Context): String {
        val ver = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (_: Exception) {
            "0"
        }
        return "ClashFest/$ver"
    }
}
