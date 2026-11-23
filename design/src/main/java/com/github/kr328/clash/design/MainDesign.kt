package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
import universe.agentic.clash.AgenticClashTheme
import universe.agentic.clash.HomeScreen
import universe.agentic.clash.ShellPages
import universe.agentic.clash.ShellTab
import universe.agentic.clash.HomeUiState
import universe.agentic.clash.QuickNodeUi
import universe.agentic.clash.TrafficRates
import universe.agentic.clash.TrafficSample
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    sealed interface Request {
        data object ToggleStatus : Request
        data class OpenProxy(val group: String?) : Request
        data class SelectNode(val group: String, val name: String) : Request
        data object OpenProfiles : Request
        data object OpenProviders : Request
        data object OpenAppsStrategy : Request
        data object OpenLogs : Request
        data object ReloadKernel : Request
        data object OpenWebdav : Request
        data object OpenFiles : Request
        data class SetMode(val mode: TunnelState.Mode) : Request
    }

    private var state by mutableStateOf(HomeUiState())
    private var insets by mutableStateOf(Insets.EMPTY)
    var tab by mutableStateOf(ShellTab.Dashboard)
    var proxyRoot by mutableStateOf<View?>(null)
    var profilesRoot by mutableStateOf<View?>(null)
    var appsRoot by mutableStateOf<View?>(null)
    var settingsRoot by mutableStateOf<View?>(null)

    fun showTab(next: ShellTab) {
        tab = next
    }

    fun openProfilesTab() {
        requests.trySend(Request.OpenProfiles)
    }

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AgenticClashTheme {
                ShellPages(
                    insets = insets,
                    tab = tab,
                    onTab = { tab = it },
                    proxy = proxyRoot,
                    profiles = profilesRoot,
                    apps = appsRoot,
                    settings = settingsRoot,
                ) {
                    HomeScreen(
                        insets = insets,
                        state = state,
                        onToggle = { request(Request.ToggleStatus) },
                        onMode = { request(Request.SetMode(it)) },
                        onProfiles = { request(Request.OpenProfiles) },
                        onApps = { request(Request.OpenAppsStrategy) },
                        onProxy = { group -> request(Request.OpenProxy(group)) },
                        onSelectNode = { group, name -> request(Request.SelectNode(group, name)) },
                        onProviders = { request(Request.OpenProviders) },
                        onLogs = { request(Request.OpenLogs) },
                        onReload = { request(Request.ReloadKernel) },
                        onWebdav = { request(Request.OpenWebdav) },
                        onFiles = { request(Request.OpenFiles) },
                    )
                }
            }
        }
    }

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            state = state.copy(profileName = name)
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            state = if (running) {
                state.copy(running = true)
            } else {
                state.copy(
                    running = false,
                    currentNode = null,
                    currentGroup = null,
                    currentDelay = -1,
                    nodeSelectable = false,
                    quickNodes = emptyList(),
                    traffic = TrafficRates(),
                    forwarded = "",
                    uptime = "",
                    providerCount = -1,
                )
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

    suspend fun setOutbound(
        name: String?,
        group: String?,
        delay: Int,
        selectable: Boolean,
        nodes: List<QuickNodeUi>,
    ) {
        withContext(Dispatchers.Main) {
            state = state.copy(
                currentNode = name,
                currentGroup = group,
                currentDelay = delay,
                nodeSelectable = selectable,
                quickNodes = nodes,
            )
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            state = state.copy(mode = mode)
        }
    }

    suspend fun setUptime(uptime: String) {
        withContext(Dispatchers.Main) {
            state = state.copy(uptime = uptime)
        }
    }

    suspend fun setAside(
        strategy: String?,
        webdav: String,
        files: String,
        providerCount: Int,
    ) {
        withContext(Dispatchers.Main) {
            state = state.copy(
                appsStrategyName = strategy,
                webdavDetail = webdav,
                filesDetail = files,
                providerCount = providerCount,
            )
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
