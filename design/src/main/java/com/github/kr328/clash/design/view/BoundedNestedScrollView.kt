package com.github.kr328.clash.design.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.widget.NestedScrollView
import com.github.kr328.clash.design.R

/**
 * A NestedScrollView that respects a maxHeight cap. When the content fits within the cap
 * the view sizes to wrap_content; once it would exceed, the inner content scrolls instead
 * of pushing the parent scroller into overflow.
 *
 * Use case: announcement card body on the home screen — without this, a long announcement
 * would push the home page into a scrollable state on shorter devices.
 */
class BoundedNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : NestedScrollView(context, attrs, defStyleAttr) {

    private var maxHeightPx: Int = 0

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.BoundedNestedScrollView)
            maxHeightPx = a.getDimensionPixelSize(
                R.styleable.BoundedNestedScrollView_maxHeight,
                0,
            )
            a.recycle()
        }
    }

    fun setMaxHeight(px: Int) {
        if (maxHeightPx == px) return
        maxHeightPx = px
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val spec = if (maxHeightPx > 0) {
            MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, spec)
    }
}
