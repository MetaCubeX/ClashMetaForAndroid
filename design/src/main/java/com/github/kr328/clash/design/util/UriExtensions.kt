package com.github.kr328.clash.design.util

import android.util.Log
import androidx.compose.ui.platform.UriHandler

private const val TAG = "UriExtensions"

/** 安全地打开链接，失败时记录日志并调用回调。 */
fun UriHandler.safeOpen(url: String, onError: (Throwable) -> Unit = {}) {
    try {
        openUri(url)
    } catch (t: Throwable) {
        Log.w(TAG, "Failed to open uri: $url", t)
        onError(t)
    }
}




