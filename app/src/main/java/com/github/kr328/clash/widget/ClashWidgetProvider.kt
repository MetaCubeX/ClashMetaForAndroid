package com.github.kr328.clash.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.github.kr328.clash.common.constants.Intents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClashWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.update(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intents.ACTION_CLASH_STARTED,
            Intents.ACTION_CLASH_STOPPED,
            Intents.ACTION_SERVICE_RECREATED,
            Intents.ACTION_PROFILE_LOADED,
            Intents.ACTION_SELECTION_CHANGED -> {
                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WidgetUpdater.updateAll(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }
}
