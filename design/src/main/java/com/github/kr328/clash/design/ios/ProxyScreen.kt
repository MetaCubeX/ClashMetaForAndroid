package com.github.kr328.clash.design.ios

import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
) {
    var picking by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
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
        title = title,
        onBack = {
            if (picking) picking = false else onBack()
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IosNavIcon(
                    icon = painterResource(R.drawable.ic_baseline_more_vert),
                    description = stringResource(R.string.more),
                    onClick = { menu = true },
                )
                if (hasGroups && !testing && !picking) {
                    IosNavIcon(
                        icon = painterResource(R.drawable.ic_baseline_flash_on),
                        description = stringResource(R.string.delay_test),
                        onClick = onUrlTest,
                    )
                }
            }
        },
    ) {
        when {
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
                    if (testing) {
                        IosProgressRow(stringResource(R.string.delay_test))
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
                        } else {
                            LazyColumn(Modifier.weight(1f)) {
                                items(count = shown.size) { index ->
                                    val proxy = shown[index]
                                    IosListRow(
                                        row = IosDetail(
                                            title = if (proxy.isGroup) proxy.name else proxy.title,
                                            subtitle = proxySubtitle(proxy, links),
                                            value = delayText(proxy.delay),
                                            selected = proxy.name == page.now,
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

    if (menu) {
        ProxyMenuSheet(
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
            LazyColumn(Modifier.weight(1f)) {
                items(count = filtered.size) { index ->
                    val (groupIndex, name) = filtered[index]
                    IosListRow(
                        row = IosDetail(
                            title = name,
                            selected = groupIndex == current,
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
private fun ProxyMenuSheet(
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
    IosSheet(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.sort),
            style = IosType.footnote,
            color = IosTheme.colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        val dismiss = LocalIosSheetDismiss.current
        options.forEachIndexed { index, (value, label) ->
            IosListRow(
                row = IosDetail(
                    title = label,
                    selected = sort == value,
                    disclosure = false,
                    onClick = { dismiss { onSort(value) } },
                ),
                showDivider = index < options.lastIndex,
            )
        }
        Text(
            text = stringResource(R.string.filter),
            style = IosType.footnote,
            color = IosTheme.colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        IosSwitchRow(
            title = stringResource(R.string.not_selectable),
            summary = null,
            checked = excludeNotSelectable,
            onCheckedChange = { checked -> dismiss { onExclude(checked) } },
        )
    }
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
    return delay.toString()
}
