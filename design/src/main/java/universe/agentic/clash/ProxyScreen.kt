package universe.agentic.clash

import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import nota.ui.theme.ShellShapes
import nota.ui.theme.shellPressBackground
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets

data class ProxyPageUi(
    val name: String,
    val type: String,
    val now: String,
    val proxies: List<Proxy>,
    val selectable: Boolean,
    val loaded: Boolean,
    val loading: Boolean,
    val urlTesting: Boolean,
)

data class ProxyLinkUi(
    val name: String,
    val loaded: Boolean,
    val now: String,
)

@Composable
fun ProxyScreen(
    insets: Insets,
    title: String,
    groupNames: List<String>,
    current: Int,
    page: ProxyPageUi?,
    links: List<ProxyLinkUi>,
    sort: ProxySort,
    excludeNotSelectable: Boolean,
    onBack: () -> Unit,
    onPickGroup: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onUrlTest: () -> Unit,
    onSort: (ProxySort) -> Unit,
    onExclude: (Boolean) -> Unit,
    providers: List<ProviderRowUi>,
    providersNow: Long,
    providersLoaded: Boolean,
    providersUnavailable: Boolean,
    onUpdateProvider: (Int) -> Unit,
    onUpdateAllProviders: () -> Unit,
    embedded: Boolean = false,
    sectionJump: Int = 0,
    sectionIndex: Int = 0,
) {
    var picking by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(0) }
    LaunchedEffect(sectionJump) {
        if (sectionJump != 0) section = sectionIndex
    }
    var query by remember(current) { mutableStateOf("") }
    var groupQuery by remember { mutableStateOf("") }
    val activity = LocalContext.current as? AppCompatActivity
    DisposableEffect(picking, activity) {
        if (activity == null || !picking) {
            onDispose { }
        } else {
            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    picking = false
                }
            }
            activity.onBackPressedDispatcher.addCallback(callback)
            onDispose { callback.remove() }
        }
    }
    val testing = page?.urlTesting == true
    val hasGroups = groupNames.isNotEmpty()

    IosScreenScaffold(
        insets = insets,
        title = if (picking) stringResource(R.string.select_proxy_group) else title,
        onBack = {
            if (picking) picking = false else onBack()
        },
        embedded = embedded,
        showBack = if (picking) true else null,
        trailing = if (picking) {
            null
        } else {
            {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (section == 0 && hasGroups) {
                    Box {
                        IosNavIcon(
                            icon = painterResource(R.drawable.ic_baseline_more_vert),
                            description = stringResource(R.string.more),
                            onClick = { menu = true },
                        )
                        ProxyOverflowMenu(
                            expanded = menu,
                            sort = sort,
                            excludeNotSelectable = excludeNotSelectable,
                            onSort = {
                                menu = false
                                onSort(it)
                            },
                            onExclude = {
                                menu = false
                                onExclude(it)
                            },
                            onDismiss = { menu = false },
                        )
                    }
                    IosNavIcon(
                        icon = painterResource(
                            if (grid) R.drawable.ic_baseline_view_list else R.drawable.ic_baseline_apps,
                        ),
                        description = stringResource(
                            if (grid) R.string.proxy_list_view else R.string.proxy_grid_view,
                        ),
                        plate = false,
                        onClick = { grid = !grid },
                    )
                    IosRefreshIcon(
                        icon = painterResource(R.drawable.ic_baseline_flash_on),
                        description = stringResource(R.string.delay_test),
                        busy = testing,
                        onClick = onUrlTest,
                    )
                } else if (section != 0 && providersLoaded && providers.isNotEmpty() && !providersUnavailable) {
                    IosRefreshIcon(
                        icon = painterResource(R.drawable.ic_baseline_update),
                        description = stringResource(R.string.update_all),
                        busy = providers.any { it.updating },
                        onClick = onUpdateAllProviders,
                    )
                }
            }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(IosContentTop))
            IosSegmentedControl(
                items = listOf(
                    stringResource(R.string.proxy_section_nodes),
                    stringResource(R.string.providers),
                ),
                    selected = section,
                    onSelect = {
                        section = it
                        if (it != 0) picking = false
                    },
            )
            Spacer(Modifier.height(IosContentTop))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (section == 1) {
                    ProviderPane(
                        rows = providers,
                        now = providersNow,
                        loaded = providersLoaded,
                        unavailable = providersUnavailable,
                        onUpdate = onUpdateProvider,
                    )
                } else when {
            !hasGroups -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    IosEmptyState(message = stringResource(R.string.proxy_empty_tips))
                }
            }
            page == null -> IosProgressRow(stringResource(R.string.loading))
            picking -> {
                GroupPicker(
                    names = groupNames,
                    current = current,
                    query = groupQuery,
                    onQuery = { groupQuery = it },
                    onPick = { index ->
                        picking = false
                        groupQuery = ""
                        onPickGroup(index)
                    },
                )
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    val typeLabel = page.type.takeIf { page.loaded && it.isNotEmpty() }
                    val nowLabel = page.now.takeIf { page.loaded && it.isNotEmpty() }
                    val nowSide = nowLabel?.let { sideValue(it) }
                    val nowBelow = if (nowLabel != null && nowSide == null) nowLabel else null
                    IosPreferenceRow(
                        row = IosDetail(
                            title = page.name,
                            subtitle = when {
                                typeLabel != null && nowBelow != null -> "$typeLabel\n$nowBelow"
                                nowBelow != null -> nowBelow
                                else -> typeLabel
                            },
                            value = nowSide,
                            onClick = if (groupNames.size > 1) {
                                {
                                    groupQuery = ""
                                    picking = true
                                }
                            } else {
                                null
                            },
                        ),
                    )
                    if (groupNames.size > 1) {
                        GroupChips(
                            names = groupNames,
                            current = current,
                            onPick = onPickGroup,
                        )
                    }
                    IosSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.search),
                    )
                    if (page.loading || !page.loaded) {
                        IosProgressRow(stringResource(R.string.loading))
                    } else {
                        val shown = page.proxies.filter { it.matches(query) }
                        if (shown.isEmpty()) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                IosEmptyState(
                                    message = stringResource(
                                        if (query.isEmpty()) R.string.list_empty else R.string.search_empty,
                                    ),
                                )
                            }
                        } else if (grid) {
                            val colors = IosTheme.colors
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                gridItems(shown) { proxy ->
                                    ProxyNodeCard(
                                        proxy = proxy,
                                        subtitle = proxySubtitle(proxy, links),
                                        selected = proxy.name == page.now,
                                        delayColor = delayTone(proxy.delay, colors),
                                        onClick = if (page.selectable) {
                                            { onSelect(proxy.name) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        } else {
                            val colors = IosTheme.colors
                            LazyColumn(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                                    .clip(ShellShapes.Container)
                                    .background(colors.grouped)
                            ) {
                                items(count = shown.size) { index ->
                                    val proxy = shown[index]
                                    IosListRow(
                                        row = IosDetail(
                                            title = if (proxy.isGroup) proxy.name else proxy.title,
                                            subtitle = proxySubtitle(proxy, links),
                                            value = delayText(proxy.delay),
                                            valueColor = delayTone(proxy.delay, colors),
                                            selected = proxy.name == page.now,
                                            exclusive = true,
                                            emphasized = proxy.name == page.now,
                                            disclosure = false,
                                            onClick = if (page.selectable) {
                                                { onSelect(proxy.name) }
                                            } else {
                                                null
                                            },
                                        ),
                                        showDivider = index < shown.lastIndex,
                                    )
                                }
                            }
                        }
                    }
                }
            }
                }
            }
        }
    }

}

