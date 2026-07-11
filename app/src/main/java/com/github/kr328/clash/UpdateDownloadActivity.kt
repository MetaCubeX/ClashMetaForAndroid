package com.github.kr328.clash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.util.AppUpdateChecker
import com.github.kr328.clash.util.GitHubReleaseUpdate
import com.github.kr328.clash.design.R as DesignR

class UpdateDownloadActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val release = AppUpdateChecker.peekCachedRelease(this)
        val downloadId = runCatching {
            release?.let { GitHubReleaseUpdate.enqueueApkDownload(this, it) } ?: -1L
        }.onFailure {
            Log.w("Update action enqueue failed: ${it.message}")
        }.getOrDefault(-1L)

        if (downloadId > 0L) {
            AppUpdateChecker.dismissUpdateNotification(this)
            Toast.makeText(this, DesignR.string.about_download_started, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, DesignR.string.about_download_failed, Toast.LENGTH_SHORT).show()
        }

        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_SYNC_PENDING_DOWNLOAD)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    companion object {
        const val ACTION_SYNC_PENDING_DOWNLOAD =
            "com.github.kr328.clash.action.SYNC_PENDING_UPDATE_DOWNLOAD"
    }
}
