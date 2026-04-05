package com.github.kr328.clash.design.util

import android.view.View
import androidx.databinding.BindingAdapter
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
