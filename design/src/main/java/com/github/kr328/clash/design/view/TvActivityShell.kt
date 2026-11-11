package com.github.kr328.clash.design.view

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.github.kr328.clash.design.databinding.ComponentTvActivityShellBinding
import com.github.kr328.clash.design.util.layoutInflater

class TvActivityShell(context: Context) : LinearLayout(context) {
    private val binding = ComponentTvActivityShellBinding
        .inflate(context.layoutInflater, this, true)

    val navigationBar: TvNavigationBar
        get() = binding.navigationBar

    init {
        orientation = VERTICAL
        clipChildren = false
    }

    fun setContent(view: View) {
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun focusFirstContent() {
        binding.contentContainer.post {
            binding.contentContainer.firstFocusableDescendant()?.requestFocus()
        }
    }

    private fun View.firstFocusableDescendant(): View? {
        if (visibility != View.VISIBLE || !isEnabled) return null
        if (isFocusable) return this
        if (this !is ViewGroup) return null

        for (index in 0 until childCount) {
            getChildAt(index).firstFocusableDescendant()?.let { return it }
        }
        return null
    }
}
