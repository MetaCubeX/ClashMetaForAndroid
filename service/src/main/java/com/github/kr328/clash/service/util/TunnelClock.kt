package com.github.kr328.clash.service.util

import android.content.Context

/**
 * Wall-clock instant when this process started the tunnel.
 * The UI reads the same file; a missing file means the start was not recorded.
 */
object TunnelClock {
    private const val FILE_NAME = "tunnel_started_at"

    fun markStarted(context: Context) {
        val file = context.filesDir.resolve(FILE_NAME)
        file.writeText(System.currentTimeMillis().toString())
    }

    fun clear(context: Context) {
        context.filesDir.resolve(FILE_NAME).delete()
    }

    fun startedAt(context: Context): Long? {
        val file = context.filesDir.resolve(FILE_NAME)
        if (!file.isFile) return null
        return file.readText().trim().toLongOrNull()
    }
}
