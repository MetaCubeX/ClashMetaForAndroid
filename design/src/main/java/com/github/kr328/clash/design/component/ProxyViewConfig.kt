package com.github.kr328.clash.design.component

import android.content.Context
import android.graphics.Color
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.resolveThemedResourceId

class ProxyViewConfig(val context: Context, var proxyLine: Int) {
    private val colorSurface = context.resolveThemedColor(com.google.android.material.R.attr.colorSurface)

    val clickableBackground =
        context.resolveThemedResourceId(android.R.attr.selectableItemBackground)

    val selectedControl = context.resolveThemedColor(com.google.android.material.R.attr.colorOnPrimary)
    val selectedBackground = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)

    val unselectedControl = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)
    val primary = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
    val onPrimary = context.resolveThemedColor(com.google.android.material.R.attr.colorOnPrimary)
    val unselectedBackground: Int
        get() = if (proxyLine==1) Color.TRANSPARENT else colorSurface

    /**
     * Badge palette.
     *
     * The type badge shows on every card, so it stays neutral — an accent there
     * would tile the whole grid in brand blue. The chain badge is rare, so it
     * earns the accent. On a selected card the background is already
     * colorPrimary, so both invert against it.
     */
    val badgeBackground = context.resolveThemedColor(com.google.android.material.R.attr.colorSurfaceVariant)
    val badgeText = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    val badgeSelectedBackground = onPrimary.withAlpha(0x38)
    val badgeSelectedText = onPrimary

    val accentBadgeBackground = primary
    val accentBadgeText = onPrimary
    val accentBadgeSelectedBackground = onPrimary
    val accentBadgeSelectedText = primary

    /** Badge metrics scale off the card's text size so grid modes stay proportional. */
    val badgeTextSize
        get() = textSize * 0.75f
    val badgeHeight
        get() = textSize * 1.32f
    val badgePaddingHorizontal
        get() = textSize * 0.42f
    val badgeGap
        get() = textMargin * 0.6f
    val badgeRadius
        get() = badgeHeight * 0.28f

    private fun Int.withAlpha(alpha: Int): Int =
        Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))

    val layoutPadding = context.getPixels(R.dimen.proxy_layout_padding).toFloat()
    val contentPadding
        get() = if (proxyLine==2) context.getPixels(R.dimen.proxy_content_padding).toFloat() else context.getPixels(R.dimen.proxy_content_padding_grid3).toFloat()
    val textMargin
        get() = if (proxyLine==2) context.getPixels(R.dimen.proxy_text_margin).toFloat() else context.getPixels(R.dimen.proxy_text_margin_grid3).toFloat()
    val textSize
        get() = if (proxyLine==2) context.getPixels(R.dimen.proxy_text_size).toFloat() else context.getPixels(R.dimen.proxy_text_size_grid3).toFloat()

    val shadow = Color.argb(
        0x15,
        Color.red(Color.DKGRAY),
        Color.green(Color.DKGRAY),
        Color.blue(Color.DKGRAY),
    )

    val cardRadius = context.getPixels(R.dimen.proxy_card_radius).toFloat()
    var cardOffset = context.getPixels(R.dimen.proxy_card_offset).toFloat()
}