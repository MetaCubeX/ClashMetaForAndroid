package com.github.kr328.clash.design.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.proxy.*
import com.github.kr328.clash.design.ui.Lightning
import com.github.kr328.clash.service.StatusProvider.Companion.currentProfile
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.DropdownImpl
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.NavigatorSwitch
import top.yukonga.miuix.kmp.icon.icons.useful.Order
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun ProxyScreen(
    proxyDesign: ProxyDesign,
    running: Boolean
) {
    val groupNames by remember { derivedStateOf { proxyDesign.groupNames } }
    val currentPage by remember(proxyDesign.groupNames) { derivedStateOf { proxyDesign.currentPage } }
    val currentGroup by remember(currentPage) { derivedStateOf { proxyDesign.proxyGroups[currentPage] } }
    val layoutType by remember { derivedStateOf { proxyDesign.proxyLayoutType } }
    val hasProxyData by remember { derivedStateOf { proxyDesign.proxyGroups.isNotEmpty() } }

    LaunchedEffect(Unit) {
        delay(100)
        if (groupNames.isNotEmpty()) {
            proxyDesign.requests.trySend(ProxyDesign.Request.ReloadAll)
        } else {
            proxyDesign.requests.trySend(ProxyDesign.Request.ReLaunch)
        }
    }

    if (!running && currentProfile == null) {
        EmptyStateContent()
    } else {
        ProxyPage(
            proxyDesign = proxyDesign,
            running = running
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyPage(
    proxyDesign: ProxyDesign,
    running: Boolean
) {
    val scrollBehavior = MiuixScrollBehavior()
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            delay(30000) // 每 30 秒检查一次
            proxyDesign.cleanupTimeoutTests()
            proxyDesign.cleanupExpiredCache()
        }
    }

    LaunchedEffect(proxyDesign.currentPage) {
        val currentPageIndex = proxyDesign.currentPage

        withTimeoutOrNull(2_000) {
            snapshotFlow { proxyDesign.proxyGroups[currentPageIndex] }
                .filter { it?.proxies?.isNotEmpty() == true }
                .first()
        }

        if (proxyDesign.proxyGroups[currentPageIndex]?.proxies?.isNotEmpty() == true) {
            proxyDesign.requestUrlTesting(currentPageIndex)
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) return@LaunchedEffect

        try {
            delay(500)

            proxyDesign.requests.trySend(ProxyDesign.Request.ReloadAll)

            val dataLoaded = withTimeoutOrNull(3_000) {
                snapshotFlow { proxyDesign.proxyGroups[proxyDesign.currentPage]?.proxies?.isNotEmpty() == true }
                    .filter { it }
                    .first()
            } != null

            var testingStarted = false
            if (dataLoaded) {
                coroutineScope.launch {
                    proxyDesign.startBackgroundDelayTest(proxyDesign.currentPage)
                }
                testingStarted = true
            }

            val progressAnimationTime = if (testingStarted) {
                val nodeCount = proxyDesign.proxyGroups[proxyDesign.currentPage]?.proxies?.size ?: 0
                when {
                    nodeCount <= 30 -> 2_000L
                    nodeCount <= 100 -> 3_000L
                    nodeCount <= 200 -> 4_000L
                    else -> 5_000L
                }
            } else {
                1_500L
            }

            val steps = 8
            val stepDelay = progressAnimationTime / steps
            repeat(steps) {
                delay(stepDelay)

            }

            delay(300)

        } finally {
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (running && proxyDesign.groupNames.isNotEmpty())
                    (proxyDesign.groupNames.getOrNull(proxyDesign.currentPage) ?: MLang.proxy_page_title)
                else MLang.proxy_page_title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    val showPopup = remember { mutableStateOf(false) }
                    var selectedIndex by remember { mutableStateOf(proxyDesign.currentPage) }
                    val items = proxyDesign.groupNames
                    IconButton(
                        modifier = Modifier.padding(start = 24.dp),
                        onClick = { showPopup.value = true }) {
                        Icon(MiuixIcons.Useful.NavigatorSwitch, contentDescription = MLang.action_switch_group)
                    }
                    ListPopup(
                        show = showPopup,
                        alignment = PopupPositionProvider.Align.Left,
                        onDismissRequest = { showPopup.value = false }
                    ) {
                        ListPopupColumn {
                            items.forEachIndexed { index, string ->
                                DropdownImpl(
                                    text = string,
                                    optionSize = items.size,
                                    isSelected = selectedIndex == index,
                                    onSelectedIndexChange = {
                                        selectedIndex = index
                                        proxyDesign.currentPage = index
                                        proxyDesign.uiStore.proxyLastGroup =
                                            proxyDesign.groupNames.getOrNull(index) ?: ""
                                        showPopup.value = false
                                    },
                                    index = index
                                )
                            }
                        }
                    }
                },
                actions = {
                    val menuExpanded = remember { mutableStateOf(false) }
                    IconButton(
                        modifier = Modifier.padding(end = 16.dp),
                        onClick = { proxyDesign.requests.trySend(ProxyDesign.Request.ReloadAll) }
                    ) {
                        Icon(
                            Lightning,
                            contentDescription = MLang.action_speed_test,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        modifier = Modifier.padding(end = 24.dp),
                        onClick = { menuExpanded.value = true },
                        holdDownState = menuExpanded.value
                    ) {
                        Icon(
                            MiuixIcons.Useful.Order,
                            contentDescription = MLang.action_more_actions,
                        )
                    }
                    ListPopup(
                        show = menuExpanded,
                        alignment = PopupPositionProvider.Align.Right,
                        onDismissRequest = { menuExpanded.value = false }
                    ) {
                        ListPopupColumn {
                            DropdownImpl(
                                text = if (proxyDesign.sortByDelay) MLang.menu_default_order else MLang.menu_sort_by_delay,
                                optionSize = 2,
                                isSelected = false,
                                onSelectedIndexChange = {
                                    proxyDesign.toggleSortByDelay()
                                    menuExpanded.value = false
                                },
                                index = 0
                            )
                            DropdownImpl(
                                text = if (proxyDesign.proxyLayoutType == 0) MLang.menu_dual_column else MLang.menu_single_column,
                                optionSize = 2,
                                isSelected = false,
                                onSelectedIndexChange = {
                                    proxyDesign.toggleProxyLayoutType()
                                    menuExpanded.value = false
                                },
                                index = 1
                            )
                        }
                    }
                }
            )
        },
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding() + 4.dp,
            start = paddingValues.calculateStartPadding(layoutDirection),
            end = paddingValues.calculateEndPadding(layoutDirection),
            bottom = paddingValues.calculateBottomPadding()
        )

        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            pullToRefreshState = pullToRefreshState,
            refreshTexts = listOf(
                MLang.pull_to_refresh,
                MLang.release_to_refresh,
                MLang.refreshing,
                MLang.refresh_complete
            ),
            contentPadding = contentPadding
        ) {
            val groupState = proxyDesign.proxyGroups[proxyDesign.currentPage]

            if (groupState == null) {
                LaunchedEffect(proxyDesign.currentPage) {
                    delay(200)
                    proxyDesign.requests.trySend(ProxyDesign.Request.Reload(proxyDesign.currentPage))
                }
            }

            ProxyGroupContent(
                proxyDesign = proxyDesign,
                groupState = groupState,
                onSelect = { name ->
                    proxyDesign.updateProxySelection(proxyDesign.currentPage, name)
                    proxyDesign.requests.trySend(ProxyDesign.Request.Select(proxyDesign.currentPage, name))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = contentPadding,
                sortByDelay = proxyDesign.sortByDelay,
                layoutType = proxyDesign.proxyLayoutType,
                running = running
            )
        }
    }
}

