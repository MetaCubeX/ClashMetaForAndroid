package com.github.kr328.clash.design.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.github.kr328.clash.common.compat.isAllowForceDarkCompat
import com.github.kr328.clash.common.compat.isSystemBarsTranslucentCompat
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.ui.Surface
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.resolveThemedResourceId
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/** Adds navigation-bar inset to content bottom padding; sheet background stays edge-to-edge. */
object BottomSheetContentInsets {
    fun apply(content: View) {
        val baseStart = content.paddingStart
        val baseTop = content.paddingTop
        val baseEnd = content.paddingEnd
        val baseBottom = content.paddingBottom
        content.setOnInsertsChangedListener { insets ->
            content.setPaddingRelative(
                baseStart,
                baseTop,
                baseEnd,
                baseBottom + insets.bottom,
            )
        }
    }
}

class AppBottomSheetDialog(
    context: Context,
    private val fitContentHeight: Boolean = false,
) : BottomSheetDialog(context) {
    private var insets: Insets = Insets.EMPTY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setCancelable(true)

        window!!.apply {
            isSystemBarsTranslucentCompat = true
            isAllowForceDarkCompat = false
        }

        findViewById<ViewGroup>(com.google.android.material.R.id.container)?.apply {
            fitsSystemWindows = false
        }

        findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            setOnInsertsChangedListener {
                if (insets != it) {
                    insets = it

                    (layoutParams as CoordinatorLayout.LayoutParams).also { params ->
                        if (ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_LTR) {
                            params.setMargins(it.start, 0, it.end, 0)
                        } else {
                            params.setMargins(it.end, 0, it.start, 0)
                        }

                        val top = context.getPixels(R.dimen.bottom_sheet_background_padding_top)
                        val height = context.getPixels(R.dimen.bottom_sheet_header_height)
                        val bottomPadding = 0

                        setPaddingRelative(
                            0,
                            top * 2 + height,
                            0,
                            bottomPadding
                        )
                    }
                }
            }
        }

        setOnShowListener {
            if (fitContentHeight) {
                behavior.apply {
                    isFitToContents = true
                    skipCollapsed = true
                    state = BottomSheetBehavior.STATE_EXPANDED
                }
            } else {
                behavior.apply {
                    skipCollapsed = true
                    state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }
}

class FullScreenDialog(
    context: Context
) : Dialog(context, context.resolveThemedResourceId(R.attr.fullScreenDialogTheme)) {
    val surface = Surface()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window!!.apply {
            isSystemBarsTranslucentCompat = true

            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )

            decorView.setOnInsertsChangedListener {
                if (surface.insets != it)
                    surface.insets = it
            }
        }
    }
}