package com.github.kr328.clash.design.ios

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.kr328.clash.common.compat.preferredLocale
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets

@Composable
fun AppCrashedScreen(
    insets: Insets,
    title: String,
    logs: String?,
    onBack: () -> Unit,
    onCopy: () -> Unit,
) {
    val context = LocalContext.current
    val notice = remember(context) { systemLogNotice(context) }
    val hasLogs = !logs.isNullOrEmpty()
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = { CopyLabel(enabled = hasLogs, onClick = onCopy) },
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = notice,
                style = IosType.footnote,
                color = IosTheme.colors.secondaryLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 8.dp),
            )
            when {
                logs == null -> Unit
                logs.isEmpty() -> IosEmptyState(message = noSystemLog(context))
                else -> {
                    val text = logs
                    Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = text,
                                style = IosType.footnote.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 20.sp,
                                ),
                                color = IosTheme.colors.label,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyLabel(enabled: Boolean, onClick: () -> Unit) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .background(if (pressed && enabled) colors.pressed else Color.Transparent)
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.age_key_copy),
            style = IosType.body,
            color = if (enabled) colors.blue else colors.tertiaryLabel,
        )
    }
}

private fun systemLogNotice(context: Context): String {
    val locale = context.resources.configuration.preferredLocale
    return when (locale.language) {
        "zh" -> when (locale.country) {
            "TW" -> "這是一次系統日誌，不是上面的擷取檔案。"
            "HK", "MO" -> "這是一次系統日誌，不是上面的捕獲檔案。"
            else -> "这是一次系统日志，不是上面的捕获文件。"
        }
        "ja" -> "これはシステムログであり、上のキャプチャファイルではありません。"
        "ko" -> "이것은 시스템 로그이며, 위의 캡처 파일이 아닙니다."
        "ru" -> "Это системный журнал, а не файл захвата выше."
        "vi" -> "Đây là nhật ký hệ thống, không phải tệp đã ghi ở trên."
        else -> "This is a system log, not the capture file above."
    }
}

private fun noSystemLog(context: Context): String {
    val locale = context.resources.configuration.preferredLocale
    return when (locale.language) {
        "zh" -> when (locale.country) {
            "TW", "HK", "MO" -> "沒有系統日誌"
            else -> "没有系统日志"
        }
        "ja" -> "システムログはありません"
        "ko" -> "시스템 로그 없음"
        "ru" -> "Системный журнал пуст"
        "vi" -> "Không có nhật ký hệ thống"
        else -> "No system log"
    }
}
