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
import com.github.kr328.clash.design.ios.LogsScreen
import com.github.kr328.clash.design.ios.iosConfirm
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogsDesign(context: Context) : Design<LogsDesign.Request>(context) {
    sealed class Request {
        object StartLogcat : Request()
        object DeleteAll : Request()

        data class OpenFile(val file: LogFile) : Request()
    }

    private var insets by mutableStateOf(Insets.EMPTY)
    private var files by mutableStateOf<List<LogFile>>(emptyList())

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val title = (context as? Activity)?.title?.toString()
            ?.ifBlank { null }
            ?: context.getString(R.string.logs)
        setContent {
            ClashMetaXTheme {
                LogsScreen(
                    insets = insets,
                    title = title,
                    files = files,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onStart = { requests.trySend(Request.StartLogcat) },
                    onDeleteAll = { requests.trySend(Request.DeleteAll) },
                    onOpen = { requests.trySend(Request.OpenFile(it)) },
                )
            }
        }
    }

    suspend fun patchLogs(logs: List<LogFile>) {
        withContext(Dispatchers.Main) {
            files = logs
        }
    }

    suspend fun requestDeleteAll(): Boolean {
        return withContext(Dispatchers.Main) {
            iosConfirm(
                context = context,
                title = context.getString(R.string.delete_all_logs),
                message = context.getString(R.string.delete_all_logs_warn),
                confirm = context.getString(R.string.ok),
                destructive = true,
            )
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
