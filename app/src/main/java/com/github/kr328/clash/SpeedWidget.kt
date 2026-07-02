package com.github.kr328.clash

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.service.widget.SpeedWidgetRenderer
import com.github.kr328.clash.util.stopClashService

/**
 * Home-screen speed widget provider (#101). Thin: rendering lives in the shared
 * [SpeedWidgetRenderer] (:service) so the running VPN service can update the widget directly
 * every tick. This provider only handles lifecycle (add / resize / OS refresh) and the tap,
 * which toggles the VPN on/off.
 */
class SpeedWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val running = StatusClient(context).currentProfile() != null
        ids.forEach { SpeedWidgetRenderer.renderId(context, manager, it, running, ZERO, ZERO) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle,
    ) {
        val running = StatusClient(context).currentProfile() != null
        SpeedWidgetRenderer.renderId(context, manager, id, running, ZERO, ZERO)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intents.ACTION_WIDGET_TOGGLE) toggle(context) else super.onReceive(context, intent)
    }

    private fun toggle(context: Context) {
        if (StatusClient(context).currentProfile() != null) {
            context.stopClashService()
            // Reflect OFF immediately; the service also renders a final "not running" frame.
            SpeedWidgetRenderer.renderAll(context, running = false, up = ZERO, down = ZERO)
        } else {
            // QuickTileStartActivity handles VPN permission + auto-select, then starts; once the
            // service is up, SpeedWidgetModule renders the live ON frames.
            context.startActivity(
                Intent(context, QuickTileStartActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    companion object {
        private const val ZERO = "0 B"
    }
}
