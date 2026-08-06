package com.github.kr328.clash.service.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.log.Log
import org.json.JSONArray
import org.json.JSONObject

/** Reads Android's system-maintained process exit history without exposing configs or secrets. */
object ProcessExitDiagnostics {
    fun read(context: Context): String {
        if (Build.VERSION.SDK_INT < 30) {
            return JSONObject()
                .put("available", false)
                .put("reason", "requires_android_11")
                .toString()
        }

        return readApi30(context)
    }

    fun logLatest(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return

        runCatching {
            val manager = context.getSystemService<ActivityManager>() ?: return
            val background = "${context.packageName}:background"
            val latest = manager.getHistoricalProcessExitReasons(context.packageName, 0, 10)
                .firstOrNull { it.processName == background }
                ?: return
            Log.w(
                "Previous background process exit: ${reasonName(latest.reason)}, " +
                    "timestamp=${latest.timestamp}, status=${latest.status}, " +
                    "description=${latest.description?.take(240).orEmpty()}"
            )
        }.onFailure { Log.w("Unable to read previous process exit reason", it) }
    }

    @RequiresApi(30)
    private fun readApi30(context: Context): String {
        val manager = context.getSystemService<ActivityManager>()
            ?: return JSONObject().put("available", false).put("reason", "activity_manager_unavailable").toString()
        val background = "${context.packageName}:background"
        val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 20)
            .filter { it.processName == background }
            .take(10)

        val rows = JSONArray()
        exits.forEach { exit ->
            rows.put(
                JSONObject()
                    .put("timestamp", exit.timestamp)
                    .put("reason", reasonName(exit.reason))
                    .put("reason_code", exit.reason)
                    .put("status", exit.status)
                    .put("importance", exit.importance)
                    .put("pss_kb", exit.pss)
                    .put("rss_kb", exit.rss)
                    .put("description", exit.description?.take(500).orEmpty())
            )
        }

        return JSONObject()
            .put("available", true)
            .put("process", background)
            .put("exits", rows)
            .toString()
    }

    private fun reasonName(reason: Int): String = when (reason) {
        0 -> "unknown"
        1 -> "exit_self"
        2 -> "signaled"
        3 -> "low_memory"
        4 -> "crash"
        5 -> "native_crash"
        6 -> "anr"
        7 -> "initialization_failure"
        8 -> "permission_change"
        9 -> "excessive_resource_usage"
        10 -> "user_requested"
        11 -> "user_stopped"
        12 -> "dependency_died"
        13 -> "other"
        14 -> "freezer"
        15 -> "package_state_change"
        16 -> "package_updated"
        else -> "unknown_$reason"
    }
}
