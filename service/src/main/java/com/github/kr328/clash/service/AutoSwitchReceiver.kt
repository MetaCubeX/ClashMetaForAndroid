package com.github.kr328.clash.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.service.model.AutoSwitchAction
import com.github.kr328.clash.service.model.AutoSwitchStrategyType
import com.github.kr328.clash.service.model.ScheduledAutoSwitch
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.sendBroadcastSelf
import java.util.TimeZone

class AutoSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intents.ACTION_AUTO_SWITCH_RESCHEDULE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIME_SET -> schedule(context)

            Intents.ACTION_AUTO_SWITCH_TRIGGER -> {
                val actionName = intent.getStringExtra(EXTRA_ACTION) ?: return
                val action = runCatching { AutoSwitchAction.valueOf(actionName) }.getOrNull() ?: return

                when (action) {
                    AutoSwitchAction.Start -> startClash(context)
                    AutoSwitchAction.Stop -> stopClash(context)
                }

                schedule(context)
            }
        }
    }

    companion object {
        private const val EXTRA_ACTION = "action"

        fun requestReschedule(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, AutoSwitchReceiver::class.java).apply {
                action = Intents.ACTION_AUTO_SWITCH_RESCHEDULE
            }
            appContext.sendBroadcastSelf(intent)
        }

        private fun schedule(context: Context) {
            val appContext = context.applicationContext
            val store = ServiceStore(appContext)
            val strategy = store.autoSwitchStrategy

            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val cancelIntent = pendingIntent(appContext, null)
            alarmManager.cancel(cancelIntent)

            if (strategy == AutoSwitchStrategyType.None) {
                return
            }

            val next = when (strategy) {
                AutoSwitchStrategyType.None -> null
                AutoSwitchStrategyType.Weekly -> store.autoSwitchWeeklySchedule.next(
                    System.currentTimeMillis(),
                    TimeZone.getDefault(),
                )
            }

            if (next == null) {
                return
            }

            val triggerIntent = pendingIntent(appContext, next)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.triggerAtMillis,
                    triggerIntent,
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    next.triggerAtMillis,
                    triggerIntent,
                )
            }
        }

        private fun startClash(context: Context) {
            val appContext = context.applicationContext
            if (useVpnMode(appContext)) {
                val vpnIntent = VpnService.prepare(appContext)
                if (vpnIntent != null) {
                    Log.w("AutoSwitch", "VPN permission required, skip auto start")
                    return
                }
                appContext.startForegroundServiceCompat(TunService::class.intent)
            } else {
                appContext.startForegroundServiceCompat(ClashService::class.intent)
            }
        }

        private fun stopClash(context: Context) {
            context.applicationContext.sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_STOP))
        }

        private fun pendingIntent(context: Context, next: ScheduledAutoSwitch?): PendingIntent {
            val intent = Intent(context, AutoSwitchReceiver::class.java).apply {
                action = Intents.ACTION_AUTO_SWITCH_TRIGGER
                if (next != null) {
                    putExtra(EXTRA_ACTION, next.action.name)
                }
            }

            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }

        private fun useVpnMode(context: Context): Boolean {
            val preferences = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
            return preferences.getBoolean("enable_vpn", true)
        }
    }
}
