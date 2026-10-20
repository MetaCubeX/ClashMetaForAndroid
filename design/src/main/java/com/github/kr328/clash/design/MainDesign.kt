package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.downloadBytes
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.core.util.trafficUpload
import com.github.kr328.clash.core.util.uploadBytes
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.HomeScreen
import com.github.kr328.clash.design.ios.HomeUiState
import com.github.kr328.clash.design.ios.TrafficRates
import com.github.kr328.clash.design.ios.TrafficSample
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    sealed interface Request {
        data object ToggleStatus : Request
        data class OpenProxy(val group: String?) : Request
        data object OpenProfiles : Request
        data object OpenProviders : Request
        data object OpenAppsStrategy : Request
        data object OpenLogs : Request
        data object OpenSettings : Request
        data object OpenHelp : Request
        data object OpenAbout : Request
        data class SetMode(val mode: TunnelState.Mode) : Request
    }

    private var state by mutableStateOf(HomeUiState())
    private var insets by mutableStateOf(Insets.EMPTY)

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ClashMetaXTheme {
                HomeScreen(
                    insets = insets,
                    state = state,
                    onToggle = { request(Request.ToggleStatus) },
                    onMode = { request(Request.SetMode(it)) },
                    onProfiles = { request(Request.OpenProfiles) },
                    onApps = { request(Request.OpenAppsStrategy) },
                    onProxy = { group -> request(Request.OpenProxy(group)) },
                    onProviders = { request(Request.OpenProviders) },
                    onLogs = { request(Request.OpenLogs) },
                    onSettings = { request(Request.OpenSettings) },
                    onHelp = { request(Request.OpenHelp) },
                    onAbout = { request(Request.OpenAbout) },
                )
            }
        }
    }

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            state = state.copy(profileName = name)
        }
    }

    suspend fun setAppsStrategyName(name: String?) {
        withContext(Dispatchers.Main) {
            state = state.copy(appsStrategyName = name)
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            state = if (running) {
                state.copy(running = true)
            } else {
                state.copy(running = false, currentNode = null, currentGroup = null, traffic = TrafficRates())
            }
        }
    }

    suspend fun setTraffic(now: Long, total: Long) {
        withContext(Dispatchers.Main) {
            val sample = TrafficSample(
                up = now.uploadBytes().toFloat(),
                down = now.downloadBytes().toFloat(),
            )
            state = state.copy(
                forwarded = total.trafficTotal(),
                traffic = state.traffic.copy(
                    upload = now.trafficUpload(),
                    download = now.trafficDownload(),
                    uploadTotal = total.trafficUpload(),
                    downloadTotal = total.trafficDownload(),
                    samples = (state.traffic.samples + sample).takeLast(36),
                ),
            )
        }
    }

    suspend fun setCurrentNode(name: String?, group: String?) {
        withContext(Dispatchers.Main) {
            state = state.copy(currentNode = name, currentGroup = group)
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            state = state.copy(mode = mode)
        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
            state = state.copy(hasProviders = has)
        }
    }

    suspend fun showAbout(versionName: String) {
        withContext(Dispatchers.Main) {
            val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }

            AlertDialog.Builder(context)
                .setView(binding.root)
                .setPositiveButton(context.getString(R.string.ok), null)
                .show()
        }
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }

    init {
        if (context is AppCompatActivity) {
            context.window.decorView.setOnInsertsChangedListener {
                if (surface.insets != it) surface.insets = it
                insets = it
            }
        }
    }
}
