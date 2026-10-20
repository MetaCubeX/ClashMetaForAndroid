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
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.SettingsScreen
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener

class SettingsDesign(context: Context) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        StartApp, StartNetwork, StartOverride, StartMetaFeature, StartWebdav,
    }

    private var insets by mutableStateOf(Insets.EMPTY)

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val title = (context as? Activity)?.title?.toString()
            ?.ifBlank { null }
            ?: context.getString(R.string.settings)
        setContent {
            ClashMetaXTheme {
                SettingsScreen(
                    insets = insets,
                    title = title,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onApp = { request(Request.StartApp) },
                    onNetwork = { request(Request.StartNetwork) },
                    onOverride = { request(Request.StartOverride) },
                    onMeta = { request(Request.StartMetaFeature) },
                    onWebdav = { request(Request.StartWebdav) },
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
