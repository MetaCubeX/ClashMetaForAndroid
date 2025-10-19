package com.github.kr328.clash.design.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    show: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    cancelText: String = "取消"
) {
    if (show) {
        val dialogState = remember { mutableStateOf(true) }

        LaunchedEffect(dialogState.value) {
            if (!dialogState.value) {
                onDismiss()
            }
        }

        SuperDialog(
            title = title,
            show = dialogState,
            onDismissRequest = { dialogState.value = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        text = cancelText,
                        onClick = { dialogState.value = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = confirmText,
                        onClick = {
                            onConfirm()
                            dialogState.value = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun InputDialog(
    title: String,
    show: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
    label: String = "",
    validator: (String) -> String? = { null },
    confirmText: String = "确定",
    cancelText: String = "取消"
) {
    if (show) {
        val dialogState = remember { mutableStateOf(true) }
        var inputValue by remember { mutableStateOf(initialValue) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(dialogState.value) {
            if (!dialogState.value) {
                onDismiss()
            }
        }

        SuperDialog(
            title = title,
            show = dialogState,
            onDismissRequest = { dialogState.value = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = inputValue,
                    onValueChange = {
                        inputValue = it
                        errorMessage = null
                    },
                    label = label,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.body2,
                        color = androidx.compose.ui.graphics.Color.Red
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        text = cancelText,
                        onClick = { dialogState.value = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = confirmText,
                        onClick = {
                            val error = validator(inputValue.trim())
                            if (error != null) {
                                errorMessage = error
                            } else {
                                onConfirm(inputValue.trim())
                                dialogState.value = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

