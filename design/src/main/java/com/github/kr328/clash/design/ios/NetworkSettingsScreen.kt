package com.github.kr328.clash.design.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.service.model.AccessControlMode

data class NetworkSettingsUiState(
    val enableVpn: Boolean = true,
    val bypassPrivateNetwork: Boolean = true,
    val dnsHijacking: Boolean = true,
    val allowBypass: Boolean = true,
    val allowIpv6: Boolean = false,
    val systemProxy: Boolean = true,
    val showSystemProxy: Boolean = false,
    val tunStackMode: String = "mips",
    val accessControlMode: AccessControlMode = AccessControlMode.AcceptAll,
    val running: Boolean = false,
    val activeStrategyName: String? = null,
) {
    // Master switch is disabled only while the tunnel is running.
    val vpnSwitchEnabled: Boolean get() = !running

    // vpnDependencies, not the app list. Also off when enableVpn is false.
    val vpnDependenciesEnabled: Boolean get() = vpnSwitchEnabled && enableVpn
}

private enum class NetworkOptionSheet {
    Tun,
    AccessMode,
}

@Composable
fun NetworkSettingsScreen(
    insets: Insets,
    title: String,
    state: NetworkSettingsUiState,
    onBack: () -> Unit,
    onEnableVpn: (Boolean) -> Unit,
    onVpnBlocked: () -> Unit,
    onBypassPrivateNetwork: (Boolean) -> Unit,
    onDnsHijacking: (Boolean) -> Unit,
    onAllowBypass: (Boolean) -> Unit,
    onAllowIpv6: (Boolean) -> Unit,
    onSystemProxy: (Boolean) -> Unit,
    onTunStack: (String) -> Unit,
    onAccessControlMode: (AccessControlMode) -> Unit,
    onAccessControlList: () -> Unit,
) {
    var sheet by remember { mutableStateOf<NetworkOptionSheet?>(null) }
    val tunValues = listOf("system", "gvisor", "mixed", "mips")
    val tunLabels = listOf(
        stringResource(R.string.tun_stack_system),
        stringResource(R.string.tun_stack_gvisor),
        stringResource(R.string.tun_stack_mixed),
        stringResource(R.string.tun_stack_mips),
    )
    val modes = AccessControlMode.values()
    val modeLabels = listOf(
        stringResource(R.string.allow_all_apps),
        stringResource(R.string.allow_selected_apps),
        stringResource(R.string.deny_selected_apps),
    )
    val tunIndex = tunValues.indexOf(state.tunStackMode)
    val modeIndex = modes.indexOf(state.accessControlMode)
    val accessControlNote = state.activeStrategyName?.let { name ->
        stringResource(R.string.access_control_idle, name)
    }
    val extra = if (state.showSystemProxy) 1 else 0
    val count = 7 + extra
    val tunRow = 4 + extra
    val modeRow = 5 + extra
    val packagesRow = 6 + extra
    val vpnTitle = stringResource(R.string.route_system_traffic)
    val vpnSummary = stringResource(R.string.routing_via_vpn_service)
    val dependenciesEnabled = state.vpnDependenciesEnabled

    IosScreenScaffold(insets = insets, title = title, onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            if (state.running) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onVpnBlocked,
                        ),
                ) {
                    IosSwitchRow(
                        title = vpnTitle,
                        summary = vpnSummary,
                        checked = state.enableVpn,
                        enabled = false,
                        index = 0,
                        count = 1,
                        onCheckedChange = {},
                    )
                }
            } else {
                IosSwitchRow(
                    title = vpnTitle,
                    summary = vpnSummary,
                    checked = state.enableVpn,
                    enabled = true,
                    index = 0,
                    count = 1,
                    onCheckedChange = onEnableVpn,
                )
            }
            Spacer(Modifier.height(22.dp))
            GroupLabel(stringResource(R.string.vpn_service_options))
            IosSwitchRow(
                title = stringResource(R.string.bypass_private_network),
                summary = stringResource(R.string.bypass_private_network_summary),
                checked = state.bypassPrivateNetwork,
                enabled = dependenciesEnabled,
                index = 0,
                count = count,
                onCheckedChange = onBypassPrivateNetwork,
            )
            IosSwitchRow(
                title = stringResource(R.string.dns_hijacking),
                summary = stringResource(R.string.dns_hijacking_summary),
                checked = state.dnsHijacking,
                enabled = dependenciesEnabled,
                index = 1,
                count = count,
                onCheckedChange = onDnsHijacking,
            )
            IosSwitchRow(
                title = stringResource(R.string.allow_bypass),
                summary = stringResource(R.string.allow_bypass_summary),
                checked = state.allowBypass,
                enabled = dependenciesEnabled,
                index = 2,
                count = count,
                onCheckedChange = onAllowBypass,
            )
            IosSwitchRow(
                title = stringResource(R.string.allow_ipv6),
                summary = stringResource(R.string.allow_ipv6_summary),
                checked = state.allowIpv6,
                enabled = dependenciesEnabled,
                index = 3,
                count = count,
                onCheckedChange = onAllowIpv6,
            )
            if (state.showSystemProxy) {
                IosSwitchRow(
                    title = stringResource(R.string.system_proxy),
                    summary = stringResource(R.string.system_proxy_summary),
                    checked = state.systemProxy,
                    enabled = dependenciesEnabled,
                    index = 4,
                    count = count,
                    onCheckedChange = onSystemProxy,
                )
            }
            IosPreferenceRow(
                row = IosDetail(
                    title = stringResource(R.string.tun_stack_mode),
                    subtitle = tunLabels.getOrNull(tunIndex) ?: state.tunStackMode,
                    enabled = dependenciesEnabled,
                    onClick = { sheet = NetworkOptionSheet.Tun },
                ),
                index = tunRow,
                count = count,
            )
            IosPreferenceRow(
                row = IosDetail(
                    title = stringResource(R.string.access_control_mode),
                    subtitle = modeLabels.getOrNull(modeIndex),
                    enabled = dependenciesEnabled,
                    onClick = { sheet = NetworkOptionSheet.AccessMode },
                ),
                index = modeRow,
                count = count,
            )
            IosPreferenceRow(
                row = IosDetail(
                    title = stringResource(R.string.access_control_packages),
                    subtitle = stringResource(R.string.access_control_packages_summary),
                    enabled = true,
                    onClick = onAccessControlList,
                ),
                index = packagesRow,
                count = count,
            )
            if (accessControlNote != null) {
                Text(
                    text = accessControlNote,
                    style = IosType.footnote,
                    color = IosTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
                )
            }
        }
    }

    when (sheet) {
        NetworkOptionSheet.Tun -> IosOptionSheet(
            title = stringResource(R.string.tun_stack_mode),
            options = tunLabels,
            selected = tunIndex,
            onSelect = { onTunStack(tunValues[it]) },
            onDismiss = { sheet = null },
        )
        NetworkOptionSheet.AccessMode -> IosOptionSheet(
            title = stringResource(R.string.access_control_mode),
            options = modeLabels,
            selected = modeIndex,
            onSelect = { onAccessControlMode(modes[it]) },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = IosType.footnote,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 6.dp),
    )
}
