package universe.agentic.clash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import nota.ui.theme.ShellShapes
import nota.ui.theme.shellPressBackground
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

data class QuickNodeUi(
    val name: String,
    val type: String,
    val delay: Int,
)

data class HomeUiState(
    val running: Boolean = false,
    val forwarded: String = "",
    val mode: TunnelState.Mode = TunnelState.Mode.Rule,
    val profileName: String? = null,
    val appsStrategyName: String? = null,
    val currentNode: String? = null,
    val currentGroup: String? = null,
    val currentDelay: Int = -1,
    val nodeSelectable: Boolean = false,
    val quickNodes: List<QuickNodeUi> = emptyList(),
    val traffic: TrafficRates = TrafficRates(),
    val uptime: String = "",
    val webdavDetail: String = "",
    val filesDetail: String = "",
    val providerCount: Int = -1,
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
    onSelectNode: (group: String, name: String) -> Unit,
    onProviders: () -> Unit,
    onLogs: () -> Unit,
    onReload: () -> Unit,
    onWebdav: () -> Unit,
    onFiles: () -> Unit,
) {
    val colors = IosTheme.colors
    val density = LocalDensity.current
    val top = with(density) { insets.top.toDp() }
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
    var switching by remember { mutableStateOf(false) }
    val modeLabel = modes.firstOrNull { it.first == state.mode }?.second
        ?: if (state.mode == TunnelState.Mode.Script) scriptShort else ""
    val chooseProfile = !state.running && state.profileName == null
    val providerValue = when {
        state.providerCount < 0 -> stringResource(R.string.proxy_after_connect)
        state.providerCount == 0 -> stringResource(R.string.list_empty)
        else -> state.providerCount.toString()
    }
    val proxyValue = if (state.running) {
        modes.firstOrNull { it.first == state.mode }?.second
            ?: if (state.mode == TunnelState.Mode.Script) scriptShort else stringResource(R.string.mode)
    } else {
        stringResource(R.string.proxy_after_connect)
    }
    val tiles = listOf(
        HomeTile(
            title = stringResource(R.string.apps_strategy),
            value = state.appsStrategyName ?: unset,
            icon = R.drawable.ic_baseline_apps,
            onClick = onApps,
        ),
        HomeTile(
            title = stringResource(R.string.webdav_sync),
            value = state.webdavDetail.ifEmpty { unset },
            icon = R.drawable.ic_baseline_cloud_upload,
            onClick = onWebdav,
        ),
        HomeTile(
            title = stringResource(R.string.providers),
            value = providerValue,
            icon = R.drawable.ic_baseline_cloud_download,
            onClick = onProviders,
        ),
        HomeTile(
            title = stringResource(R.string.home_files_geo),
            value = state.filesDetail.ifEmpty { unset },
            icon = R.drawable.ic_baseline_assignment,
            onClick = onFiles,
        ),
        HomeTile(
            title = stringResource(R.string.profile),
            value = state.profileName ?: unset,
            icon = R.drawable.ic_outline_folder,
            onClick = onProfiles,
        ),
        HomeTile(
            title = stringResource(R.string.proxy),
            value = proxyValue,
            icon = R.drawable.ic_baseline_vpn_lock,
            enabled = state.running,
            onClick = { onProxy(null) },
        ),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(start = start, top = top, end = end),
    ) {
    Box(Modifier.fillMaxWidth().height(IosTabTitleHeight)) {
        Text(
            text = stringResource(R.string.application_name),
            style = IosType.title2,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp, end = 104.dp),
        )
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IosIconButton(
                icon = R.drawable.ic_outline_article,
                description = stringResource(R.string.logs),
                onClick = onLogs,
            )
            IosRefreshIcon(
                icon = painterResource(R.drawable.ic_baseline_sync),
                description = stringResource(R.string.kernel_reload),
                onClick = onReload,
            )
        }
    }
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(top = IosContentTop, bottom = 16.dp),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(ShellShapes.Container)
                .background(colors.grouped)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BreathingDot(running = state.running)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(if (state.running) R.string.running else R.string.stopped),
                        style = IosType.title3,
                        color = colors.label,
                    )
                }
                if (state.running && state.uptime.isNotEmpty()) {
                    Text(
                        text = state.uptime,
                        style = IosType.footnote,
                        color = colors.secondaryLabel,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PowerButton(
                running = state.running,
                onClick = if (chooseProfile) onProfiles else onToggle,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    when {
                        state.running -> R.string.disconnect
                        chooseProfile -> R.string.select_profile
                        else -> R.string.connect
                    },
                ),
                style = IosType.headline,
                color = colors.label,
            )
            val hint = when {
                chooseProfile -> stringResource(R.string.no_profile_selected)
                !state.profileName.isNullOrEmpty() && modeLabel.isNotEmpty() ->
                    "${state.profileName} · $modeLabel"
                !state.profileName.isNullOrEmpty() -> state.profileName
                else -> modeLabel
            }
            if (!hint.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = hint,
                    style = IosType.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            IosSegmentedControl(
                items = modes.map { it.second },
                selected = selected,
                onSelect = { index ->
                    val mode = modes[index].first
                    if (mode != state.mode) onMode(mode)
                },
                horizontalInset = 0.dp,
            )
            if (modeSummary.isNotEmpty() && state.mode != TunnelState.Mode.Script) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = modeSummary,
                    style = IosType.footnote,
                    color = colors.secondaryLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.mode_session_hint),
                style = IosType.footnote,
                color = colors.tertiaryLabel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.running) {
            val outboundName = state.currentNode?.takeIf { it.isNotEmpty() }
            if (outboundName != null) {
                Spacer(Modifier.height(12.dp))
                OutboundCard(
                    name = outboundName,
                    group = state.currentGroup?.takeIf { it.isNotEmpty() && it != outboundName },
                    delay = state.currentDelay,
                    onClick = {
                        if (state.nodeSelectable && state.quickNodes.isNotEmpty()) {
                            switching = true
                        } else {
                            onProxy(state.currentGroup)
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TrafficCard(state)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.section_in_use),
            style = IosType.section,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        tiles.chunked(2).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { item ->
                    HomeShortcut(
                        title = item.title,
                        value = item.value,
                        icon = item.icon,
                        enabled = item.enabled,
                        onClick = item.onClick,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (switching) {
        QuickNodeSheet(
            nodes = state.quickNodes,
            current = state.currentNode,
            onSelect = { name ->
                val group = state.currentGroup
                switching = false
                if (group != null) onSelectNode(group, name)
            },
            onOpenAll = {
                switching = false
                onProxy(state.currentGroup)
            },
            onDismiss = { switching = false },
        )
    }
    }
}

private data class HomeTile(
    val title: String,
    val value: String,
    val icon: Int,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun HomeShortcut(
    title: String,
    value: String,
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(ShellShapes.Container)
            .background(colors.grouped)
            .shellPressBackground(ShellShapes.Container, enabled = enabled, interactionSource = interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IosLeadingTile(icon = icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = IosType.headline,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value.replaceFirst(" · ", "\n"),
                    style = IosType.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
        Text(text = value, style = IosType.title2, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BreathingDot(running: Boolean) {
    val colors = IosTheme.colors
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )
    Box(contentAlignment = Alignment.Center) {
        if (running) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colors.green.copy(alpha = 0.28f * pulse)),
            )
        }
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (running) colors.green.copy(alpha = pulse) else colors.tertiaryLabel),
        )
    }
}

@Composable
private fun PowerButton(running: Boolean, onClick: () -> Unit) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (running) colors.blue else colors.track)
                .shellPressBackground(
                    CircleShape,
                    interactionSource = interaction,
                    tint = if (running) Color.Black.copy(alpha = 0.10f) else null,
                )
                .semantics { role = Role.Button }
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PowerGlyph(color = if (running) colors.onBlue else colors.secondaryLabel)
        }
    }
}

