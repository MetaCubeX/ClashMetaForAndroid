package com.github.kr328.clash.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.MainActivity
import com.github.kr328.clash.UpdateActionReceiver
import com.github.kr328.clash.UpdateCheckReceiver
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.service.R as ServiceR

object AppUpdateChecker {
    private const val PREFS_NAME = "app_update"
    private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
    private const val PERIODIC_REQUEST_CODE = 1101
    private const val OPEN_APP_REQUEST_CODE = 1102
    private const val ACTION_DOWNLOAD_REQUEST_CODE = 1104
    private const val ACTION_OPEN_RELEASE_REQUEST_CODE = 1105
    private const val INTERVAL_MS = 24L * 60L * 60L * 1000L // 24h
    private const val FIRST_DELAY_MS = 30L * 60L * 1000L // 30 min after app starts
    private const val CHANNEL_ID = "app_update_channel"
    private const val NOTIFICATION_ID = 1103

    fun schedulePeriodic(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = periodicPendingIntent(app)

        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + FIRST_DELAY_MS,
            INTERVAL_MS,
            pending,
        )
    }

    suspend fun checkAndNotify(context: Context) {
        val app = context.applicationContext
        val latest = GitHubReleaseUpdate.fetchLatest() ?: return
        if (latest.tagName.isBlank()) return

        val current = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")
        val hasUpdate = GitHubReleaseUpdate.compareVersions(latest.tagName, current) > 0
        if (!hasUpdate) return

        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_NOTIFIED_TAG, null) == latest.tagName) return

        notifyUpdateAvailable(app, latest)
        prefs.edit().putString(KEY_LAST_NOTIFIED_TAG, latest.tagName).apply()
    }

    /**
     * Immediately shows update notification with actions.
     * Used by manual "Check for updates" flow.
     */
    fun showUpdateNotification(context: Context, release: GitHubReleaseUpdate.Info) {
        notifyUpdateAvailable(context.applicationContext, release)
    }

    private fun periodicPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, UpdateCheckReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            PERIODIC_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notifyUpdateAvailable(context: Context, release: GitHubReleaseUpdate.Info) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(DesignR.string.update_notification_channel_name))
                .setDescription(context.getString(DesignR.string.update_notification_channel_description))
                .build(),
        )

        val target = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val openPendingIntent = PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openReleasePendingIntent = PendingIntent.getBroadcast(
            context,
            ACTION_OPEN_RELEASE_REQUEST_CODE,
            Intent(context, UpdateActionReceiver::class.java).apply {
                action = UpdateActionReceiver.ACTION_OPEN_RELEASE_PAGE
                putExtra(UpdateActionReceiver.EXTRA_RELEASE_URL, release.htmlUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val downloadAndInstallPendingIntent = if (!release.apkUrl.isNullOrBlank()) {
            PendingIntent.getBroadcast(
                context,
                ACTION_DOWNLOAD_REQUEST_CODE,
                Intent(context, UpdateActionReceiver::class.java).apply {
                    action = UpdateActionReceiver.ACTION_DOWNLOAD_AND_INSTALL
                    putExtra(UpdateActionReceiver.EXTRA_TAG, release.tagName)
                    putExtra(UpdateActionReceiver.EXTRA_APK_URL, release.apkUrl)
                    putExtra(UpdateActionReceiver.EXTRA_APK_NAME, release.apkName)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            null
        }

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(ServiceR.drawable.ic_logo_service)
            .setContentTitle(context.getString(DesignR.string.update_notification_title))
            .setContentText(context.getString(DesignR.string.update_notification_text, release.tagName))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent)
            .addAction(
                0,
                context.getString(DesignR.string.about_open_release),
                openReleasePendingIntent,
            )

        if (downloadAndInstallPendingIntent != null) {
            notificationBuilder.addAction(
                0,
                context.getString(DesignR.string.about_download_install),
                downloadAndInstallPendingIntent,
            )
        }

        val notification = notificationBuilder.build()

        nm.notify(NOTIFICATION_ID, notification)
    }

}
