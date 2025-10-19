package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class KeyValueEditorDesign(
    context: Context,
    val title: String,
    initialMap: Map<String, String>,
    val keyValidator: (String) -> Boolean = { true },
    val valueValidator: (String) -> Boolean = { true },
    val keyValidatorMessage: String = "键格式不正确",
    val valueValidatorMessage: String = "值格式不正确",
    val keyPlaceholder: String = "键",
    val valuePlaceholder: String = "值",
    val onSave: (Map<String, String>) -> Unit = {},
    val onRequestClose: () -> Unit = {}
) : Design<KeyValueEditorDesign.Request>(context) {
    enum class Request { Save, Close }

    var items by mutableStateOf(initialMap.toMutableMap())

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.KeyValueEditorScreen(
            title = title,
            items = items,
            keyValidator = keyValidator,
            valueValidator = valueValidator,
            keyValidatorMessage = keyValidatorMessage,
            valueValidatorMessage = valueValidatorMessage,
            keyPlaceholder = keyPlaceholder,
            valuePlaceholder = valuePlaceholder,
            onItemsChange = { items = it.toMutableMap() },
            onRequestSave = { requests.trySend(Request.Save) },
            onRequestClose = { onRequestClose() }
        )
    }
}
