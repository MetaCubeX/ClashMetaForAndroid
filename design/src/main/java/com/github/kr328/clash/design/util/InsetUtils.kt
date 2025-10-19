package com.github.kr328.clash.design.util

import android.content.Context
import android.view.View
import androidx.core.view.WindowInsetsCompat
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets

/**
 * Insets 工具类
 * 合并自 util/Inserts.kt 和 util/Landscape.kt
 */

// ========== Inserts.kt ==========

fun View.setOnInsertsChangedListener(adaptLandscape: Boolean = true, listener: (Insets) -> Unit) {
    setOnApplyWindowInsetsListener { v, ins ->
        val compat = WindowInsetsCompat.toWindowInsetsCompat(ins)
        val insets = compat.getInsets(WindowInsetsCompat.Type.systemBars())

        // 使用 View 的 layoutDirection 替代废弃的 ViewCompat.getLayoutDirection
        val rInsets = if (v.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
            Insets(
                insets.left,
                insets.top,
                insets.right,
                insets.bottom,
            )
        } else {
            Insets(
                insets.right,
                insets.top,
                insets.left,
                insets.bottom,
            )
        }

        listener(if (adaptLandscape) rInsets.landscape(v.context) else rInsets)

        compat.toWindowInsets()!!
    }

    requestApplyInsets()
}

// ========== Landscape.kt ==========

fun Insets.landscape(context: Context): Insets {
    val displayMetrics = context.resources.displayMetrics
    val minWidth = context.getPixels(R.dimen.surface_landscape_min_width)

    val width = displayMetrics.widthPixels
    val height = displayMetrics.heightPixels

    return if (width > height && width > minWidth) {
        val expectedWidth = width.coerceAtMost(height.coerceAtLeast(minWidth))

        val padding = (width - expectedWidth).coerceAtLeast(start + end) / 2

        copy(start = padding.coerceAtLeast(start), end = padding.coerceAtLeast(end))
    } else {
        this
    }
}

