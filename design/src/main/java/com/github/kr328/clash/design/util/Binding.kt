package com.github.kr328.clash.design.util

import android.view.View
import android.view.ViewGroup
import androidx.databinding.BindingAdapter
import com.github.kr328.clash.design.R
import com.google.android.material.card.MaterialCardView
import kotlin.math.roundToInt

@BindingAdapter("android:minHeight")
fun bindMinHeight(view: View, value: Float) {
    view.minimumHeight = value.toInt()
}

/**
 * Data binding resolves [@dimen/...] in expressions to px [Float] via [Resources.getDimension],
 * while [MaterialCardView.setStrokeWidth] expects [Int] px — without this adapter KAPT fails.
 */
@BindingAdapter("cardStrokeWidthBound")
fun MaterialCardView.setCardStrokeWidthBound(dimensionPx: Float) {
    strokeWidth = dimensionPx.roundToInt()
}

@BindingAdapter("bottomNavInset")
fun View.bindBottomNavInset(insetPx: Int) {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    val target = context.resources.getDimensionPixelSize(R.dimen.main_bottom_nav_bottom_margin) + insetPx.coerceAtLeast(0)
    if (lp.bottomMargin == target) return
    lp.bottomMargin = target
    layoutParams = lp
}
