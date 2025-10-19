package com.github.kr328.clash.design

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import com.github.kr328.clash.core.model.LogMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogcatDesign(
    context: Context,
    val streaming: Boolean,
) : Design<LogcatDesign.Request>(context) {
    enum class Request {
        Close, Delete, Export
    }

    var messages by mutableStateOf<List<LogMessage>>(emptyList())
    internal var showCopiedDialog by mutableStateOf(false)
    internal var showDeleteDialog by mutableStateOf(false)
    internal var showExportDialog by mutableStateOf(false)
    var isRunning by mutableStateOf(true)
    internal var currentLogLevel by mutableStateOf("全部")
    internal val logLevels = listOf("全部", "错误", "警告", "信息", "调试", "静默")

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.LogcatScreen(this)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    fun finish() {
        (context as? Activity)?.finish()
    }

    suspend fun patchMessages(newMessages: List<LogMessage>, removed: Int, appended: Int) {
        withContext(Dispatchers.Main) {
            messages = newMessages
        }
    }

    fun setInitialMessages(initialMessages: List<LogMessage>) {
        messages = initialMessages
    }

    fun copyToClipboard(message: String) {
        launch {
            val data = ClipData.newPlainText("log_message", message)
            context.getSystemService<ClipboardManager>()?.setPrimaryClip(data)
            showCopiedDialog = true
        }
    }

    fun deleteAllLogs() {
        request(Request.Delete)
    }

    fun exportLogs() {
        request(Request.Export)
    }

    fun getFilteredMessages(): List<LogMessage> {
        val filtered = if (currentLogLevel == "全部") {
            messages
        } else {
            val targetLevel = when (currentLogLevel) {
                "错误" -> LogMessage.Level.Error
                "警告" -> LogMessage.Level.Warning
                "信息" -> LogMessage.Level.Info
                "调试" -> LogMessage.Level.Debug
                "静默" -> LogMessage.Level.Silent
                else -> null
            }
            messages.filter { it.level == targetLevel }
        }
        return filtered.reversed()
    }
}
