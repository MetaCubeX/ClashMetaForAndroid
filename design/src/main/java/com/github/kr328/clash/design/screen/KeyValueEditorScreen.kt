package com.github.kr328.clash.design.screen

import androidx.compose.runtime.*
import com.github.kr328.clash.design.components.EditorItemList
import com.github.kr328.clash.design.components.EditorScreen
import com.github.kr328.clash.design.components.EmptyStateContainer
import com.github.kr328.clash.design.components.KeyValueItem
import com.github.kr328.clash.design.dialog.UnifiedKeyValueDialog
import com.github.kr328.clash.design.dialog.Validator
import com.github.kr328.clash.design.dialog.rememberDialogState
import dev.oom_wg.purejoy.mlang.MLang

@Composable
fun KeyValueEditorScreen(
    title: String,
    items: MutableMap<String, String>,
    keyValidator: (String) -> Boolean,
    valueValidator: (String) -> Boolean,
    keyValidatorMessage: String,
    valueValidatorMessage: String,
    keyPlaceholder: String,
    valuePlaceholder: String,
    onItemsChange: (Map<String, String>) -> Unit,
    onRequestSave: () -> Unit,
    onRequestClose: () -> Unit
) {
    // 对话框状态
    val addDialogState = rememberDialogState()
    val editDialogState = rememberDialogState()
    var editingKey by remember { mutableStateOf<String?>(null) }

    val itemsList = items.toList()

    // 使用统一 Scaffold
    EditorScreen(
        title = title,
        onBack = {
            onRequestSave()
            onRequestClose()
        },
        onAdd = { addDialogState.open() }
    ) { paddingValues ->
        if (items.isEmpty()) {
            EmptyStateContainer(paddingValues, MLang.empty_message, MLang.empty_hint)
        } else {
            EditorItemList(
                items = itemsList,
                paddingValues = paddingValues,
                headerTitle = String.format(MLang.item_count, items.size),
                onItemClick = { _, (key, _) ->
                    editingKey = key
                    editDialogState.open()
                },
                onDeleteItem = { _, (key, _) ->
                    val newItems = items.toMutableMap()
                    newItems.remove(key)
                    onItemsChange(newItems)
                },
                itemContent = { _, (key, value) ->
                    KeyValueItem(key = key, value = value)
                }
            )
        }
    }

    // 添加对话框
    UnifiedKeyValueDialog(
        title = MLang.dialog_add_title,
        state = addDialogState,
        keyLabel = keyPlaceholder,
        valueLabel = valuePlaceholder,
        keyValidator = Validator { key ->
            when {
                !keyValidator(key) -> keyValidatorMessage
                items.containsKey(key) -> MLang.error_key_exists
                else -> null
            }
        },
        valueValidator = Validator { value ->
            if (!valueValidator(value)) valueValidatorMessage else null
        },
        onConfirm = { key, value ->
            val newItems = items.toMutableMap()
            newItems[key] = value
            onItemsChange(newItems)
        }
    )

    // 编辑对话框
    editingKey?.let { currentKey ->
        UnifiedKeyValueDialog(
            title = MLang.dialog_edit_title,
            state = editDialogState,
            initialKey = currentKey,
            initialValue = items[currentKey] ?: "",
            keyLabel = keyPlaceholder,
            valueLabel = valuePlaceholder,
            keyValidator = { key ->
                when {
                    !keyValidator(key) -> keyValidatorMessage
                    key != currentKey && items.containsKey(key) -> MLang.error_key_exists
                    else -> null
                }
            },
            valueValidator = { value ->
                if (!valueValidator(value)) valueValidatorMessage else null
            },
            onConfirm = { key, value ->
                val newItems = items.toMutableMap()
                newItems.remove(currentKey)
                newItems[key] = value
                onItemsChange(newItems)
                editingKey = null
            },
            onDismiss = {
                editDialogState.close()
                editingKey = null
            }
        )
    }
}

