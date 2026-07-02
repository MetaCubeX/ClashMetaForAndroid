package com.github.kr328.clash.service.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.service.R

/**
 * Single source of truth for the home-screen speed widget's RemoteViews (#101).
 *
 * Lives in :service so the running VPN service can update the widget *directly* every tick (no
 * broadcast round-trip through the app's usually-evicted UI process, which made live updates
 * lag). The :app [AppWidgetProvider] delegates its lifecycle renders here too, so both paths
 * render identically.
 *
 * Design (owner spec): the sloth mark is the hero — B&W when OFF, colour + a green dot when ON.
 * The whole tile **toggles the VPN** (never opens the app). A 1-cell instance is just the mark;
 * wider instances add the live ↑/↓ rate to the right of the mark. No wordmark, no chip.
 */
object SpeedWidgetRenderer {
    const val WIDGET_PROVIDER_CLASS = "com.github.kr328.clash.SpeedWidget"
    private const val COMPACT_MAX_WIDTH_DP = 100

    private val COLOR_ON = Color.parseColor("#5EE6A8")
    private val TEXT_PRIMARY = Color.parseColor("#ECECEC")
    private val TEXT_MUTED = Color.parseColor("#7C7C82")

    fun provider(context: Context) = ComponentName(context.packageName, WIDGET_PROVIDER_CLASS)

    /** Update every bound widget instance with the given state. */
    fun renderAll(context: Context, running: Boolean, up: String, down: String) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = runCatching { manager.getAppWidgetIds(provider(context)) }.getOrNull() ?: return
        ids.forEach { renderId(context, manager, it, running, up, down) }
    }

    fun renderId(
        context: Context, manager: AppWidgetManager, id: Int,
        running: Boolean, up: String, down: String,
    ) {
        val minWidth = manager.getAppWidgetOptions(id).getInt(OPTION_APPWIDGET_MIN_WIDTH, 0)
        val views = if (minWidth in 1 until COMPACT_MAX_WIDTH_DP)
            compact(context, running)
        else
            expanded(context, running, up, down)
        runCatching { manager.updateAppWidget(id, views) }
    }

    private fun compact(context: Context, running: Boolean): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_speed_compact).apply {
            paintMark(this, running)
            setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(context))
        }

    private fun expanded(context: Context, running: Boolean, up: String, down: String): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_speed).apply {
            paintMark(this, running)
            if (running) {
                setTextViewText(R.id.widget_up, "↑ $up/s")
                setTextColor(R.id.widget_up, TEXT_PRIMARY)
                setViewVisibility(R.id.widget_down, View.VISIBLE)
                setTextViewText(R.id.widget_down, "↓ $down/s")
            } else {
                setTextViewText(R.id.widget_up, context.getString(R.string.widget_off))
                setTextColor(R.id.widget_up, TEXT_MUTED)
                setViewVisibility(R.id.widget_down, View.GONE)
            }
            setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(context))
        }

    /** Sloth mark: colour + green dot when ON, B&W with no dot when OFF. */
    private fun paintMark(views: RemoteViews, running: Boolean) {
        views.setImageViewResource(
            R.id.widget_icon,
            if (running) R.drawable.widget_sloth else R.drawable.widget_sloth_off,
        )
        views.setViewVisibility(R.id.widget_dot, if (running) View.VISIBLE else View.GONE)
        views.setInt(R.id.widget_dot, "setColorFilter", COLOR_ON)
    }

    private fun togglePendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(Intents.ACTION_WIDGET_TOGGLE).setComponent(provider(context)),
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
        )
}
