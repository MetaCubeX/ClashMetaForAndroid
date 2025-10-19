package com.github.kr328.clash.design.screen

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.LogcatDesign
import com.github.kr328.clash.design.components.SettingRowDropdown
import com.github.kr328.clash.design.components.StandardListScreen
import com.github.kr328.clash.design.modifiers.standardCardPadding
import com.github.kr328.clash.design.theme.AppDimensions
import com.github.kr328.clash.design.util.finishActivity
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Remove
import top.yukonga.miuix.kmp.icon.icons.useful.Save
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LogcatScreen(design: LogcatDesign) {
    val listState = rememberLazyListState()

    // 自动滚动到顶部 - 当开启实时模式时，监听过滤后的消息数量变化
    // 因为现在是倒序排列，最新的日志在顶部（索引 0）
    LaunchedEffect(design.getFilteredMessages().size) {
        if (design.streaming && design.isRunning && design.getFilteredMessages().isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // 复制成功对话框
    if (design.showCopiedDialog) {
        val dialogState = remember { mutableStateOf(true) }

        SuperDialog(
            title = MLang.logcat_copied_title,
            show = dialogState,
            onDismissRequest = {
                dialogState.value = false
                design.showCopiedDialog = false
            }
        ) {
            TextButton(
                text = MLang.action_ok,
                onClick = {
                    dialogState.value = false
                    design.showCopiedDialog = false
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 删除确认对话框
    if (design.showDeleteDialog) {
        val dialogState = remember { mutableStateOf(true) }

        com.github.kr328.clash.design.dialog.UnifiedConfirmDialog(
            title = MLang.logcat_delete_title,
            state = com.github.kr328.clash.design.dialog.DialogState(true),
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

    // 导出确认对话框
    if (design.showExportDialog) {
        val dialogState = remember { mutableStateOf(true) }

        com.github.kr328.clash.design.dialog.UnifiedConfirmDialog(
            title = MLang.logcat_export_title,
            state = com.github.kr328.clash.design.dialog.DialogState(true),
            onConfirm = {
                design.exportLogs()
                dialogState.value = false
                design.showExportDialog = false
            },
            onDismiss = {
                dialogState.value = false
                design.showExportDialog = false
            },
            confirmText = MLang.action_export
        )
    }

    val context = LocalContext.current

    StandardListScreen(
        title = MLang.logcat_page_title,
        onBack = { context.finishActivity() },
        actions = {
            // 历史日志模式：始终显示导出按钮
            if (!design.streaming) {
                IconButton(
                    modifier = Modifier.padding(end = AppDimensions.spacing_xxl),
                    onClick = { design.showExportDialog = true }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Useful.Save,
                        contentDescription = MLang.action_export
                    )
                }
            }
            // 实时日志模式：根据是否正在记录显示不同按钮
            else if (design.isRunning) {
                // 正在记录时只显示停止按钮
                IconButton(
                    modifier = Modifier.padding(end = AppDimensions.spacing_xxl),
                    onClick = {
                        design.isRunning = false
                        design.request(LogcatDesign.Request.Close)
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Useful.Remove,
                        contentDescription = MLang.action_stop_logging
                    )
                }
            }
            // 停止记录后的实时日志：不显示任何按钮（避免混淆）
        }
    ) {
        // 日志等级筛选器
        item {
            SmallTitle(MLang.logcat_section_filter)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .standardCardPadding()
            ) {
                var selectedIndex by remember(design.currentLogLevel) {
                    mutableStateOf(design.logLevels.indexOf(design.currentLogLevel))
                }

                SettingRowDropdown(
                    title = MLang.logcat_level_label,
                    items = design.logLevels,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { newIndex ->
                        selectedIndex = newIndex
                        design.currentLogLevel = design.logLevels[newIndex]
                    }
                )

                // 删除当前日志按钮 - 历史日志或停止记录后的实时日志可用
                if ((!design.streaming || (design.streaming && !design.isRunning)) && design.messages.isNotEmpty()) {
                    SuperArrow(
                        title = MLang.logcat_delete_current_log,
                        summary = MLang.logcat_delete_current_log_summary,
                        onClick = {
                            design.showDeleteDialog = true
                        }
                    )
                }
            }
        }


        // 显示当前日志统计信息（调试用）
        item {
            if (design.messages.isNotEmpty()) {
                SmallTitle(
                    text = String.format(
                        MLang.logcat_current_display,
                        design.getFilteredMessages().size,
                        design.messages.size
                    )
                )
            }
        }

        // 日志消息列表（带动画效果）
        items(
            items = design.getFilteredMessages(),
            key = { message -> "${message.time.time}_${message.message.hashCode()}" }
        ) { message ->
            LogMessageItem(
                message = message,
                onClick = { design.copyToClipboard(message.message) },
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(durationMillis = 300),
                    fadeOutSpec = tween(durationMillis = 300),
                    placementSpec = tween(durationMillis = 300)
                )
            )
        }
    }
}

@Composable
private fun LogMessageItem(
    message: LogMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (message.level) {
        LogMessage.Level.Error -> {
            MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        }

        LogMessage.Level.Warning -> {
            MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        }

        LogMessage.Level.Info -> {
            MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
        }

        else -> MiuixTheme.colorScheme.surface
    }

    val textColor = when (message.level) {
        LogMessage.Level.Error -> MiuixTheme.colorScheme.primary
        LogMessage.Level.Warning -> MiuixTheme.colorScheme.primary
        LogMessage.Level.Info -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurface
    }

    val levelText = when (message.level) {
        LogMessage.Level.Error -> MLang.logcat_log_error
        LogMessage.Level.Warning -> MLang.logcat_log_warning
        LogMessage.Level.Info -> MLang.logcat_log_info
        LogMessage.Level.Debug -> MLang.logcat_log_debug
        LogMessage.Level.Silent -> MLang.logcat_log_silent
        LogMessage.Level.Unknown -> MLang.logcat_log_unknown
    }

    Card(
        modifier = modifier
            .padding(
                horizontal = AppDimensions.margin_horizontal,
                vertical = AppDimensions.spacing_xs
            )
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(AppDimensions.spacing_md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = levelText,
                    style = MiuixTheme.textStyles.body2,
                    color = textColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = getFormattedTimestamp(message.time.time),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(AppDimensions.spacing_xs))
            Text(
                text = message.message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun getFormattedTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return "--:--:--"
    val date = java.util.Date(timestamp)
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(date)
}

