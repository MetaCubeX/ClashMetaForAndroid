package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppCrashedDesign(context: Context) : Design<Unit>(context) {
    var appLogs by mutableStateOf("")

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.AppCrashedScreen(appLogs)
    }
}
