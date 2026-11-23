package com.github.kr328.clash.design.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets

private class InsertListener(
    val adaptLandscape: Boolean,
    val listener: (Insets) -> Unit,
)

fun View.setOnInsertsChangedListener(adaptLandscape: Boolean = true, listener: (Insets) -> Unit) {
    val existing = getTag(R.id.insert_listeners) as? MutableList<InsertListener>
    val bucket = existing ?: mutableListOf<InsertListener>().also { setTag(R.id.insert_listeners, it) }
    bucket.add(InsertListener(adaptLandscape, listener))
    setOnApplyWindowInsetsListener { v, ins ->
        val compat = WindowInsetsCompat.toWindowInsetsCompat(ins)
        val insets = compat.getInsets(WindowInsetsCompat.Type.systemBars())
        val raw = if (ViewCompat.getLayoutDirection(v) == ViewCompat.LAYOUT_DIRECTION_LTR) {
            Insets(insets.left, insets.top, insets.right, insets.bottom)
        } else {
            Insets(insets.right, insets.top, insets.left, insets.bottom)
        }
        val items = v.getTag(R.id.insert_listeners) as? List<InsertListener>
        items?.forEach { item ->
            item.listener(if (item.adaptLandscape) raw.landscape(v.context) else raw)
        }
        compat.toWindowInsets()!!
    }
    requestApplyInsets()
}
