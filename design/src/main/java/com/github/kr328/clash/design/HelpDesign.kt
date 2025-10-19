package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
class HelpDesign(
    context: Context,
) : Design<HelpDesign.Request>(context) {

    sealed class Request {
        data class Open(val uri: android.net.Uri) : Request()
        object Back : Request()
    }

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.HelpScreen(
            onOpen = { uri -> requests.trySend(Request.Open(uri)) },
            onBack = { requests.trySend(Request.Back) }
        )
    }
}
