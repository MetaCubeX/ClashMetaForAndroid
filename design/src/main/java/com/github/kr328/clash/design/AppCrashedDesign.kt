package com.github.kr328.clash.design

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.getSystemService
import com.github.kr328.clash.design.ios.AppCrashedScreen
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import kotlinx.coroutines.launch

class AppCrashedDesign(context: Context) : Design<Unit>(context) {
    private var insets by mutableStateOf(Insets.EMPTY)
    private var logs by mutableStateOf<String?>(null)

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ClashMetaXTheme {
                AppCrashedScreen(
                    insets = insets,
                    title = context.pushedTitle(R.string.application_crashed),
                    logs = logs,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onCopy = { copyLogs() },
                )
            }
        }
    }

    fun setAppLogs(logs: String) {
        this.logs = logs
    }

    private fun copyLogs() {
        val text = logs ?: return
        if (text.isEmpty()) return
        launch {
            val data = ClipData.newPlainText("crash", text)
            val clipboard = context.getSystemService<ClipboardManager>() ?: return@launch
            clipboard.setPrimaryClip(data)
            showToast(R.string.copied, ToastDuration.Short)
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
