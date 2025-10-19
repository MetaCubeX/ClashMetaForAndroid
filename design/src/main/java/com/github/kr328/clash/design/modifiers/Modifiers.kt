package com.github.kr328.clash.design.modifiers

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.github.kr328.clash.design.theme.AppDimensions

/**
 * Card 内边距 modifier
 * 用于卡片内容的标准内边距
 */
fun Modifier.cardPadding(): Modifier = padding(
    horizontal = AppDimensions.Card.paddingHorizontal,
    vertical = AppDimensions.Card.paddingVertical
)

/**
 * 标准卡片外边距 modifier
 * 用于卡片容器的标准外边距
 */
fun Modifier.standardCardPadding(): Modifier = padding(
    horizontal = AppDimensions.margin_horizontal,
    vertical = AppDimensions.spacing_sm
)