@Composable
private fun ProviderPane(
    rows: List<ProviderRowUi>,
    now: Long,
    loaded: Boolean,
    unavailable: Boolean,
    onUpdate: (Int) -> Unit,
) {
    when {
        unavailable -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                IosEmptyState(message = stringResource(R.string.unavailable))
            }
        }
        !loaded -> IosProgressRow(stringResource(R.string.loading))
        else -> ProviderSections(
            rows = rows,
            now = now,
            onUpdate = onUpdate,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun GroupPicker(
    names: List<String>,
    current: Int,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (Int) -> Unit,
) {
    val filtered = names.mapIndexed { index, name -> index to name }
        .filter { (_, name) -> query.isEmpty() || name.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        IosSearchField(
            value = query,
            onValueChange = onQuery,
            placeholder = stringResource(R.string.search),
        )
        if (query.isNotEmpty() && filtered.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IosEmptyState(message = stringResource(R.string.search_empty))
            }
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .clip(ShellShapes.Container)
                    .background(IosTheme.colors.grouped)
            ) {
                items(count = filtered.size) { index ->
                    val (groupIndex, name) = filtered[index]
                    IosListRow(
                        row = IosDetail(
                            title = name,
                            selected = groupIndex == current,
                            exclusive = true,
                            emphasized = groupIndex == current,
                            disclosure = false,
                            onClick = { onPick(groupIndex) },
                        ),
                        showDivider = index < filtered.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProxyOverflowMenu(
    expanded: Boolean,
    sort: ProxySort,
    excludeNotSelectable: Boolean,
    onSort: (ProxySort) -> Unit,
    onExclude: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        ProxySort.Default to stringResource(R.string.default_),
        ProxySort.Title to stringResource(R.string.name),
        ProxySort.Delay to stringResource(R.string.delay),
    )
    IosPopupMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        groups = listOf(
            IosMenuGroup(
                title = stringResource(R.string.sort),
                actions = options.map { (value, label) ->
                    IosAction(title = label, marked = sort == value, onClick = { onSort(value) })
                },
            ),
            IosMenuGroup(
                title = stringResource(R.string.filter),
                actions = listOf(
                    IosAction(
                        title = stringResource(R.string.not_selectable),
                        marked = excludeNotSelectable,
                        onClick = { onExclude(!excludeNotSelectable) },
                    ),
                ),
            ),
        ),
    )
}

/** Right-side value is one line and at most 160dp. A long node name stays under the title. */
private fun sideValue(text: String): String? = text.takeIf { it.fitsTrailingValue() }

private fun Proxy.matches(query: String): Boolean {
    if (query.isEmpty()) return true
    return name.contains(query, ignoreCase = true) || title.contains(query, ignoreCase = true)
}

private fun proxySubtitle(proxy: Proxy, links: List<ProxyLinkUi>): String {
    if (!proxy.isGroup) return proxy.subtitle
    val link = links.find { it.name == proxy.name } ?: return proxy.type
    if (!link.loaded) return proxy.type
    return "%s(%s)".format(proxy.type, link.now.ifEmpty { "*" })
}

private fun delayText(delay: Int): String? {
    if (delay !in 0..Short.MAX_VALUE) return null
    return if (delay == 0) "0" else "$delay ms"
}

@Composable
private fun GroupChips(
    names: List<String>,
    current: Int,
    onPick: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = names.size) { index ->
            IosChip(
                text = names[index],
                selected = index == current,
                onClick = { onPick(index) },
            )
        }
    }
}

@Composable
private fun ProxyNodeCard(
    proxy: Proxy,
    subtitle: String,
    selected: Boolean,
    delayColor: Color?,
    onClick: (() -> Unit)?,
) {
    val colors = IosTheme.colors
    val interaction = remember(proxy.name) { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(ShellShapes.Container)
            .background(colors.grouped)
            .background(if (selected) colors.label.copy(alpha = 0.12f) else Color.Transparent)
            .shellPressBackground(
                ShellShapes.Container,
                enabled = onClick != null,
                interactionSource = interaction,
            )
            .clickable(
                enabled = onClick != null,
                interactionSource = interaction,
                indication = null,
                onClick = { onClick?.invoke() },
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = if (proxy.isGroup) proxy.name else proxy.title,
                style = IosType.headline.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = colors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            IosChoiceMark(selected = selected)
        }
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = IosType.caption2,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (proxy.type.isNotEmpty()) {
                IosStatusPill(proxy.type, colors.secondaryLabel)
            }
            Spacer(Modifier.weight(1f))
            val delay = delayText(proxy.delay)
            if (delay != null) {
                Text(
                    text = delay,
                    style = IosType.caption1,
                    color = delayColor ?: colors.secondaryLabel,
                )
            }
        }
    }
}
