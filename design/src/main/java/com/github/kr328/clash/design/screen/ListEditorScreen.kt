package com.github.kr328.clash.design.screen

import androidx.compose.runtime.*
import com.github.kr328.clash.design.components.EditorItemList
import com.github.kr328.clash.design.components.EditorScreen
import com.github.kr328.clash.design.components.EmptyStateContainer
import com.github.kr328.clash.design.components.SimpleTextItem
import com.github.kr328.clash.design.dialog.UnifiedInputDialog
import com.github.kr328.clash.design.dialog.Validator
import com.github.kr328.clash.design.dialog.rememberDialogState
import dev.oom_wg.purejoy.mlang.MLang

@Composable
fun ListEditorScreen(
    title: String,
    items: MutableList<String>,
    validator: (String) -> Boolean,
    validatorMessage: String,
    onItemsChange: (List<String>) -> Unit,
    onRequestSave: () -> Unit,
    onRequestClose: () -> Unit
) {
    // 对话框状态
    val addDialogState = rememberDialogState()
    val editDialogState = rememberDialogState()
    var editingIndex by remember { mutableStateOf(-1) }

    EditorScreen(
        title = title,
        onBack = {
            onRequestSave()
            onRequestClose()
        },
        onAdd = { addDialogState.open() }
    ) { paddingValues ->
        if (items.isEmpty()) {
            EmptyStateContainer(
                paddingValues = paddingValues,
                message = MLang.empty_message,
                hint = MLang.empty_hint
            )
        } else {
            EditorItemList(
                items = items,
                paddingValues = paddingValues,
                headerTitle = String.format(MLang.total_items, items.size),
                onItemClick = { index, _ ->
                    editingIndex = index
                    editDialogState.open()
                },
                onDeleteItem = { index, _ ->
                    val newItems = items.toMutableList()
                    newItems.removeAt(index)
                    onItemsChange(newItems)
                },
                itemContent = { index, item ->
                    SimpleTextItem(index = index, text = item)
                }
            )
        }
    }

    // 添加对话框
    UnifiedInputDialog(
        title = MLang.dialog_add_title,
        state = addDialogState,
        label = MLang.input_hint,
        validator = Validator { value ->
            if (!validator(value)) validatorMessage else null
        },
        onConfirm = { value ->
            val newItems = items.toMutableList()
            newItems.add(value)
            onItemsChange(newItems)
        },
        confirmText = MLang.action_add
    )

    // 编辑对话框
    if (editingIndex >= 0 && editingIndex < items.size) {
        UnifiedInputDialog(
            title = MLang.dialog_edit_title,
            state = editDialogState,
            initialValue = items[editingIndex],
            label = MLang.input_label,
            validator = Validator { value ->
                if (!validator(value)) validatorMessage else null
            },
            onConfirm = { value ->
                val newItems = items.toMutableList()
                newItems[editingIndex] = value
                onItemsChange(newItems)
                editingIndex = -1
            },
            onDismiss = {
                editDialogState.close()
                editingIndex = -1
            }
        )
    }
}

