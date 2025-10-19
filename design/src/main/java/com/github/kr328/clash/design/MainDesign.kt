package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.core.util.trafficUpload

class MainDesign(context: Context, val proxyDesign: ProxyDesign) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        OpenProxy,
        OpenProfiles,
        OpenProviders,
        OpenLogs,
        OpenSettings,
        OpenHelp,
        OpenAbout,
    }

    val profileName = mutableStateOf<String?>(null)
    val clashRunning = mutableStateOf(false)
    val forwarded = mutableStateOf("0 B")
    val upload = mutableStateOf("0 B")
    val download = mutableStateOf("0 B")
    val mode = mutableStateOf(context.getString(R.string.rule_mode))
    val hasProviders = mutableStateOf(false)
    val currentProxy = mutableStateOf<String?>(null)
    val currentProxySubtitle = mutableStateOf<String?>(null)
    val currentDelay = mutableStateOf<Int?>(null)

    val toggleInProgress = mutableStateOf(false)

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.MainScreen(
            running = clashRunning.value,
            currentProfile = profileName.value,
            currentForwarded = forwarded.value,
            currentUpload = upload.value,
            currentDownload = download.value,
            currentMode = mode.value,
            currentProxy = currentProxy.value,
            currentProxySubtitle = currentProxySubtitle.value,
            currentDelay = currentDelay.value,
            isToggling = toggleInProgress.value,
            onRequest = { request(it) }
        )
    }


    suspend fun setProfileName(name: String?) = updateStateOnMain {
        profileName.value = name
    }

    fun getProfileName(): State<String?> = profileName

    suspend fun setClashRunning(running: Boolean) = updateStateOnMain {
        clashRunning.value = running
    }

    suspend fun setForwarded(value: Long) = updateStateOnMain {
        forwarded.value = value.trafficTotal()
        upload.value = value.trafficUpload()
        download.value = value.trafficDownload()
    }

    suspend fun setMode(mode: TunnelState.Mode) = updateStateOnMain {
        this@MainDesign.mode.value = when (mode) {
            TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
            TunnelState.Mode.Global -> context.getString(R.string.global_mode)
            TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
            else -> context.getString(R.string.rule_mode)
        }
    }

    suspend fun setHasProviders(has: Boolean) = updateStateOnMain {
        hasProviders.value = has
    }

    suspend fun setToggleInProgress(busy: Boolean) = updateStateOnMain {
        toggleInProgress.value = busy
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
