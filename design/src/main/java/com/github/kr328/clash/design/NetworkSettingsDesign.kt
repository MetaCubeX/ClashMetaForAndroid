package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.NetworkSettingsScreen
import com.github.kr328.clash.design.ios.NetworkSettingsUiState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KMutableProperty0

class NetworkSettingsDesign(
    context: Context,
    private val uiStore: UiStore,
    private val srvStore: ServiceStore,
    running: Boolean,
) : Design<NetworkSettingsDesign.Request>(context) {
    enum class Request {
        StartAccessControlList,
        AccessControlChanged,
    }

    private var insets by mutableStateOf(Insets.EMPTY)
    private var state by mutableStateOf(
        NetworkSettingsUiState(
            enableVpn = uiStore.enableVpn,
            bypassPrivateNetwork = srvStore.bypassPrivateNetwork,
            dnsHijacking = srvStore.dnsHijacking,
            allowBypass = srvStore.allowBypass,
            allowIpv6 = srvStore.allowIpv6,
            systemProxy = srvStore.systemProxy,
            showSystemProxy = Build.VERSION.SDK_INT >= 29,
            tunStackMode = srvStore.tunStackMode,
            accessControlMode = srvStore.accessControlMode,
            running = running,
            activeStrategyName = readActiveStrategyName(),
        )
    )

    private fun readActiveStrategyName(): String? {
        val uuid = srvStore.activeAppsStrategyConfigUuid ?: return null
        return srvStore.appsStrategyConfigs
            .find { it.uuid == uuid }
            ?.name
            ?.takeIf { it.isNotBlank() }
    }

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val title = (context as? Activity)?.title?.toString()
            ?.ifBlank { null }
            ?: context.getString(R.string.network)
        setContent {
            ClashMetaXTheme {
                NetworkSettingsScreen(
                    insets = insets,
                    title = title,
                    state = state,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onEnableVpn = ::setEnableVpn,
                    onVpnBlocked = ::showOptionsUnavailable,
                    onBypassPrivateNetwork = ::setBypassPrivateNetwork,
                    onDnsHijacking = ::setDnsHijacking,
                    onAllowBypass = ::setAllowBypass,
                    onAllowIpv6 = ::setAllowIpv6,
                    onSystemProxy = ::setSystemProxy,
                    onTunStack = ::setTunStack,
                    onAccessControlMode = ::setAccessControlMode,
                    onAccessControlList = ::openAccessControlList,
                )
            }
        }
    }

    private fun setEnableVpn(value: Boolean) {
        if (!state.vpnSwitchEnabled) {
            showOptionsUnavailable()
            return
        }
        launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) {
                uiStore.enableVpn = value
                uiStore.enableVpn
            }
            state = state.copy(enableVpn = stored)
        }
    }

    private fun setBypassPrivateNetwork(value: Boolean) {
        editVpnBoolean(srvStore::bypassPrivateNetwork, value) {
            state = state.copy(bypassPrivateNetwork = it)
        }
    }

    private fun setDnsHijacking(value: Boolean) {
        editVpnBoolean(srvStore::dnsHijacking, value) {
            state = state.copy(dnsHijacking = it)
        }
    }

    private fun setAllowBypass(value: Boolean) {
        editVpnBoolean(srvStore::allowBypass, value) {
            state = state.copy(allowBypass = it)
        }
    }

    private fun setAllowIpv6(value: Boolean) {
        editVpnBoolean(srvStore::allowIpv6, value) {
            state = state.copy(allowIpv6 = it)
        }
    }

    private fun setSystemProxy(value: Boolean) {
        if (!state.showSystemProxy) return
        editVpnBoolean(srvStore::systemProxy, value) {
            state = state.copy(systemProxy = it)
        }
    }

    private fun setTunStack(value: String) {
        if (!state.vpnDependenciesEnabled) return
        launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) {
                srvStore.tunStackMode = value
                srvStore.tunStackMode
            }
            state = state.copy(tunStackMode = stored)
        }
    }

    private fun setAccessControlMode(value: AccessControlMode) {
        if (!state.vpnDependenciesEnabled) return
        launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) {
                srvStore.accessControlMode = value
                srvStore.accessControlMode
            }
            state = state.copy(accessControlMode = stored)
            requests.trySend(Request.AccessControlChanged)
        }
    }

    private fun openAccessControlList() {
        requests.trySend(Request.StartAccessControlList)
    }

    private fun editVpnBoolean(
        property: KMutableProperty0<Boolean>,
        value: Boolean,
        apply: (Boolean) -> Unit,
    ) {
        if (!state.vpnDependenciesEnabled) return
        launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) {
                property.set(value)
                property.get()
            }
            apply(stored)
        }
    }

    private fun showOptionsUnavailable() {
        if (!root.isAttachedToWindow) {
            root.post { showOptionsUnavailable() }
            return
        }
        launch {
            showToast(R.string.options_unavailable, ToastDuration.Indefinite)
        }
    }

    init {
        if (context is AppCompatActivity) {
            context.window.decorView.setOnInsertsChangedListener {
                if (surface.insets != it) surface.insets = it
                insets = it
            }
        }
        if (running) showOptionsUnavailable()
    }
}
