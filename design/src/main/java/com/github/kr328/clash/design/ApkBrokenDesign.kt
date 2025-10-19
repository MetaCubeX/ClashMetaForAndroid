package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import com.github.kr328.clash.design.screen.ApkBrokenScreen

class ApkBrokenDesign(context: Context) : Design<ApkBrokenDesign.Request>(context) {
    data class Request(val url: String)

    @Composable
    override fun Content() {
        ApkBrokenScreen { url ->
            requests.trySend(Request(url))
        }
    }
}
