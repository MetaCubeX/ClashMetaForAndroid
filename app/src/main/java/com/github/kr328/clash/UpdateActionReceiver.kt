package com.github.kr328.clash

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.util.UpdateApkVerifier

class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        when (intent?.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId <= 0L) return
                val pendingId = app
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getLong(KEY_PENDING_DOWNLOAD_ID, -1L)
                if (pendingId <= 0L || pendingId != completedId) return
                handleDownloadedApk(app, completedId)
            }
        }
    }

    private fun handleDownloadedApk(app: Context, downloadId: Long) {
        val dm = app.getSystemService(DownloadManager::class.java) ?: return
        val query = DownloadManager.Query().setFilterById(downloadId)
        dm.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) return

            val uri = dm.getUriForDownloadedFile(downloadId) ?: return
            val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            if (!UpdateApkVerifier.isTrustedDownloadedApk(app, localUri)) {
                Log.w("Reject update install: downloaded APK signature/package mismatch")
                app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_PENDING_DOWNLOAD_ID)
                    .apply()
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !app.packageManager.canRequestPackageInstalls()
            ) {
                runCatching {
                    app.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${app.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }.onFailure {
                    Log.w("Open unknown-app-sources settings failed: ${it.message}")
                }
                return
            }

            runCatching {
                app.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )
                app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_PENDING_DOWNLOAD_ID)
                    .apply()
            }.onFailure {
                Log.w("Open APK installer failed: ${it.message}")
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "app_update"
        private const val KEY_PENDING_DOWNLOAD_ID = "pending_download_id"
    }
}
