package com.github.kr328.clash.design.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kr328.clash.design.ui.Insets

/**
 * Snapshot of bars insets for this view's window, if already available.
 * After swapping content (reinflate), avoids one layout pass with [Insets.EMPTY] and a follow-up jump.
 */
fun View.currentWindowInsetsSnapshot(adaptLandscape: Boolean = true): Insets? {
    val compat = ViewCompat.getRootWindowInsets(this) ?: return null
    val statusInsets = compat.getInsets(WindowInsetsCompat.Type.statusBars())
    val navInsets = compat.getInsets(WindowInsetsCompat.Type.navigationBars())
    val rInsets = if (ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_LTR) {
        Insets(
            navInsets.left,
            statusInsets.top,
            navInsets.right,
            navInsets.bottom,
        )
    } else {
        Insets(
            navInsets.right,
            statusInsets.top,
            navInsets.left,
            navInsets.bottom,
        )
    }
    return if (adaptLandscape) rInsets.landscape(context) else rInsets
}

fun View.setOnInsertsChangedListener(adaptLandscape: Boolean = true, listener: (Insets) -> Unit) {
    setOnApplyWindowInsetsListener { v, ins ->
        val compat = WindowInsetsCompat.toWindowInsetsCompat(ins)
        val statusInsets = compat.getInsets(WindowInsetsCompat.Type.statusBars())
        val navInsets = compat.getInsets(WindowInsetsCompat.Type.navigationBars())

        val rInsets = if (ViewCompat.getLayoutDirection(v) == ViewCompat.LAYOUT_DIRECTION_LTR) {
            Insets(
                navInsets.left,
                statusInsets.top,
                navInsets.right,
                navInsets.bottom,
            )
        } else {
            Insets(
                navInsets.right,
                statusInsets.top,
                navInsets.left,
                navInsets.bottom,
            )
        }

        listener(if (adaptLandscape) rInsets.landscape(v.context) else rInsets)

        compat.toWindowInsets()!!
    }

    requestApplyInsets()
}
