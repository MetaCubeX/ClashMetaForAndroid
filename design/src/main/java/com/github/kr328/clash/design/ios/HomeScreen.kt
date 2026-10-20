package com.github.kr328.clash.design.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets

data class TrafficRates(
    val upload: String = "",
    val download: String = "",
    val uploadTotal: String = "",
    val downloadTotal: String = "",
    val samples: List<TrafficSample> = emptyList(),
)

data class HomeUiState(
    val running: Boolean = false,
    val forwarded: String = "",
    val mode: TunnelState.Mode = TunnelState.Mode.Rule,
    val profileName: String? = null,
    val appsStrategyName: String? = null,
    val hasProviders: Boolean = false,
    val currentNode: String? = null,
    val currentGroup: String? = null,
    val traffic: TrafficRates = TrafficRates(),
)

@Composable
fun HomeScreen(
    insets: Insets,
    state: HomeUiState,
    onToggle: () -> Unit,
    onMode: (TunnelState.Mode) -> Unit,
    onProfiles: () -> Unit,
    onApps: () -> Unit,
    onProxy: (String?) -> Unit,
    onProviders: () -> Unit,
    onLogs: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
) {
    val colors = IosTheme.colors
    val density = LocalDensity.current
    val top = with(density) { insets.top.toDp() }
    val bottom = with(density) { insets.bottom.toDp() }
    val start = with(density) { insets.start.toDp() }
    val end = with(density) { insets.end.toDp() }
    val unset = stringResource(R.string.not_selected)
    val modes = listOf(
        TunnelState.Mode.Rule to stringResource(R.string.mode_rule_short),
        TunnelState.Mode.Global to stringResource(R.string.mode_global_short),
        TunnelState.Mode.Direct to stringResource(R.string.mode_direct_short),
    )
    val scriptShort = stringResource(R.string.mode_script_short)
    val modeSummary = when (state.mode) {
        TunnelState.Mode.Rule -> stringResource(R.string.mode_rule_summary)
        TunnelState.Mode.Global -> stringResource(R.string.mode_global_summary)
        TunnelState.Mode.Direct -> stringResource(R.string.mode_direct_summary)
        TunnelState.Mode.Script -> stringResource(R.string.mode_script_summary)
    }
    val selected = modes.indexOfFirst { it.first == state.mode }
    val inUse = buildList {
        add(IosDetail(stringResource(R.string.profile), state.profileName ?: unset, onClick = onProfiles))
        add(IosDetail(stringResource(R.string.apps_strategy), state.appsStrategyName ?: unset, onClick = onApps))
        add(
            IosDetail(
                title = stringResource(R.string.proxy),
                value = if (state.running) {
                    modes.firstOrNull { it.first == state.mode }?.second
                        ?: if (state.mode == TunnelState.Mode.Script) {
                            scriptShort
                        } else {
                            stringResource(R.string.mode)
                        }
                } else {
                    stringResource(R.string.proxy_after_connect)
                },
                enabled = state.running,
                onClick = { onProxy(null) },
            ),
        )
        if (state.running && state.hasProviders) {
            add(IosDetail(stringResource(R.string.providers), onClick = onProviders))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(start = start, top = top, end = end, bottom = bottom)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.application_name),
            style = IosType.largeTitle,
            color = colors.label,
            modifier = Modifier.padding(start = 32.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        )
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.grouped)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(if (state.running) R.string.running else R.string.stopped),
                style = IosType.title2,
                color = if (state.running) colors.green else colors.label,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    state.running && state.forwarded.isNotEmpty() ->
                        stringResource(R.string.format_traffic_forwarded, state.forwarded)
                    state.running -> stringResource(R.string.running)
                    state.profileName == null -> stringResource(R.string.no_profile_selected)
                    else -> stringResource(R.string.tap_to_start)
                },
                style = IosType.subhead,
                color = colors.secondaryLabel,
            )
            Spacer(Modifier.height(16.dp))
            val chooseProfile = !state.running && state.profileName == null
            IosButton(
                text = stringResource(
                    when {
                        state.running -> R.string.disconnect
                        chooseProfile -> R.string.select_profile
                        else -> R.string.connect
                    },
                ),
                style = if (state.running) IosButtonStyle.Destructive else IosButtonStyle.Filled,
                onClick = if (chooseProfile) onProfiles else onToggle,
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.mode),
            style = IosType.footnote,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(start = 32.dp, bottom = 6.dp),
        )
        IosModePicker(
            labels = modes.map { it.second },
            selected = selected,
            onSelect = { index ->
                val mode = modes[index].first
                if (mode != state.mode) onMode(mode)
            },
        )
        if (modeSummary != null) {
            Text(
                text = modeSummary,
                style = IosType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 32.dp, top = 8.dp, end = 32.dp),
            )
        }
        Text(
            text = stringResource(R.string.mode_session_hint),
            style = IosType.footnote,
            color = colors.tertiaryLabel,
            modifier = Modifier.padding(
                start = 32.dp,
                top = if (modeSummary != null) 4.dp else 8.dp,
                end = 32.dp,
            ),
        )
        if (state.running) {
            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.traffic),
                style = IosType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 32.dp, bottom = 6.dp),
            )
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.grouped)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    TrafficStat(
                        label = stringResource(R.string.upload),
                        value = stringResource(R.string.per_second, state.traffic.upload.ifEmpty { "0 Bytes" }),
                        color = colors.green,
                        modifier = Modifier.weight(1f),
                    )
                    TrafficStat(
                        label = stringResource(R.string.download),
                        value = stringResource(R.string.per_second, state.traffic.download.ifEmpty { "0 Bytes" }),
                        color = colors.blue,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                TrafficSparkline(state.traffic.samples)
                if (state.traffic.uploadTotal.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${stringResource(R.string.upload)} ${state.traffic.uploadTotal} · ${stringResource(R.string.download)} ${state.traffic.downloadTotal}",
                        style = IosType.footnote,
                        color = colors.tertiaryLabel,
                    )
                }
            }
            if (!state.currentNode.isNullOrEmpty()) {
                Spacer(Modifier.height(22.dp))
                IosInsetGroup(
                    title = stringResource(R.string.current_node),
                    rows = listOf(
                        IosDetail(state.currentNode, onClick = { onProxy(state.currentGroup) }),
                    ),
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        IosInsetGroup(title = stringResource(R.string.section_in_use), rows = inUse)
        Spacer(Modifier.height(22.dp))
        IosInsetGroup(
            title = stringResource(R.string.section_tools),
            rows = listOf(
                IosDetail(stringResource(R.string.logs), onClick = onLogs),
                IosDetail(stringResource(R.string.settings), onClick = onSettings),
            ),
        )
        Spacer(Modifier.height(22.dp))
        IosInsetGroup(
            title = stringResource(R.string.section_other),
            rows = listOf(
                IosDetail(stringResource(R.string.help), onClick = onHelp),
                IosDetail(stringResource(R.string.about), onClick = onAbout),
            ),
        )
    }
}

@Composable
private fun TrafficStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(text = label, style = IosType.footnote, color = IosTheme.colors.secondaryLabel)
        Spacer(Modifier.height(2.dp))
        Text(text = value, style = IosType.headline, color = color, maxLines = 1)
    }
}
