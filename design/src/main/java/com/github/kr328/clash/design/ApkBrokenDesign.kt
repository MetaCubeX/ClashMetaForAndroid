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
import com.github.kr328.clash.design.ios.ApkBrokenScreen
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener

class ApkBrokenDesign(context: Context) : Design<ApkBrokenDesign.Request>(context) {
    data class Request(val url: String)

    private var insets by mutableStateOf(Insets.EMPTY)

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ClashMetaXTheme {
                ApkBrokenScreen(
                    insets = insets,
                    title = context.pushedTitle(R.string.application_broken),
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onReleases = {
                        requests.trySend(Request(context.getString(R.string.meta_github_url)))
                    },
                )
            }
        }
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

private fun Context.pushedTitle(fallback: Int): String {
    val current = (this as? Activity)?.title?.toString()?.ifBlank { null }
    return current ?: getString(fallback)
}
