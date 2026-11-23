package universe.agentic.clash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import nota.ui.theme.shellPressBackground
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

data class NetworkSettingsUiState(
    val enableVpn: Boolean = true,
    val bypassPrivateNetwork: Boolean = true,
    val dnsHijacking: Boolean = true,
    val allowBypass: Boolean = true,
    val allowIpv6: Boolean = false,
    val systemProxy: Boolean = true,
    val showSystemProxy: Boolean = false,
    val tunStackMode: String = "mips",
    val running: Boolean = false,
) {
    // Master switch is disabled only while the tunnel is running.
    val vpnSwitchEnabled: Boolean get() = !running

    // vpnDependencies, not the app list. Also off when enableVpn is false.
    val vpnDependenciesEnabled: Boolean get() = vpnSwitchEnabled && enableVpn
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
) {
    var tunOpen by remember { mutableStateOf(false) }
    val tunValues = listOf("system", "gvisor", "mixed", "mips")
    val tunLabels = listOf(
        stringResource(R.string.tun_stack_system),
        stringResource(R.string.tun_stack_gvisor),
        stringResource(R.string.tun_stack_mixed),
        stringResource(R.string.tun_stack_mips),
    )
    val tunIndex = tunValues.indexOf(state.tunStackMode)
    val count = if (state.showSystemProxy) 6 else 5
    val tunRow = if (state.showSystemProxy) 5 else 4
    val vpnTitle = stringResource(R.string.route_system_traffic)
    val vpnSummary = stringResource(R.string.routing_via_vpn_service)
    val dependenciesEnabled = state.vpnDependenciesEnabled

    IosScreenScaffold(insets = insets, title = title, onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = IosContentTop, bottom = 24.dp),
        ) {
            if (state.running) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .shellPressBackground(interactionSource = interaction)
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
            Spacer(Modifier.height(12.dp))
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
            val tunLabel = tunLabels.getOrNull(tunIndex) ?: state.tunStackMode
            Box {
                IosPreferenceRow(
                    row = IosDetail(
                        title = stringResource(R.string.tun_stack_mode),
                        value = tunLabel.takeIf { it.fitsTrailingValue() },
                        subtitle = tunLabel.takeUnless { it.fitsTrailingValue() },
                        enabled = dependenciesEnabled,
                        disclosure = false,
                        onClick = if (dependenciesEnabled) {
                            { tunOpen = true }
                        } else {
                            null
                        },
                    ),
                    index = tunRow,
                    count = count,
                )
                IosPopupMenu(
                    expanded = tunOpen,
                    onDismiss = { tunOpen = false },
                    actions = tunLabels.mapIndexed { index, label ->
                        IosAction(
                            title = label,
                            marked = index == tunIndex,
                            onClick = { onTunStack(tunValues[index]) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = IosType.section,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}
