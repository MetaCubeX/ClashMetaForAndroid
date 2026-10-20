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
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.LogcatScreen
import com.github.kr328.clash.design.ios.iosConfirm
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogcatDesign(
    context: Context,
    private val streaming: Boolean,
    private val fileName: String? = null,
) : Design<LogcatDesign.Request>(context) {
    enum class Request {
        Close, Delete, Export
    }

    private var insets by mutableStateOf(Insets.EMPTY)
    private var messages by mutableStateOf<List<LogMessage>>(emptyList())
    private var level by mutableStateOf<LogMessage.Level?>(null)
    private var keyword by mutableStateOf("")
    private var confirmingDelete = false

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val title = (context as? Activity)?.title?.toString()
            ?.ifBlank { null }
            ?: context.getString(R.string.logcat)
        setContent {
            ClashMetaXTheme {
                LogcatScreen(
                    insets = insets,
                    title = title,
                    streaming = streaming,
                    messages = messages,
                    level = level,
                    keyword = keyword,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onKeyword = { keyword = it },
                    onLevel = { level = it },
                    onClose = { requests.trySend(Request.Close) },
                    onDelete = { requestDelete() },
                    onExport = { requests.trySend(Request.Export) },
                    onCopy = { copyMessage(it) },
                )
            }
        }
    }

    // messages is the whole buffer, index 0 oldest. removed/appended are not a patch.
    @Suppress("UNUSED_PARAMETER")
    suspend fun patchMessages(messages: List<LogMessage>, removed: Int, appended: Int) {
        withContext(Dispatchers.Main) {
            this@LogcatDesign.messages = messages
        }
    }

    private fun requestDelete() {
        if (streaming || confirmingDelete) return
        confirmingDelete = true
        launch {
            try {
                val confirmed = withContext(Dispatchers.Main) {
                    iosConfirm(
                        context = context,
                        title = context.getString(R.string.delete),
                        message = fileName ?: context.getString(R.string.delete),
                        confirm = context.getString(R.string.delete),
                        destructive = true,
                    )
                }
                if (confirmed) requests.trySend(Request.Delete)
            } finally {
                confirmingDelete = false
            }
        }
    }

    private fun copyMessage(message: LogMessage) {
        launch {
            val data = ClipData.newPlainText("log_message", message.message)
            context.getSystemService<ClipboardManager>()?.setPrimaryClip(data)
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
