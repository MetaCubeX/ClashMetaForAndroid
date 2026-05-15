package com.github.kr328.clash.util

import android.content.Context
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.sendConnectionsChanged

suspend fun Context.closeConnectionsAfterUserProxySwitchIfEnabled(
    showFeedback: suspend (String, ToastDuration) -> Unit,
) {
    if (!ServiceStore(this).closeConnectionsAfterProxySwitch) return

    runCatching {
        withClash { closeAllConnections() }
    }.onSuccess { closed ->
        showFeedback(
            getString(R.string.connections_closed_many, closed),
            ToastDuration.Short,
        )
        sendConnectionsChanged()
    }.onFailure {
        showFeedback(
            getString(R.string.proxy_switch_close_connections_failed),
            ToastDuration.Long,
        )
    }
}
