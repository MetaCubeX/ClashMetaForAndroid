package com.github.kr328.clash.design.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.kr328.clash.design.dialog.DialogState

/** 对话框管理器 */
class DialogManager {
    private val states = mutableMapOf<String, DialogState>()

    fun getDialog(key: String): DialogState {
        return states.getOrPut(key) { DialogState() }
    }

    fun getDialogs(vararg keys: String): Map<String, DialogState> {
        return keys.associateWith { getDialog(it) }
    }

    fun closeAll() {
        states.values.forEach { it.close() }
    }
}

object DialogKeys {
    const val HTTP_PORT = "httpPort"
    const val SOCKS_PORT = "socksPort"
    const val REDIRECT_PORT = "redirectPort"
    const val TPROXY_PORT = "tproxyPort"
    const val MIXED_PORT = "mixedPort"
    const val BIND_ADDRESS = "bindAddress"
    const val EXTERNAL_CONTROLLER = "externalController"
    const val EXTERNAL_CONTROLLER_TLS = "externalControllerTls"
    const val DNS_LISTEN = "dnsListen"
    const val GEOIP_CODE = "geoIpCode"
    const val SECRET = "secret"
    const val RESET = "reset"
}

@Composable
fun rememberDialogManager(): DialogManager {
    return remember { DialogManager() }
}

