package com.github.kr328.clash.design.screen

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.kr328.clash.design.NetworkSettingsDesign
import com.github.kr328.clash.design.components.SettingsRenderer
import com.github.kr328.clash.design.components.StandardListScreen
import com.github.kr328.clash.design.components.settingsList
import com.github.kr328.clash.design.theme.AppDimensions
import com.github.kr328.clash.design.util.finishActivity
import com.github.kr328.clash.service.model.AccessControlMode
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog

@Composable
fun NetworkSettingsScreen(design: NetworkSettingsDesign) {
    val ctx = LocalContext.current

    // 直接使用 Design 层的 DialogState，每次 running 变化时更新
    LaunchedEffect(design.running) {
        if (design.running) {
            design.warningDialogState.open()
        }
    }

    // 警告弹窗 - 使用 key 确保状态隔离
    key(design.warningDialogState.show) {
        if (design.warningDialogState.show) {
            val dialogShow = remember { mutableStateOf(true) }

            LaunchedEffect(dialogShow.value) {
                if (!dialogShow.value) {
                    design.warningDialogState.close()
                    ctx.finishActivity()
                }
            }

            SuperDialog(
                title = MLang.network_dialog_unavailable_title,
                show = dialogShow,
                onDismissRequest = { dialogShow.value = false }
            ) {
                TextButton(
                    text = MLang.action_ok,
                    onClick = { dialogShow.value = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // States
    var enableVpn by remember { mutableStateOf(design.uiStore.enableVpn) }
    var bypassPrivateNetwork by remember { mutableStateOf(design.srvStore.bypassPrivateNetwork) }
    var dnsHijacking by remember { mutableStateOf(design.srvStore.dnsHijacking) }
    var allowBypass by remember { mutableStateOf(design.srvStore.allowBypass) }
    var allowIpv6 by remember { mutableStateOf(design.srvStore.allowIpv6) }
    var systemProxy by remember { mutableStateOf(design.srvStore.systemProxy) }
    var tunStackMode by remember { mutableStateOf(design.srvStore.tunStackMode) }
    var accessControlMode by remember { mutableStateOf(design.srvStore.accessControlMode) }

    val vpnDependenciesEnabled = enableVpn && !design.running

    val sections = settingsList {
        section(MLang.section_vpn) {
            triState(MLang.route_traffic, enableVpn, { checked ->
                if (checked != null) {
                    enableVpn = checked
                }
                if (checked != null) {
                    design.uiStore.enableVpn = checked
                }
            }, MLang.route_traffic_summary, enabled = !design.running)
        }
        section(MLang.section_vpn_options) {
            listOf(
                Triple(MLang.bypass_private, MLang.bypass_private_summary, bypassPrivateNetwork to { v: Boolean ->
                    bypassPrivateNetwork = v
                    design.srvStore.bypassPrivateNetwork = v
                }),
                Triple(MLang.dns_hijacking, MLang.dns_hijacking_summary, dnsHijacking to { v: Boolean ->
                    dnsHijacking = v
                    design.srvStore.dnsHijacking = v
                }),
                Triple(MLang.allow_bypass, MLang.allow_bypass_summary, allowBypass to { v: Boolean ->
                    allowBypass = v
                    design.srvStore.allowBypass = v
                }),
                Triple(MLang.allow_ipv6, MLang.allow_ipv6_summary, allowIpv6 to { v: Boolean ->
                    allowIpv6 = v
                    design.srvStore.allowIpv6 = v
                })
            ).forEach { (title, summary, statePair) ->
                val (current, onChange) = statePair
                switch(title, current, onChange, summary = summary, enabled = vpnDependenciesEnabled)
            }
            if (Build.VERSION.SDK_INT >= 29) {
                switch(MLang.system_proxy, systemProxy, { v ->
                    systemProxy = v
                    design.srvStore.systemProxy = v
                }, summary = MLang.system_proxy_summary, enabled = vpnDependenciesEnabled)
            }
        }
        section(MLang.section_proxy_options) {
            dropdown(
                title = MLang.tun_stack,
                items = listOf(MLang.tun_stack_system, MLang.tun_stack_gvisor, MLang.tun_stack_mixed),
                selectedIndex = listOf("system", "gvisor", "mixed").indexOf(tunStackMode),
                onSelectedIndexChange = { idx ->
                    val newMode = listOf("system", "gvisor", "mixed")[idx]
                    tunStackMode = newMode
                    design.srvStore.tunStackMode = newMode
                },
                enabled = vpnDependenciesEnabled
            )
            dropdown(
                title = MLang.access_control_mode,
                items = listOf(
                    MLang.access_mode_allow_all,
                    MLang.access_mode_allow_selected,
                    MLang.access_mode_deny_selected
                ),
                selectedIndex = AccessControlMode.entries.indexOf(accessControlMode),
                onSelectedIndexChange = { idx ->
                    val newMode = AccessControlMode.entries[idx]
                    accessControlMode = newMode
                    design.srvStore.accessControlMode = newMode
                },
                enabled = vpnDependenciesEnabled
            )
            arrow(
                title = MLang.access_control_list,
                summary = MLang.access_control_list_summary,
                onClick = { design.request(NetworkSettingsDesign.Request.StartAccessControlList) },
                enabled = vpnDependenciesEnabled
            )
        }
    }

    StandardListScreen(
        title = MLang.network_page_title,
        onBack = { ctx.finishActivity() }
    ) {
        item {
            SettingsRenderer(sections)
            Spacer(modifier = Modifier.height(AppDimensions.spacing_xxl))
        }
    }
}

