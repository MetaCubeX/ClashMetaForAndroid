package com.github.kr328.clash.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.MainActivity
import com.github.kr328.clash.UpdateCheckReceiver
import com.github.kr328.clash.UpdateDownloadActivity
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.service.R as ServiceR

object AppUpdateChecker {
    private const val PREFS_NAME = "app_update"
    private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
    // Latest release snapshot — populated every time checkAndNotify sees an update,
    // even if we have already notified for that tag. Used by the in-UI badge so the
    // tap-to-install dialog can render release notes without re-fetching from GitHub.
    private const val KEY_LATEST_TAG = "latest_tag"
    private const val KEY_LATEST_BODY = "latest_body"
    private const val KEY_LATEST_HTML_URL = "latest_html_url"
    private const val KEY_LATEST_APK_URL = "latest_apk_url"
    private const val KEY_LATEST_APK_NAME = "latest_apk_name"
    private const val KEY_LAST_CHECK_AT = "last_check_at"
    private const val PERIODIC_REQUEST_CODE = 1101
    private const val OPEN_APP_REQUEST_CODE = 1102
    private const val ACTION_DOWNLOAD_REQUEST_CODE = 1104
    private const val ACTION_OPEN_RELEASE_REQUEST_CODE = 1105
    private const val INTERVAL_MS = 24L * 60L * 60L * 1000L // 24h
    private const val FIRST_DELAY_MS = 30L * 60L * 1000L // 30 min after app starts
    private const val OPPORTUNISTIC_THROTTLE_MS = 6L * 60L * 60L * 1000L // 6h
    private const val CHANNEL_ID = "app_update_channel"
    const val NOTIFICATION_ID = 1103

    fun dismissUpdateNotification(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    /** Clears any stored "we notified about tag X" so a future identical version no longer
     *  suppresses notifications, and dismisses the visible banner. Call after install completes
     *  or when MainActivity confirms current version >= last-notified tag. */
    fun resetIfUpdated(context: Context, currentVersion: String) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTag = prefs.getString(KEY_LAST_NOTIFIED_TAG, null)
        val cachedTag = prefs.getString(KEY_LATEST_TAG, null)
        if (lastTag != null && GitHubReleaseUpdate.compareVersions(currentVersion, lastTag) >= 0) {
            prefs.edit().remove(KEY_LAST_NOTIFIED_TAG).apply()
            dismissUpdateNotification(context)
        }
        // Also clear the in-UI badge cache once we are running the version it was advertising.
        if (cachedTag != null && GitHubReleaseUpdate.compareVersions(currentVersion, cachedTag) >= 0) {
            prefs.edit()
                .remove(KEY_LATEST_TAG)
                .remove(KEY_LATEST_BODY)
                .remove(KEY_LATEST_HTML_URL)
                .remove(KEY_LATEST_APK_URL)
                .remove(KEY_LATEST_APK_NAME)
                .apply()
        }
    }

    /**
     * Cheap, synchronous check used by [MainActivity.onResume] to toggle the
     * in-header update badge. Reads SharedPreferences only — no network.
     */
    fun isUpdateAvailable(context: Context): Boolean {
        return peekCachedRelease(context) != null
    }

    /**
     * Returns the most recently observed release info that is still newer than the
     * installed version, or null. The tap-to-install dialog reads from here so it
     * can render release notes instantly. Updated by [checkAndNotify].
     */
    fun peekCachedRelease(context: Context): GitHubReleaseUpdate.Info? {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tag = prefs.getString(KEY_LATEST_TAG, null)?.takeIf { it.isNotBlank() } ?: return null
        val current = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")
        if (GitHubReleaseUpdate.compareVersions(tag, current) <= 0) return null
        return GitHubReleaseUpdate.Info(
            tagName = tag,
            body = prefs.getString(KEY_LATEST_BODY, "").orEmpty(),
            htmlUrl = prefs.getString(KEY_LATEST_HTML_URL, "").orEmpty(),
            apkUrl = prefs.getString(KEY_LATEST_APK_URL, null)?.takeIf { it.isNotBlank() },
            apkName = prefs.getString(KEY_LATEST_APK_NAME, null)?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Run [checkAndNotify] at most once every [OPPORTUNISTIC_THROTTLE_MS]. Used from
     * MainActivity.onResume so the badge picks up new releases that landed between
     * AlarmManager ticks, without burning battery on every screen entry.
     */
    suspend fun maybeOpportunisticCheck(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAt = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        if (System.currentTimeMillis() - lastAt < OPPORTUNISTIC_THROTTLE_MS) return
        checkAndNotify(context)
    }

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
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val latest = GitHubReleaseUpdate.fetchLatest() ?: run {
            // Still record that we tried so opportunistic throttle doesn't keep retrying.
            prefs.edit().putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis()).apply()
            return
        }
        prefs.edit().putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis()).apply()
        if (latest.tagName.isBlank()) return

        val current = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")
        val hasUpdate = GitHubReleaseUpdate.compareVersions(latest.tagName, current) > 0
        if (!hasUpdate) return

        // Cache full release info every time we see an update (even if Android notification
        // was already shown for this tag). The in-UI badge / dialog rely on this snapshot.
        cacheRelease(app, latest)

        if (prefs.getString(KEY_LAST_NOTIFIED_TAG, null) == latest.tagName) return

        notifyUpdateAvailable(app, latest)
        prefs.edit().putString(KEY_LAST_NOTIFIED_TAG, latest.tagName).apply()
    }

    /**
     * Persist a release snapshot so [peekCachedRelease] / [isUpdateAvailable] (the Home-header
     * badge and the in-app update dialog) reflect it. Shared by the background check and the
     * manual "Check now" so both surfaces stay consistent.
     */
    fun cacheRelease(context: Context, release: GitHubReleaseUpdate.Info) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LATEST_TAG, release.tagName)
            .putString(KEY_LATEST_BODY, release.body)
            .putString(KEY_LATEST_HTML_URL, release.htmlUrl)
            .putString(KEY_LATEST_APK_URL, release.apkUrl.orEmpty())
            .putString(KEY_LATEST_APK_NAME, release.apkName.orEmpty())
            .apply()
    }

    /**
     * Immediately shows update notification with actions. Used only by the background check —
     * the manual flow shows the in-app dialog instead.
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

        val openReleasePendingIntent = release.htmlUrl
            .takeIf(UpdateApkVerifier::isTrustedReleasePageUrl)
            ?.let { releaseUrl ->
                PendingIntent.getActivity(
                    context,
                    ACTION_OPEN_RELEASE_REQUEST_CODE,
                    Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        // The notification launches a non-exported activity directly. This avoids Android 12+
        // notification trampolines and keeps cached release data out of exported MainActivity.
        val downloadAndInstallPendingIntent = if (!release.apkUrl.isNullOrBlank()) {
            PendingIntent.getActivity(
                context,
                ACTION_DOWNLOAD_REQUEST_CODE,
                Intent(context, UpdateDownloadActivity::class.java),
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

        if (openReleasePendingIntent != null) {
            notificationBuilder.addAction(
                0,
                context.getString(DesignR.string.about_open_release),
                openReleasePendingIntent,
            )
        }

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
