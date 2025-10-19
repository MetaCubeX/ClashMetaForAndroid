package com.github.kr328.clash.design.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ObservableCurrentTime {
    var value: Long by mutableStateOf(System.currentTimeMillis())
        private set

    fun update() {
        value = System.currentTimeMillis()
    }
}