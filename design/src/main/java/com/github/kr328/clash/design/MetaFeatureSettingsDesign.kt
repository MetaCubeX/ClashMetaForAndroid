package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.util.DialogManager

class MetaFeatureSettingsDesign(
    context: Context,
    initialConfiguration: ConfigurationOverride,
    val onConfigChange: (ConfigurationOverride) -> Unit = {}
) : Design<MetaFeatureSettingsDesign.Request>(context) {
    enum class Request {
        Close, ResetOverride, Save, ImportGeoIp, ImportGeoSite, ImportCountry, ImportASN,
        EditForceDomains, EditSkipDomains, EditSkipSrcAddresses, EditSkipDstAddresses,
        EditHttpPorts, EditTlsPorts, EditQuicPorts
    }

    var config by mutableStateOf(initialConfiguration)
    val dialogManager = DialogManager()

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.MetaFeatureSettingsScreen(this)
    }

    fun requestClear() {
        requests.trySend(Request.ResetOverride)
    }
}
