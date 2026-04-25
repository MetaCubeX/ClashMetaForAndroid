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
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.service.R as ServiceR

object AppUpdateChecker {
    private const val PREFS_NAME = "app_update"
    private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
    private const val PERIODIC_REQUEST_CODE = 1101
    private const val OPEN_APP_REQUEST_CODE = 1102
    private const val INTERVAL_MS = 24L * 60L * 60L * 1000L // 24h
    private const val FIRST_DELAY_MS = 30L * 60L * 1000L // 30 min after app starts
    private const val CHANNEL_ID = "app_update_channel"
    private const val NOTIFICATION_ID = 1103

    data class ReleaseInfo(
        val tagName: String,
        val htmlUrl: String,
    )

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
        val latest = fetchLatestReleaseInfo() ?: return
        if (latest.tagName.isBlank()) return

        val current = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")
        val hasUpdate = compareVersions(latest.tagName, current) > 0
        if (!hasUpdate) return

        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_NOTIFIED_TAG, null) == latest.tagName) return

        notifyUpdateAvailable(app, latest)
        prefs.edit().putString(KEY_LAST_NOTIFIED_TAG, latest.tagName).apply()
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

    private fun notifyUpdateAvailable(context: Context, release: ReleaseInfo) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(DesignR.string.update_notification_channel_name))
                .setDescription(context.getString(DesignR.string.update_notification_channel_description))
                .build(),
        )

        val target = if (release.htmlUrl.isNotBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
        } else {
            Intent(context, MainActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val openPendingIntent = PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(ServiceR.drawable.ic_logo_service)
            .setContentTitle(context.getString(DesignR.string.update_notification_title))
            .setContentText(context.getString(DesignR.string.update_notification_text, release.tagName))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun fetchLatestReleaseInfo(): ReleaseInfo? {
        return runCatching {
            val endpoint = "https://api.github.com/repos/Nemu-x/ClashFest/releases/latest"
            val text = URL(endpoint).openStream().bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            ReleaseInfo(
                tagName = json.optString("tag_name"),
                htmlUrl = json.optString("html_url"),
            )
        }.getOrNull()
    }

    private fun compareVersions(left: String, right: String): Int {
        fun semverTriplet(v: String): IntArray? {
            val m = Regex("""(\d+)\.(\d+)\.(\d+)""").find(v) ?: return null
            return intArrayOf(
                m.groupValues[1].toIntOrNull() ?: 0,
                m.groupValues[2].toIntOrNull() ?: 0,
                m.groupValues[3].toIntOrNull() ?: 0,
            )
        }

        val a = semverTriplet(left)
        val b = semverTriplet(right)
        if (a != null && b != null) {
            for (i in 0..2) {
                if (a[i] != b[i]) return a[i].compareTo(b[i])
            }
            return 0
        }

        return left.lowercase(Locale.ROOT).compareTo(right.lowercase(Locale.ROOT))
    }
}
