package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.ClickablePreference
import com.github.kr328.clash.design.preference.category
import com.github.kr328.clash.design.preference.clickable
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.tips
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class DiagnosticsDesign(
    context: Context,
    initialState: State,
) : Design<DiagnosticsDesign.Request>(context) {
    enum class Request {
        EnableDebug, DisableDebug, Export,
    }

    data class State(
        val clashRunning: Boolean,
        val appVersion: String,
        val coreVersion: String,
        val mode: String?,
        val tunStack: String,
        val logLevel: String?,
        val dnsEnhancedMode: String?,
        val ipv6: String?,
        val connections: Int,
    )

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private var state: State = initialState

    private val statusValues = mutableMapOf<String, ClickablePreference>()

    init {
        binding.surface = surface

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        rebuild()
    }

    fun patch(newState: State) {
        state = newState

        statusValues["clash_status"]?.let { it.summary = statusText(state) }
        statusValues["app_version"]?.let { it.summary = state.appVersion }
        statusValues["core_version"]?.let { it.summary = state.coreVersion }
        statusValues["mode"]?.let { it.summary = state.mode ?: "-" }
        statusValues["tun_stack"]?.let { it.summary = state.tunStack }
        statusValues["log_level"]?.let { it.summary = state.logLevel ?: "-" }
        statusValues["dns_mode"]?.let { it.summary = state.dnsEnhancedMode ?: "-" }
        statusValues["ipv6"]?.let { it.summary = state.ipv6 ?: "-" }
        statusValues["connections"]?.let { it.summary = state.connections.toString() }
    }

    private fun statusText(state: State): String {
        return if (state.clashRunning) {
            context.getString(R.string.diagnostics_running)
        } else {
            context.getString(R.string.diagnostics_stopped)
        }
    }

    private fun rebuild() {
        binding.content.removeAllViews()

        val screen = preferenceScreen(context) {
            tips(R.string.diagnostics_tips)

            category(R.string.diagnostics_status)

            clickable(
                title = R.string.diagnostics_clash_status,
            ) {
                summary = statusText(state)
            }.also {
                statusValues["clash_status"] = it
            }

            clickable(
                title = R.string.diagnostics_app_version,
            ) {
                summary = state.appVersion
            }.also {
                statusValues["app_version"] = it
            }

            clickable(
                title = R.string.diagnostics_core_version,
            ) {
                summary = state.coreVersion
            }.also {
                statusValues["core_version"] = it
            }

            clickable(
                title = R.string.diagnostics_mode,
            ) {
                summary = state.mode ?: "-"
            }.also {
                statusValues["mode"] = it
            }

            clickable(
                title = R.string.diagnostics_tun_stack,
            ) {
                summary = state.tunStack
            }.also {
                statusValues["tun_stack"] = it
            }

            clickable(
                title = R.string.diagnostics_log_level,
            ) {
                summary = state.logLevel ?: "-"
            }.also {
                statusValues["log_level"] = it
            }

            clickable(
                title = R.string.diagnostics_dns_mode,
            ) {
                summary = state.dnsEnhancedMode ?: "-"
            }.also {
                statusValues["dns_mode"] = it
            }

            clickable(
                title = R.string.diagnostics_ipv6,
            ) {
                summary = state.ipv6 ?: "-"
            }.also {
                statusValues["ipv6"] = it
            }

            clickable(
                title = R.string.diagnostics_connections,
            ) {
                summary = state.connections.toString()
            }.also {
                statusValues["connections"] = it
            }

            category(R.string.diagnostics_actions)

            clickable(
                title = R.string.diagnostics_enable_debug,
                summary = R.string.diagnostics_enable_debug_summary,
                icon = R.drawable.ic_baseline_flash_on,
            ) {
                clicked {
                    requests.trySend(Request.EnableDebug)
                }
            }

            clickable(
                title = R.string.diagnostics_disable_debug,
                summary = R.string.diagnostics_disable_debug_summary,
                icon = R.drawable.ic_baseline_stop,
            ) {
                clicked {
                    requests.trySend(Request.DisableDebug)
                }
            }

            clickable(
                title = R.string.diagnostics_export,
                summary = R.string.diagnostics_export_summary,
                icon = R.drawable.ic_baseline_save,
            ) {
                clicked {
                    requests.trySend(Request.Export)
                }
            }
        }

        binding.content.addView(screen.root)
    }
}
