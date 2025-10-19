package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import com.github.kr328.clash.design.screen.AboutScreen

class AboutDesign(
    context: Context,
    val versionName: String,
) : Design<AboutDesign.Request>(context) {

    sealed class Request {
        data class OpenUrl(val url: String) : Request()
        object Back : Request()
    }

    @Composable
    override fun Content() {
        AboutScreen(
            design = this,
        )
    }
}


