package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kr328.clash.design.model.LogFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LogsDesign(context: Context) : Design<LogsDesign.Request>(context) {

    sealed class Request {
        object OpenLogcat : Request()
        object DeleteAll : Request()
        object StartLogcat : Request()
        object StopLogcat : Request()
        data class OpenLogFile(val file: LogFile) : Request()
    }

    internal var logs by mutableStateOf<List<LogFile>>(emptyList())
    internal var showDeleteDialog by mutableStateOf(false)
    var isLogcatRunning by mutableStateOf(false)
    internal var currentLogLevel by mutableStateOf("全部")
    internal val logLevels = listOf("全部", "错误", "警告", "信息", "调试", "静默")

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.LogsScreen(this)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    fun finish() {
        (context as? Activity)?.finish()
    }

    fun loadLogs() {
        launch(Dispatchers.IO) {
            try {
                val logDir = File(context.cacheDir, "logs")
                val newLogs = if (logDir.exists()) {
                    logDir.listFiles()
                        ?.mapNotNull { file -> LogFile.parseFromFileName(file.name) }
                        ?.sortedByDescending { it.date }
                        ?: emptyList()
                } else {
                    logDir.mkdirs()
                    emptyList()
                }
                withContext(Dispatchers.Main) {
                    logs = newLogs
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun deleteAllLogsInternal() {
        withContext(Dispatchers.IO) {
            try {
                val logDir = File(context.cacheDir, "logs")
                if (logDir.exists()) {
                    logDir.deleteRecursively()
                    withContext(Dispatchers.Main) {
                        loadLogs()
                        showDeleteDialog = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteAllLogs() {
        launch {
            deleteAllLogsInternal()
        }
    }

    fun toggleLogcat() {
        if (isLogcatRunning) {
            requests.trySend(Request.StopLogcat)
        } else {
            requests.trySend(Request.StartLogcat)
        }
    }
}