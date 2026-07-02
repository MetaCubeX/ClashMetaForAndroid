package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficUpload
import com.github.kr328.clash.service.widget.SpeedWidgetRenderer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit

/**
 * Drives the home-screen speed widget (#101) straight from the running VPN service: on each
 * tick it queries live traffic and renders the widget via [SpeedWidgetRenderer.renderAll] —
 * a direct AppWidgetManager update, no broadcast, so it never has to cold-start the app's UI
 * process (which is what made the earlier broadcast approach update erratically).
 *
 * Ticks fast (1s) only while the screen is on; the whole point is a glanceable instantaneous
 * rate that nobody can see with the screen off. On stop it renders a final "not running" frame.
 */
class SpeedWidgetModule(service: Service) : Module<Unit>(service) {
    private val widgetManager = AppWidgetManager.getInstance(service)

    private fun hasWidgets(): Boolean = runCatching {
        widgetManager.getAppWidgetIds(SpeedWidgetRenderer.provider(service)).isNotEmpty()
    }.getOrDefault(false)

    private fun push(running: Boolean) {
        if (!hasWidgets()) return
        val now = if (running) Clash.queryTrafficNow() else 0L
        SpeedWidgetRenderer.renderAll(
            service,
            running = running,
            up = now.trafficUpload(),
            down = now.trafficDownload(),
        )
    }

    override suspend fun run() = coroutineScope {
        var interactive = service.getSystemService<PowerManager>()?.isInteractive ?: true

        val screenToggle = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        val profileLoaded = receiveBroadcast(capacity = Channel.CONFLATED) {
            addAction(Intents.ACTION_PROFILE_LOADED)
        }

        val tickerFast = ticker(TimeUnit.SECONDS.toMillis(1))
        val tickerIdle = ticker(TimeUnit.SECONDS.toMillis(30))

        push(running = true)

        try {
            while (true) {
                select<Unit> {
                    screenToggle.onReceive {
                        interactive = it.action == Intent.ACTION_SCREEN_ON
                        if (interactive) push(running = true)
                    }
                    profileLoaded.onReceive { push(running = true) }
                    if (interactive) {
                        tickerFast.onReceive { push(running = true) }
                    } else {
                        tickerIdle.onReceive { push(running = true) }
                    }
                }
            }
        } finally {
            push(running = false)
        }
    }
}
