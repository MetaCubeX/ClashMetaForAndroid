package com.github.kr328.clash.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 应用形状规范
 * 精简版 - 仅保留实际使用的形状
 */
object AppShapes {
    // 基础圆角
    val corner_sm = RoundedCornerShape(8.dp)
    val corner_md = RoundedCornerShape(12.dp)
    val corner_lg = RoundedCornerShape(16.dp)
    val corner_xl = RoundedCornerShape(20.dp)

    // 卡片形状
    val card_shape = RoundedCornerShape(16.dp)

    // 兼容旧API - 直接属性
    val small: Shape get() = corner_sm
    val extraLarge: Shape get() = corner_xl
}

