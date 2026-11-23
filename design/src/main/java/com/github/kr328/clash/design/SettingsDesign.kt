package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import universe.agentic.clash.AgenticClashTheme
import universe.agentic.clash.SettingsRootStatus
import universe.agentic.clash.SettingsScreen
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener

class SettingsDesign(
    context: Context,
    private val embedded: Boolean = false,
) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        StartApp, StartNetwork, StartOverride, StartMetaFeature, StartWebdav, StartMcp, StartAbout,
    }

    private var insets by mutableStateOf(Insets.EMPTY)
    private var status by mutableStateOf(SettingsRootStatus())

    fun showStatus(next: SettingsRootStatus) {
        status = next
    }

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(
            if (embedded) ViewCompositionStrategy.DisposeOnDetachedFromWindow
            else ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        val title = if (embedded) {
            context.getString(R.string.page_settings)
        } else {
            (context as? Activity)?.title?.toString()?.ifBlank { null }
                ?: context.getString(R.string.settings)
        }
        setContent {
            AgenticClashTheme {
                SettingsScreen(
                    insets = insets,
                    title = title,
                    status = status,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onApp = { request(Request.StartApp) },
                    onNetwork = { request(Request.StartNetwork) },
                    onOverride = { request(Request.StartOverride) },
                    onMeta = { request(Request.StartMetaFeature) },
                    onWebdav = { request(Request.StartWebdav) },
                    onMcp = { request(Request.StartMcp) },
                    onAbout = { request(Request.StartAbout) },
                    embedded = embedded,
                )
            }
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    init {
        if (context is AppCompatActivity) {
            context.window.decorView.setOnInsertsChangedListener {
                if (surface.insets != it) surface.insets = it
                insets = it
            }
        }
    }
}
