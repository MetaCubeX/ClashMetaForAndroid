package com.github.kr328.clash.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.dialog.DialogState
import com.github.kr328.clash.design.util.ValidatorsUnified
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog

/**
 * 端口输入对话框
 *
 * 用于设置页面中输入端口号的统一对话框
 *
 * @param title 对话框标题
 * @param state 对话框状态
 * @param currentValue 当前端口值
 * @param onConfirm 确认回调，返回输入的端口值（null 表示清空）
 * @param onDismiss 取消回调
 */
@Composable
fun PortInputDialog(
    title: String,
    state: DialogState,
    currentValue: Int?,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    SettingInputDialog(
        title = title,
        state = state,
        onDismiss = onDismiss,
        currentValue = currentValue?.toString() ?: "",
        label = MLang.input_port_label,
        validator = ValidatorsUnified::port,
        onConfirm = { value ->
            onConfirm(value.toIntOrNull())
        }
    )
}

/**
 * 字符串输入对话框
 *
 * 用于设置页面中输入字符串的统一对话框
 *
 * @param title 对话框标题
 * @param state 对话框状态
 * @param currentValue 当前值
 * @param label 输入框标签
 * @param validator 验证器
 * @param onConfirm 确认回调
 * @param onDismiss 取消回调
 */
@Composable
fun StringInputDialog(
    title: String,
    state: DialogState,
    currentValue: String?,
    label: String,
    validator: (String) -> String? = { null },
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    SettingInputDialog(
        title = title,
        state = state,
        onDismiss = onDismiss,
        currentValue = currentValue ?: "",
        label = label,
        validator = validator,
        onConfirm = { value ->
            onConfirm(value.ifBlank { null })
        }
    )
}

/**
 * 重置确认对话框
 *
 * 用于确认重置设置的统一对话框
 *
 * @param state 对话框状态
 * @param onConfirm 确认回调
 */
@Composable
fun ResetConfirmDialog(
    state: DialogState,
    onConfirm: () -> Unit
) {
    key(state.show) {
        if (state.show) {
            val dialogShow = remember { mutableStateOf(true) }

            LaunchedEffect(dialogShow.value) {
                if (!dialogShow.value) {
                    state.close()
                }
            }

            SuperDialog(
                title = MLang.dialog_reset_title,
                show = dialogShow,
                onDismissRequest = { dialogShow.value = false }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        text = MLang.action_cancel,
                        onClick = { dialogShow.value = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = MLang.dialog_reset_confirm,
                        onClick = {
                            dialogShow.value = false
                            onConfirm()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}


