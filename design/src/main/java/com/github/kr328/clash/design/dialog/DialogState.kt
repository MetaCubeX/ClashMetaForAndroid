package com.github.kr328.clash.design.dialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 统一的对话框可见性状态。
 */
class DialogState(initial: Boolean = false) {
    var show: Boolean by mutableStateOf(initial)
        private set

    fun open() {
        show = true
    }

    fun close() {
        show = false
    }
}





