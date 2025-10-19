package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kr328.clash.design.dialog.DialogState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.store.ServiceStore

class NetworkSettingsDesign(
    context: Context,
    val uiStore: UiStore,
    val srvStore: ServiceStore,
    running: Boolean
) : Design<NetworkSettingsDesign.Request>(context) {
    var running by mutableStateOf(running)
    val warningDialogState = DialogState()

    enum class Request {
        StartAccessControlList
    }

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.NetworkSettingsScreen(this)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}