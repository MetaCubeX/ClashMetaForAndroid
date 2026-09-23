package com.github.kr328.clash.design

import android.content.Context
import android.content.res.Configuration
import android.text.format.Formatter
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficUpload
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.view.TvNavigationBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    enum class Request {
        OpenHome,
        ToggleStatus,
        OpenProxy,
        OpenProfiles,
        OpenProviders,
        OpenLogs,
        OpenSettings,
        OpenHelp,
        OpenAbout,
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            binding.profileName = name
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            binding.clashRunning = running
            binding.root.findViewById<TvNavigationBar>(R.id.tv_navigation_bar)?.proxyEnabled = running
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            binding.forwarded = value.trafficTotal()
        }
    }

    suspend fun setTrafficStats(now: Long, total: Long, connections: Int, memory: Long) {
        withContext(Dispatchers.Main) {
            binding.uploadSpeed = "${now.trafficUpload()}/s"
            binding.downloadSpeed = "${now.trafficDownload()}/s"
            binding.uploaded = total.trafficUpload()
            binding.downloaded = total.trafficDownload()
            binding.activeConnections = connections.toString()
            binding.memoryUsage = Formatter.formatShortFileSize(context, memory)
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            binding.mode = when (mode) {
                TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
                else -> context.getString(R.string.rule_mode)
            }
        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
            binding.hasProviders = has
        }
    }

    suspend fun setProviderCount(count: Int) {
        withContext(Dispatchers.Main) {
            binding.providerSummary = context.getString(R.string.tv_provider_summary, count)
        }
    }

    suspend fun setCurrentProxy(group: String?, node: String?) {
        withContext(Dispatchers.Main) {
            binding.currentGroup = group ?: context.getString(R.string.tv_group_fallback)
            binding.currentNode = node?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.tv_node_fallback)
        }
    }

    suspend fun focusPrimaryAction() {
        withContext(Dispatchers.Main) {
            binding.root.findViewById<View>(R.id.tv_status)?.requestFocus()
        }
    }

    suspend fun showAbout(versionName: String) {
        withContext(Dispatchers.Main) {
            val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }

            AlertDialog.Builder(context)
                .setView(binding.root)
                .show()
        }
    }

    init {
        binding.self = this

        binding.colorClashStarted = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        binding.colorClashStopped = context.resolveThemedColor(R.attr.colorClashStopped)

        if (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
        ) {
            binding.root.findViewById<TvNavigationBar>(R.id.tv_navigation_bar)?.apply {
                setActiveTab(TvNavigationBar.Tab.Home)
                proxyEnabled = binding.clashRunning
                setNextFocusDown(TvNavigationBar.Tab.Home, R.id.tv_status)
                setNextFocusDown(TvNavigationBar.Tab.Proxy, R.id.tv_proxies)
                setNextFocusDown(TvNavigationBar.Tab.Profiles, R.id.tv_profiles)
                setNextFocusDown(TvNavigationBar.Tab.Logs, R.id.tv_mode_card)
                setNextFocusDown(TvNavigationBar.Tab.Settings, R.id.tv_mode_card)
                setNextFocusDown(TvNavigationBar.Tab.Help, R.id.tv_mode_card)
                setNextFocusDown(TvNavigationBar.Tab.About, R.id.tv_mode_card)
                onTabSelected = { tab ->
                    request(
                        when (tab) {
                            TvNavigationBar.Tab.Home -> Request.OpenHome
                            TvNavigationBar.Tab.Proxy -> Request.OpenProxy
                            TvNavigationBar.Tab.Profiles -> Request.OpenProfiles
                            TvNavigationBar.Tab.Logs -> Request.OpenLogs
                            TvNavigationBar.Tab.Settings -> Request.OpenSettings
                            TvNavigationBar.Tab.Help -> Request.OpenHelp
                            TvNavigationBar.Tab.About -> Request.OpenAbout
                        },
                    )
                }
            }
            binding.root.post {
                binding.root.findViewById<View>(R.id.tv_status)?.requestFocus()
            }
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
