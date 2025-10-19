package com.github.kr328.clash.design.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Surface {
    var insets: Insets by mutableStateOf(Insets.EMPTY)
}