package com.github.kr328.clash.design.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.ProxyDesign
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SpinnerEntry
import top.yukonga.miuix.kmp.extra.SuperSpinner
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

// 延迟显示组件
@Composable
fun DelayIndicator(
    delay: Int?,
    textStyle: androidx.compose.ui.text.TextStyle = MiuixTheme.textStyles.footnote1
) {
    delay?.takeIf { it.isValidDelay() }?.let { validDelay ->
        Text(
            text = "${validDelay}ms",
            style = textStyle,
            color = getDelayColor(validDelay)
        )
    }
}

// 代理信息组件（双列布局）
@Composable
fun ProxyInfoDualColumn(
    proxy: Proxy,
    delay: Int?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .padding(
                horizontal = ProxyCardConstants.CONTENT_PADDING_HORIZONTAL,
                vertical = ProxyCardConstants.CONTENT_PADDING_VERTICAL
            )
    ) {
        Text(
            text = proxy.name.truncateIfNeeded(),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(ProxyCardConstants.TEXT_SPACING))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = proxy.type.toString(),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            DelayIndicator(delay)
        }
    }
}

// 代理信息组件（单列布局）
@Composable
fun ProxyInfoSingleColumn(
    proxy: Proxy,
    delay: Int?
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .padding(
                horizontal = ProxyCardConstants.SINGLE_CONTENT_PADDING_HORIZONTAL,
                vertical = ProxyCardConstants.SINGLE_CONTENT_PADDING_VERTICAL
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = proxy.name.truncateIfNeeded(),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(ProxyCardConstants.TEXT_SPACING))
            Text(
                text = proxy.type.toString(),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DelayIndicator(delay, MiuixTheme.textStyles.body2)
    }
}

// 卡片包装器组件
@Composable
fun ProxyCardWrapper(
    isSelected: Boolean,
    layoutType: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val height = if (layoutType == 1) ProxyCardConstants.DUAL_COLUMN_HEIGHT else ProxyCardConstants.SINGLE_COLUMN_HEIGHT

    // 统一的外部容器结构，避免选中切换时位置移动
    Box(
        modifier = modifier
            .height(height)
            .border(
                width = ProxyCardConstants.BORDER_WIDTH,
                color = if (isSelected) MiuixTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(ProxyCardConstants.CARD_CORNER_RADIUS)
            )
            .clip(RoundedCornerShape(ProxyCardConstants.CARD_CORNER_RADIUS))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(ProxyCardConstants.BORDER_WIDTH),
            insideMargin = PaddingValues(0.dp),
            onClick = onClick
        ) {
            content()
        }
    }
}

// 代理卡片组件
@Composable
fun ProxyItemCard(
    proxy: Proxy,
    isSelected: Boolean,
    isSelectable: Boolean,
    delay: Int? = null,
    layoutType: Int = 0, // 0=单列，1=双列
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ProxyCardWrapper(
        isSelected = isSelected,
        layoutType = layoutType,
        onClick = { if (isSelectable) onClick() },
        modifier = modifier
    ) {
        if (layoutType == 1) {
            ProxyInfoDualColumn(proxy = proxy, delay = delay)
        } else {
            ProxyInfoSingleColumn(proxy = proxy, delay = delay)
        }
    }
}

// 双列行组件
@Composable
fun DualColumnRow(
    leftProxy: Proxy?,
    rightProxy: Proxy?,
    selectedProxyName: String?,
    isSelectable: Boolean,
    delayMap: Map<String, Int>,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ProxyCardConstants.ITEM_SPACING)
    ) {
        if (leftProxy != null) {
            val isSelected = selectedProxyName == leftProxy.name
            val leftDelay = delayMap[leftProxy.name] ?: leftProxy.delay

            ProxyItemCard(
                proxy = leftProxy,
                isSelected = isSelected,
                isSelectable = isSelectable,
                delay = leftDelay,
                layoutType = 1,
                onClick = {
                    if (!isSelected) {
                        onSelect(leftProxy.name)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (rightProxy != null) {
            val isSelected = selectedProxyName == rightProxy.name
            val rightDelay = delayMap[rightProxy.name] ?: rightProxy.delay

            ProxyItemCard(
                proxy = rightProxy,
                isSelected = isSelected,
                isSelectable = isSelectable,
                delay = rightDelay,
                layoutType = 1,
                onClick = {
                    if (!isSelected) {
                        onSelect(rightProxy.name)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

// 模式选择卡片组件
@Composable
fun ModeSelectionCard(
    proxyDesign: ProxyDesign
) {
    Card {
        val spinnerEntries = remember {
            proxyDesign.modes.map { mode ->
                when (mode) {
                    TunnelState.Mode.Direct -> MLang.mode_direct
                    TunnelState.Mode.Global -> MLang.mode_global
                    TunnelState.Mode.Rule -> MLang.mode_rule
                    TunnelState.Mode.Script -> MLang.mode_script
                }
            }
        }
        val selectedIndex by remember(proxyDesign.currentMode) {
            derivedStateOf { proxyDesign.modes.indexOf(proxyDesign.currentMode) }
        }

        SuperSpinner(
            title = MLang.mode_title,
            items = spinnerEntries.map { SpinnerEntry(title = it) },
            selectedIndex = selectedIndex,
            onSelectedIndexChange = { newIndex ->
                val newMode = proxyDesign.modes.getOrNull(newIndex) ?: return@SuperSpinner
                proxyDesign.currentMode = newMode
                proxyDesign.requests.trySend(ProxyDesign.Request.PatchMode(newMode))
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmptyStateContent() {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = MLang.proxy_page_title,
                scrollBehavior = scrollBehavior
            )
        },
        content = { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = MLang.empty_text,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = MLang.empty_hint,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    )
}


