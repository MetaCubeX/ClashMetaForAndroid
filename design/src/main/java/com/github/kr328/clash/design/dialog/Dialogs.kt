package com.github.kr328.clash.design.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.google.android.material.color.MaterialColors
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

    // Resolve the accent from the HOST (activity) context — the dialog's own theme falls back to
    // the base emerald, not the user's dynamic/brand accent.
    private val hostAccent: Int = MaterialColors.getColor(
        context, com.google.android.material.R.attr.colorPrimary, Color.TRANSPARENT,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setCancelable(true)

        // The sheet can't reliably draw behind the nav bar under Material's edge-to-edge
        // insets, which left a transparent strip over the gesture bar. Paint the nav bar
        // with the SAME surface colour the sheet uses (bg_bottom_sheet = ?attr/colorSurface),
        // resolved against the dialog's themed context so it matches in light & dark.
        val surfaceTv = android.util.TypedValue()
        getContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorSurface, surfaceTv, true,
        )

        window!!.apply {
            isSystemBarsTranslucentCompat = true
            isAllowForceDarkCompat = false
            navigationBarColor = surfaceTv.data
        }

        // On-device measurement showed the dialog's root `container` applies the bottom
        // gesture inset (~84px) as its OWN bottom padding (via Material's inset listener,
        // not fitsSystemWindows), pushing the coordinator + sheet up by that much — the
        // strip over the gesture bar. Override the listener: keep the status-bar inset as
        // top padding, drop the bottom, and pass insets through so our design_bottom_sheet
        // listener can pad the content (above the gesture) while the surface fills the edge.
        findViewById<ViewGroup>(com.google.android.material.R.id.container)?.let { container ->
            container.fitsSystemWindows = false
            ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                val bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                )
                v.setPadding(bars.left, bars.top, bars.right, 0)
                insets
            }
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
                        // Content padding = nav-bar/gesture inset so the last row sits
                        // above the gesture bar, while the sheet surface (with
                        // isGestureInsetBottomIgnored) fills all the way to the bottom edge.
                        val bottomPadding = it.bottom

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
            behavior.apply {
                if (fitContentHeight) isFitToContents = true
                skipCollapsed = true
                // By default the behavior reserves space for the system gesture area,
                // so the sheet stops short of the bottom edge — leaving a transparent
                // strip over the gesture pill. Ignore it so the sheet draws fully to the
                // bottom (content is kept above the gesture via the inset padding above).
                isGestureInsetBottomIgnored = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }

            // Soft accent glow at the sheet's TOP edge only — not a hard perimeter frame. A hard
            // stroke framed the whole card and read cheap; a vertical gradient from a faint accent
            // tint at the very top fading to transparent (with the rounded-top corners) gives a
            // soft "lit edge" like the reference, with no visible side/bottom line.
            if (hostAccent != Color.TRANSPARENT) {
                findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                    val r = 28f * sheet.resources.displayMetrics.density
                    sheet.foreground = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            ColorUtils.setAlphaComponent(hostAccent, 0x2E), // ~18% at the top rim
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                        ),
                    ).apply {
                        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                    }
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