package com.github.kr328.clash.design.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kr328.clash.design.ui.Insets

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
