package com.github.kr328.clash.design.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.LogsDesign
import com.github.kr328.clash.design.components.SettingRowDropdown
import com.github.kr328.clash.design.components.StandardListScreen
import com.github.kr328.clash.design.dialog.DialogState
import com.github.kr328.clash.design.dialog.UnifiedConfirmDialog
import com.github.kr328.clash.design.theme.AppDimensions
import com.github.kr328.clash.design.util.finishActivity
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Play
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogsScreen(design: LogsDesign) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        design.loadLogs()
    }

    // 删除确认对话框
    if (design.showDeleteDialog) {
        val dialogState = remember { mutableStateOf(true) }

        UnifiedConfirmDialog(
            title = MLang.logs_dialog_delete_title,
            state = DialogState(true),
            onConfirm = {
                design.deleteAllLogs()
                dialogState.value = false
                design.showDeleteDialog = false
            },
            onDismiss = {
                dialogState.value = false
                design.showDeleteDialog = false
            }
        )
    }

    StandardListScreen(
        title = MLang.logs_page_title,
        onBack = { context.finishActivity() },
        actions = {
            // 日志记录控制按钮
            IconButton(
                modifier = Modifier.padding(end = AppDimensions.spacing_xxl),
                onClick = {
                    if (design.isLogcatRunning) {
                        // 如果服务正在运行，直接打开实时日志页面
                        design.request(LogsDesign.Request.OpenLogcat)
                    } else {
                        // 开始记录并跳转到实时日志
                        design.request(LogsDesign.Request.StartLogcat)
                        design.request(LogsDesign.Request.OpenLogcat)
                    }
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Useful.Play,
                    contentDescription = MLang.action_start_logging
                )
            }
        }
    ) {
        // 日志等级选择器
        item {
            SmallTitle(MLang.logs_section_filter)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                var selectedIndex by remember(design.currentLogLevel) {
                    mutableStateOf(design.logLevels.indexOf(design.currentLogLevel))
                }

                SettingRowDropdown(
                    title = MLang.logs_level_label,
                    items = design.logLevels,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { newIndex ->
                        selectedIndex = newIndex
                        design.currentLogLevel = design.logLevels[newIndex]
                    }
                )
                // 删除全部日志按钮
                if (design.logs.isNotEmpty()) {
                    SuperArrow(
                        title = String.format(MLang.logs_delete_all, design.logs.size),
                        onClick = { design.showDeleteDialog = true }
                    )
                }
            }
        }


        // 日志文件列表标题
        item {
            SmallTitle(
                text = if (design.isLogcatRunning)
                    String.format(MLang.logs_files_title_recording, design.logs.size)
                else
                    String.format(MLang.logs_files_title, design.logs.size),
            )
        }

        // 日志文件列表
        items(design.logs) { logFile ->
            LogFileItem(
                logFile = logFile,
                onClick = { design.request(LogsDesign.Request.OpenLogFile(logFile)) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}


@Composable
fun LogFileItem(
    logFile: com.github.kr328.clash.design.model.LogFile,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val formattedDate = remember(logFile.date) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(logFile.date)
    }

    // 获取文件大小的辅助函数
    val fileSize = remember(logFile.fileName) {
        try {
            val logDir = File(context.cacheDir, "logs")
            val file = File(logDir, logFile.fileName)
            if (file.exists()) {
                val length = file.length()
                when {
                    length < 1024 -> "${length} B"
                    length < 1024 * 1024 -> "${length / 1024} KB"
                    else -> "${length / (1024 * 1024)} MB"
                }
            } else {
                MLang.logs_unknown_size
            }
        } catch (e: Exception) {
            MLang.logs_unknown_size
        }
    }

    // 根据文件名判断日志类型
    val logType = remember(logFile.fileName) {
        when {
            logFile.fileName.contains("error", ignoreCase = true) -> MLang.logs_type_error
            logFile.fileName.contains("warning", ignoreCase = true) -> MLang.logs_type_warning
            logFile.fileName.contains("info", ignoreCase = true) -> MLang.logs_type_info
            logFile.fileName.contains("debug", ignoreCase = true) -> MLang.logs_type_debug
            logFile.fileName.contains("silent", ignoreCase = true) -> MLang.logs_type_silent
            else -> MLang.logs_type_all
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        SuperArrow(
            title = logFile.fileName,
            summary = "${MLang.logs_type_label}$logType | ${MLang.logs_create_time_label}$formattedDate | ${MLang.logs_size_label}$fileSize",
            onClick = onClick
        )
    }
}