@Composable
fun ProxyGroupContent(
    proxyDesign: ProxyDesign,
    groupState: ProxyGroupState?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    sortByDelay: Boolean = false,
    layoutType: Int = 0, // 0=单列，1=双列
    running: Boolean = true
) {
    val selectedProxyName = groupState?.parent?.now
    val isSelectable = groupState?.selectable ?: false
    val delayMap = groupState?.links?.mapValues { it.value.delay } ?: emptyMap()

    val sortedProxies = remember(groupState?.proxies, sortByDelay) {
        val proxies = groupState?.proxies ?: emptyList()
        if (proxies.isEmpty() || !sortByDelay || groupState?.testingUpdatedDelays == true) {
            proxies
        } else {
            proxies.sortedWith(compareBy { proxy ->
                val linkDelay = groupState?.links?.get(proxy.name)?.delay
                val pDelay = proxy.delay
                when {
                    linkDelay != null && linkDelay > 0 -> linkDelay
                    pDelay > 0 -> pDelay
                    else -> Int.MAX_VALUE
                }
            })
        }
    }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ProxyCardConstants.ITEM_SPACING),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding()
        )
    ) {
        item {
            ModeSelectionCard(proxyDesign = proxyDesign)
        }

        if (layoutType == 1) {
            val rowCount = (sortedProxies.size + 1) / 2
            items(rowCount) { rowIdx ->
                val leftIdx = rowIdx * 2
                val rightIdx = leftIdx + 1
                val leftProxy = sortedProxies.getOrNull(leftIdx)
                val rightProxy = sortedProxies.getOrNull(rightIdx)

                DualColumnRow(
                    leftProxy = leftProxy,
                    rightProxy = rightProxy,
                    selectedProxyName = selectedProxyName,
                    isSelectable = isSelectable,
                    delayMap = delayMap,
                    onSelect = onSelect
                )
            }
        } else {
            items(sortedProxies, key = { it.name }) { proxy ->
                val isSelected = selectedProxyName == proxy.name
                val proxyDelay = delayMap[proxy.name] ?: proxy.delay

                ProxyItemCard(
                    proxy = proxy,
                    isSelected = isSelected,
                    isSelectable = isSelectable,
                    delay = proxyDelay,
                    layoutType = 0,
                    onClick = {
                        if (!isSelected) {
                            onSelect(proxy.name)
                        }
                    }
                )
            }
        }
    }
}

