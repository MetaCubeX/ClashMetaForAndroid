package com.github.kr328.clash.design.proxy

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.model.ProxyState
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 常量定义
object ProxyCardConstants {
    val SINGLE_COLUMN_HEIGHT = 78.dp
    val DUAL_COLUMN_HEIGHT = 72.dp
    val BORDER_WIDTH = 1.dp
    val CARD_CORNER_RADIUS = 16.dp
    val INNER_CORNER_RADIUS = 16.dp
    val CONTENT_PADDING_HORIZONTAL = 12.dp
    val CONTENT_PADDING_VERTICAL = 10.dp
    val SINGLE_CONTENT_PADDING_HORIZONTAL = 20.dp
    val SINGLE_CONTENT_PADDING_VERTICAL = 16.dp
    val ITEM_SPACING = 8.dp
    val TEXT_SPACING = 4.dp

    // 延迟颜色常量
    const val COLOR_DELAY_FAST = 0xFF2FCD73
    const val COLOR_DELAY_MEDIUM = 0xFFDE764D

    // 代理相关常量
    const val DELAY_MIN = 0
    const val DELAY_MAX = 1000
    const val DELAY_FAST_THRESHOLD = 300
    const val DELAY_MEDIUM_THRESHOLD = 1000
    const val MAX_NAME_LENGTH = 32
    const val ALPHA_HIGH = 0.87f
}

// 工具函数
fun Int?.isValidDelay(): Boolean =
    this != null && this in ProxyCardConstants.DELAY_MIN..ProxyCardConstants.DELAY_MAX

fun String.truncateIfNeeded(maxLength: Int = ProxyCardConstants.MAX_NAME_LENGTH): String =
    if (length <= maxLength) this else take(maxLength - 3) + "..."

@Composable
fun getDelayColor(delay: Int): Color = when {
    delay < ProxyCardConstants.DELAY_FAST_THRESHOLD -> Color(ProxyCardConstants.COLOR_DELAY_FAST)
    delay < ProxyCardConstants.DELAY_MEDIUM_THRESHOLD -> Color(ProxyCardConstants.COLOR_DELAY_MEDIUM)
    else -> MiuixTheme.colorScheme.onBackground.copy(alpha = ProxyCardConstants.ALPHA_HIGH)
}

// 数据类
@androidx.compose.runtime.Stable
data class ProxyGroupState(
    val proxies: List<Proxy> = emptyList(),
    val selectable: Boolean = false,
    val parent: ProxyState? = null,
    val links: Map<String, ProxyState> = emptyMap(),
    val urlTesting: Boolean = false,
    val testingUpdatedDelays: Boolean = false
)

