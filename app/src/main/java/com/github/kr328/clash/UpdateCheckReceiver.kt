package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.log.LogRedaction
import com.github.kr328.clash.util.AppUpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UpdateCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppUpdateChecker.checkAndNotify(context)
            } catch (e: Exception) {
                Log.w("Periodic update check failed: ${LogRedaction.throwableMessage(e)}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
