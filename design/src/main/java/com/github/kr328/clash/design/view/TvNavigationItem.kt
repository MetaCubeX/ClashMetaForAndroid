package com.github.kr328.clash.design.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.ComponentTvNavigationItemBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.google.android.material.card.MaterialCardView

class TvNavigationItem @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
) : MaterialCardView(context, attributeSet, defStyleAttr) {
    private val binding = ComponentTvNavigationItemBinding
        .inflate(context.layoutInflater, this, true)

    var text: CharSequence?
        get() = binding.textView.text
        set(value) {
            binding.textView.text = value
        }

    var icon: Drawable?
        get() = binding.iconView.drawable
        set(value) {
            binding.iconView.setImageDrawable(value)
        }

    init {
        isFocusable = true
        isClickable = true
        radius = resources.getDimension(R.dimen.tv_dashboard_radius)
        strokeWidth = resources.getDimensionPixelSize(R.dimen.tv_dashboard_stroke)
        cardElevation = 0f
        setCardBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.tv_transparent)))
        setStrokeColor(ContextCompat.getColorStateList(context, R.color.tv_nav_stroke))
        setRippleColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.tv_accent_ripple)))

        context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.LargeActionLabel,
            defStyleAttr,
            0,
        ).apply {
            try {
                icon = getDrawable(R.styleable.LargeActionLabel_icon)
                text = getString(R.styleable.LargeActionLabel_text)
            } finally {
                recycle()
            }
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        setCardBackgroundColor(
            if (isFocused || isSelected) ContextCompat.getColor(context, R.color.tv_accent_surface)
            else ContextCompat.getColor(context, R.color.tv_transparent),
        )
        alpha = if (isEnabled) 1f else 0.42f
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animate()
            .scaleX(if (gainFocus) 1.012f else 1f)
            .scaleY(if (gainFocus) 1.012f else 1f)
            .translationZ(if (gainFocus) resources.getDimension(R.dimen.tv_focus_elevation) else 0f)
            .setDuration(120L)
            .start()
    }
}
