package com.github.kr328.clash.design.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun interface Validator<T> {
    fun validate(value: T): String?
}

object StringValidators {
    val NotEmpty: Validator<String> = Validator { value ->
        if (value.trim().isEmpty()) MLang.validation_empty else null
    }

    val IsNumber: Validator<String> = Validator { value ->
        if (value.toIntOrNull() == null) "必须是数字" else null
    }

    val IsPort: Validator<String> = Validator { value ->
        val num = value.toIntOrNull()
        when {
            num == null -> "必须是数字"
            num !in 1..65535 -> "端口范围: 1-65535"
            else -> null
        }
    }

    val IsIpAddress: Validator<String> = Validator { value ->
        val parts = value.split(".")
        if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) {
            "无效的 IP 地址"
        } else null
    }

    fun regex(pattern: String, errorMsg: String): Validator<String> = Validator { value ->
        if (!value.matches(Regex(pattern))) errorMsg else null
    }

    fun all(vararg validators: Validator<String>): Validator<String> = Validator { value ->
        validators.firstNotNullOfOrNull { it.validate(value) }
    }
}

@Composable
fun UnifiedInputDialog(
    title: String,
    state: DialogState,
    initialValue: String = "",
    label: String = MLang.input_hint,
    validator: Validator<String> = StringValidators.NotEmpty,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit = { state.close() },
    confirmText: String = MLang.action_save,
    cancelText: String = MLang.action_cancel,
    singleLine: Boolean = true,
) {
    key(state.show) {
        if (state.show) {
            val dialogShow = remember { mutableStateOf(true) }
            var inputValue by remember { mutableStateOf(initialValue) }
            var errorMessage by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(inputValue) {
                errorMessage = null
            }

            LaunchedEffect(dialogShow.value) {
                if (!dialogShow.value) {
                    state.close()
                    onDismiss()
                }
            }

            SuperDialog(
                title = title,
                show = dialogShow,
                onDismissRequest = { dialogShow.value = false }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        label = label,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = singleLine,
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = Color.Red,
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            text = cancelText,
                            onClick = { dialogShow.value = false },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = confirmText,
                            onClick = {
                                val trimmed = inputValue.trim()
                                val error = validator.validate(trimmed)
                                if (error != null) {
                                    errorMessage = error
                                } else {
                                    onConfirm(trimmed)
                                    dialogShow.value = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UnifiedConfirmDialog(
    title: String,
    state: DialogState,
    message: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = { state.close() },
    confirmText: String = MLang.action_confirm,
    cancelText: String = MLang.action_cancel,
) {
    key(state.show) {
        if (state.show) {
            val dialogShow = remember { mutableStateOf(true) }

            LaunchedEffect(dialogShow.value) {
                if (!dialogShow.value) {
                    state.close()
                    onDismiss()
                }
            }

            SuperDialog(
                title = title,
                show = dialogShow,
                onDismissRequest = { dialogShow.value = false }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (message != null) {
                        Text(
                            text = message,
                            style = MiuixTheme.textStyles.body1,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            text = cancelText,
                            onClick = { dialogShow.value = false },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = confirmText,
                            onClick = {
                                dialogShow.value = false
                                onConfirm()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UnifiedKeyValueDialog(
    title: String,
    state: DialogState,
    initialKey: String = "",
    initialValue: String = "",
    keyLabel: String = "键",
    valueLabel: String = "值",
    keyValidator: Validator<String> = StringValidators.NotEmpty,
    valueValidator: Validator<String> = StringValidators.NotEmpty,
    onConfirm: (key: String, value: String) -> Unit,
    onDismiss: () -> Unit = { state.close() },
) {
    if (!state.show) return

    val dialogShow = remember { mutableStateOf(true) }
    var inputKey by remember(initialKey) { mutableStateOf(initialKey) }
    var inputValue by remember(initialValue) { mutableStateOf(initialValue) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dialogShow.value) {
        if (!dialogShow.value) onDismiss()
    }

    SuperDialog(
        title = title,
        show = dialogShow,
        onDismissRequest = { dialogShow.value = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = inputKey,
                onValueChange = { inputKey = it; errorMessage = null },
                label = keyLabel,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            TextField(
                value = inputValue,
                onValueChange = { inputValue = it; errorMessage = null },
                label = valueLabel,
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Color.Red,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                    text = MLang.action_save,
                    onClick = {
                        val trimmedKey = inputKey.trim()
                        val trimmedValue = inputValue.trim()

                        val keyError = keyValidator.validate(trimmedKey)
                        val valueError = valueValidator.validate(trimmedValue)

                        when {
                            keyError != null -> errorMessage = keyError
                            valueError != null -> errorMessage = valueError
                            else -> {
                                onConfirm(trimmedKey, trimmedValue)
                                dialogShow.value = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


fun DialogState.openInputDialog(
    onConfirm: (String) -> Unit
): DialogState {
    this.open()
    return this
}

@Composable
fun rememberDialogState(initial: Boolean = false): DialogState {
    return remember { DialogState(initial) }
}
