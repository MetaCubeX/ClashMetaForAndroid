package com.github.kr328.clash.design.preference

import android.view.View

fun interface OnChangedListener {
    fun onChanged()
}

interface Preference {
    val view: View

    var enabled: Boolean
        get() = view.isEnabled
        set(value) {
            view.isEnabled = value
            view.isClickable = value
            view.isFocusable = value
            view.alpha = if (value) 1.0f else 0.33f
        }

    /** Show/hide the row entirely (GONE), so dependent options don't clutter as disabled rows. */
    var visible: Boolean
        get() = view.visibility == View.VISIBLE
        set(value) {
            view.visibility = if (value) View.VISIBLE else View.GONE
        }
}