package com.github.kr328.clash.design.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppDimensions {
    val spacing_xs = 4.dp
    val spacing_sm = 8.dp
    val spacing_md = 12.dp
    val spacing_lg = 16.dp
    val spacing_xl = 20.dp
    val spacing_xxl = 24.dp

    val padding_inner = 16.dp

    // 页面边距
    val margin_horizontal = 12.dp

    // 卡片
    val card_padding_horizontal = 12.dp
    val card_padding_vertical = 16.dp

    // 兼容旧API - 嵌套对象
    object Card {
        val paddingHorizontal: Dp get() = card_padding_horizontal
        val paddingVertical: Dp get() = card_padding_vertical
    }

    object Padding {
        val xxs: Dp get() = spacing_xs
        val small: Dp get() = spacing_sm
        val medium: Dp get() = spacing_md
        val large: Dp get() = spacing_lg
    }

    object Border {
        val normal: Dp get() = 1.dp
    }
}

