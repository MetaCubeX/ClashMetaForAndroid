package com.github.kr328.clash.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.github.kr328.clash.R
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.remote.StatusClient

object WidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, ClashWidgetProvider::class.java)

        update(context, manager, manager.getAppWidgetIds(component))
    }

    @Synchronized
    fun update(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty())
            return

        try {
            val store = WidgetStore(context)
            val status = StatusClient(context)
            val profile = status.currentProfile()
            val running = profile != null

            val node = if (running)
                status.currentProxy()?.takeIf { it.isNotBlank() }
            else
                null

            if (node != null)
                store.lastNode = node

            val name = node
                ?: profile?.takeIf { it.isNotBlank() }
                ?: store.lastNode.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.widget_no_node)

            val night = WidgetAppearanceResolver.isNight(context, store.appearance)
            val background = WidgetAppearanceResolver.background(night, store.transparency)
            val foreground = ContextCompat.getColor(
                context,
                WidgetAppearanceResolver.foreground(night, running)
            )

            val state = context.getString(
                if (running) R.string.widget_state_on else R.string.widget_state_off
            )
            val description = context.getString(R.string.widget_content_description, state, name)

            val toggleIntent = Intent(context, WidgetToggleActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val togglePendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_TOGGLE,
                toggleIntent,
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
            )

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_clash).apply {
                    setInt(R.id.widget_root, "setBackgroundResource", background)
                    setInt(R.id.widget_icon, "setColorFilter", foreground)
                    setTextColor(R.id.widget_title, foreground)
                    setTextViewText(R.id.widget_title, name)
                    setContentDescription(R.id.widget_root, description)
                    setOnClickPendingIntent(R.id.widget_root, togglePendingIntent)
                }

                manager.updateAppWidget(appWidgetId, views)
            }
        } catch (e: Exception) {
            Log.w("Update widget: $e", e)
        }
    }

    private const val REQUEST_TOGGLE = 1
}
