package com.github.kr328.clash.service.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.service.TunService

/**
 * A dead-man timer for OEM process kills. A healthy service continuously moves the alarm
 * forward, so it never fires or wakes the device. If the process disappears abruptly, the
 * last system-owned PendingIntent remains and recreates the foreground VPN service.
 */
object VpnServiceRecovery {
    private const val REQUEST_CODE = 0x564E
    private const val ACTION_RECOVER = "com.github.kr328.clash.action.RECOVER_VPN"
    private const val INTERACTIVE_HEARTBEAT_MS = 20_000L
    private const val IDLE_HEARTBEAT_MS = 120_000L
    private const val INTERACTIVE_TIMEOUT_MS = 60_000L
    private const val IDLE_TIMEOUT_MS = 240_000L

    fun heartbeatDelay(context: Context): Long =
        if (context.getSystemService<PowerManager>()?.isInteractive == false)
            IDLE_HEARTBEAT_MS
        else
            INTERACTIVE_HEARTBEAT_MS

    fun arm(context: Context) {
        val alarm = context.getSystemService<AlarmManager>() ?: return
        val timeout = if (context.getSystemService<PowerManager>()?.isInteractive == false)
            IDLE_TIMEOUT_MS
        else
            INTERACTIVE_TIMEOUT_MS
        val intent = recoveryIntent(context)
        alarm.cancel(intent)
        val triggerAt = SystemClock.elapsedRealtime() + timeout
        if (Build.VERSION.SDK_INT >= 23)
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, intent)
        else
            alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, intent)
    }

    fun cancel(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(recoveryIntent(context))
    }

    private fun recoveryIntent(context: Context): PendingIntent {
        val intent = Intent(context, TunService::class.java).setAction(ACTION_RECOVER)
        val flags = pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        return if (Build.VERSION.SDK_INT >= 26)
            PendingIntent.getForegroundService(context, REQUEST_CODE, intent, flags)
        else
            PendingIntent.getService(context, REQUEST_CODE, intent, flags)
    }
}
