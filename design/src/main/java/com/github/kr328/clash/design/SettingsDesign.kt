package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable


class SettingsDesign(context: Context) : Design<SettingsDesign.Request>(context) {
    enum class Request { StartApp, StartNetwork, StartOverride, StartMetaFeature }

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.SettingsScreen(
            onMainRequest = { },
            onSettingsRequest = { req -> requests.trySend(req) }
        )
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}