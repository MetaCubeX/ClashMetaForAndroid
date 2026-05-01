package com.github.kr328.clash

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.util.GitHubReleaseUpdate
import java.security.MessageDigest

class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        when (intent?.action) {
            ACTION_DOWNLOAD_AND_INSTALL -> {
                val tag = intent.getStringExtra(EXTRA_TAG).orEmpty()
                val url = intent.getStringExtra(EXTRA_APK_URL).orEmpty()
                val name = intent.getStringExtra(EXTRA_APK_NAME)
                if (url.isBlank()) return
                runCatching {
                    GitHubReleaseUpdate.enqueueApkDownload(
                        context = app,
                        tagName = tag.ifBlank { "update" },
                        apkUrl = url,
                        apkName = name,
                    )
                }.onFailure {
                    Log.w("Update action enqueue failed: ${it.message}")
                }
            }

            ACTION_OPEN_RELEASE_PAGE -> {
                val url = intent.getStringExtra(EXTRA_RELEASE_URL).orEmpty()
                if (url.isBlank()) return
                runCatching {
                    app.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                }.onFailure {
                    Log.w("Update action open release failed: ${it.message}")
                }
            }

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
            if (!isTrustedDownloadedApk(app, localUri)) {
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

    private fun isTrustedDownloadedApk(app: Context, localUri: String?): Boolean {
        if (localUri.isNullOrBlank()) return false
        val apkPath = Uri.parse(localUri).path ?: return false
        val pm = app.packageManager

        val archiveInfo = getArchivePackageInfo(pm, apkPath) ?: return false
        if (archiveInfo.packageName != app.packageName) return false

        val installedInfo = getInstalledPackageInfo(pm, app.packageName) ?: return false
        val installed = signaturesDigest(installedInfo)
        val archive = signaturesDigest(archiveInfo)
        return installed.isNotEmpty() && installed == archive
    }

    @Suppress("DEPRECATION")
    private fun getArchivePackageInfo(pm: PackageManager, apkPath: String): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                apkPath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES)
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackageInfo(pm: PackageManager, packageName: String): PackageInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                )
            } else {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun signaturesDigest(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            val values = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            values?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }

        return signatures.mapTo(linkedSetOf()) {
            val digest = MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
            digest.joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
        }
    }

    companion object {
        private const val PREFS_NAME = "app_update"
        private const val KEY_PENDING_DOWNLOAD_ID = "pending_download_id"
        const val ACTION_DOWNLOAD_AND_INSTALL = "com.github.kr328.clash.action.UPDATE_DOWNLOAD_AND_INSTALL"
        const val ACTION_OPEN_RELEASE_PAGE = "com.github.kr328.clash.action.UPDATE_OPEN_RELEASE_PAGE"
        const val EXTRA_TAG = "extra_tag"
        const val EXTRA_APK_URL = "extra_apk_url"
        const val EXTRA_APK_NAME = "extra_apk_name"
        const val EXTRA_RELEASE_URL = "extra_release_url"
    }
}
