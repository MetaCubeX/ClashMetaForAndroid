package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.oom_wg.purejoy.mlang.MLang

class ListEditorDesign(
    context: Context,
    val title: String,
    initialItems: List<String>,
    val validator: (String) -> Boolean = { true },
    val validatorMessage: String = MLang.validation_invalid,
    val onSave: (List<String>) -> Unit = {},
    val onRequestClose: () -> Unit = {}
) : Design<ListEditorDesign.Request>(context) {
    enum class Request { Save, Close }

    var items by mutableStateOf(initialItems.toMutableList())

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.ListEditorScreen(
            title = title,
            items = items,
            validator = validator,
            validatorMessage = validatorMessage,
            onItemsChange = { items = it.toMutableList() },
            onRequestSave = { requests.trySend(Request.Save) },
            onRequestClose = { onRequestClose() }
        )
    }
}
