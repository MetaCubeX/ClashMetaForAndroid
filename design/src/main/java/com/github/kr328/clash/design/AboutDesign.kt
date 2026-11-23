package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import universe.agentic.clash.AboutScreen
import universe.agentic.clash.AgenticClashTheme
import universe.agentic.clash.NoticesScreen
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener

class AboutDesign(
    context: Context,
    private val versionName: String,
    appIcon: Bitmap,
) : Design<AboutDesign.Request>(context) {
    sealed interface Request {
        data class Open(val url: String) : Request
        data class Copy(val text: String) : Request
    }

    private var insets by mutableStateOf(Insets.EMPTY)
    private var notices by mutableStateOf(false)
    private var focusNotices by mutableStateOf(false)
    private val noticesFocus = FocusRequester()
    private val icon: ImageBitmap = appIcon.asImageBitmap()

    fun back(): Boolean {
        if (!notices) return false
        notices = false
        focusNotices = true
        return true
    }

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AgenticClashTheme {
                if (notices) {
                    NoticesScreen(
                        insets = insets,
                        title = context.getString(R.string.about_notices),
                        onBack = {
                            notices = false
                            focusNotices = true
                        },
                        onOpen = { requests.trySend(Request.Open(it)) },
                    )
                } else {
                    AboutScreen(
                        insets = insets,
                        title = context.pushedTitle(R.string.about),
                        versionName = versionName,
                        icon = icon,
                        noticesFocus = noticesFocus,
                        focusNotices = focusNotices,
                        onFocusNoticesConsumed = { focusNotices = false },
                        onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                        onOpen = { requests.trySend(Request.Open(it)) },
                        onCopy = { requests.trySend(Request.Copy(it)) },
                        onNotices = { notices = true },
                    )
                }
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
