package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.util.DialogManager

class OverrideSettingsDesign(
    context: Context,
    configuration: ConfigurationOverride,
    val onConfigChange: (ConfigurationOverride) -> Unit = {}
) : Design<OverrideSettingsDesign.Request>(context) {
    enum class Request {
        Close, ResetOverride, Save,

        EditAuthentication, EditCorsOrigins, EditNameServers, EditFallbackDns,
        EditDefaultDns, EditFakeIpFilter, EditFallbackDomains, EditFallbackIpCidr,

        EditHosts, EditNameServerPolicy
    }

    var config by mutableStateOf(configuration)
    val dialogManager = DialogManager()

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.OverrideSettingsScreen(this)
    }

    fun requestClear() {
        requests.trySend(Request.ResetOverride)
    }
}