@Composable
private fun PowerGlyph(color: Color) {
    Canvas(Modifier.size(32.dp)) {
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        val cx = size.width / 2f
        val cy = size.height / 2f + 2.dp.toPx()
        val radius = size.minDimension * 0.32f
        drawArc(
            color = color,
            startAngle = 300f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2f, radius * 2f),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(cx, size.height * 0.12f),
            end = Offset(cx, cy),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun OutboundCard(
    name: String,
    group: String?,
    delay: Int,
    onClick: () -> Unit,
) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val tone = delayTone(delay, colors)
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(ShellShapes.Container)
            .background(colors.grouped)
            .shellPressBackground(ShellShapes.Container, interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.outbound_label),
                style = IosType.caption1,
                color = colors.secondaryLabel,
            )
            if (tone != null) {
                IosStatusPill(text = "$delay ms", color = tone)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            style = IosType.title3,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!group.isNullOrEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = group,
                style = IosType.footnote,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TrafficCard(state: HomeUiState) {
    val colors = IosTheme.colors
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(ShellShapes.Container)
            .background(colors.grouped)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            TrafficStat(
                label = stringResource(R.string.upload),
                value = stringResource(R.string.per_second, state.traffic.upload.ifEmpty { "0 B" }),
                color = colors.green,
                modifier = Modifier.weight(1f),
            )
            TrafficStat(
                label = stringResource(R.string.download),
                value = stringResource(R.string.per_second, state.traffic.download.ifEmpty { "0 B" }),
                color = colors.blue,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.traffic.samples.size >= 2) {
            Spacer(Modifier.height(8.dp))
            TrafficSparkline(state.traffic.samples)
        }
        val totals = buildList {
            if (state.traffic.uploadTotal.isNotEmpty()) {
                add("${stringResource(R.string.upload)} ${state.traffic.uploadTotal}")
            }
            if (state.traffic.downloadTotal.isNotEmpty()) {
                add("${stringResource(R.string.download)} ${state.traffic.downloadTotal}")
            }
            if (state.forwarded.isNotEmpty()) {
                add(stringResource(R.string.format_traffic_forwarded, state.forwarded))
            }
        }
        if (totals.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = totals.joinToString(" · "),
                style = IosType.footnote,
                color = colors.tertiaryLabel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun QuickNodeSheet(
    nodes: List<QuickNodeUi>,
    current: String?,
    onSelect: (String) -> Unit,
    onOpenAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IosTheme.colors
    var query by remember { mutableStateOf("") }
    val shown = remember(nodes, query) {
        if (query.isEmpty()) {
            nodes
        } else {
            nodes.filter { node ->
                node.name.contains(query, ignoreCase = true) ||
                    node.type.contains(query, ignoreCase = true)
            }
        }
    }
    val nav = iosSheetBottom()
    IosSheetFrame(onDismiss = onDismiss, panel = { close ->
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
                    .clip(ShellShapes.SheetTop)
                    .background(colors.grouped)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(bottom = nav + 16.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.quick_switch_node),
                        style = IosType.headline,
                        color = colors.label,
                    )
                    IosIconButton(
                        icon = R.drawable.ic_baseline_close,
                        description = stringResource(R.string.cancel),
                        onClick = close,
                    )
                }
                IosSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search),
                    containerColor = colors.track,
                )
                if (shown.isEmpty()) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        IosEmptyState(message = stringResource(R.string.search_empty))
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        items(shown) { node ->
                            val selected = node.name == current
                            val tone = delayTone(node.delay, colors)
                            val interaction = remember(node.name) { MutableInteractionSource() }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(ShellShapes.Control)
                                    .background(if (selected) colors.label.copy(alpha = 0.12f) else Color.Transparent)
                                    .shellPressBackground(ShellShapes.Control, interactionSource = interaction)
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = null,
                                        onClick = { onSelect(node.name) },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = node.name,
                                        style = IosType.headline.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        ),
                                        color = colors.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (node.type.isNotEmpty()) {
                                        Text(
                                            text = node.type,
                                            style = IosType.caption1,
                                            color = colors.secondaryLabel,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (tone != null) {
                                    Text(
                                        text = "${node.delay} ms",
                                        style = IosType.footnote,
                                        color = tone,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                IosChoiceMark(selected = selected)
                            }
                        }
                    }
                }
                val openAll = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .shellPressBackground(interactionSource = openAll)
                        .clickable(interactionSource = openAll, indication = null, onClick = onOpenAll),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.open_all_nodes),
                        style = IosType.headline,
                        color = colors.blue,
                    )
                }
            }
    })
}
