package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.ui.Surface
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.currentWindowInsetsSnapshot
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

abstract class Design<R>(val context: Context) :
    CoroutineScope by CoroutineScope(Dispatchers.Unconfined) {
    abstract val root: View

    val surface = Surface()
    val requests: Channel<R> = Channel(Channel.UNLIMITED)

    suspend fun showToast(
        resId: Int,
        duration: ToastDuration,
        configure: Snackbar.() -> Unit = {}
    ) {
        return showToast(context.getString(resId), duration, configure)
    }

    suspend fun showToast(
        message: CharSequence,
        duration: ToastDuration,
        configure: Snackbar.() -> Unit = {}
    ) {
        withContext(Dispatchers.Main) {
            val len = when (duration) {
                ToastDuration.Short -> Snackbar.LENGTH_SHORT
                ToastDuration.Long -> Snackbar.LENGTH_LONG
                ToastDuration.Indefinite -> Snackbar.LENGTH_INDEFINITE
            }
            Snackbar.make(root, message, len).apply {
                root.findViewById<View>(R.id.main_bottom_nav_card)?.let { anchorView = it }
                styleAsMaterial3()
                configure()
            }.show()
        }
    }

    /**
     * Material 3 styling for the Snackbar: rounded inverse-surface card
     * (high-contrast against the page on both light and dark themes) with
     * inverse-on-surface text and inverse-primary action button. Keeps the
     * spec-correct inverse pairing — just gives it our 20dp corner radius
     * via [R.drawable.bg_snackbar_m3] instead of the framework square edges.
     */
    private fun Snackbar.styleAsMaterial3() {
        val ctx = view.context
        // M3 inverse palette (the spec name is `colorSurfaceInverse`, NOT
        // the legacy MaterialComponents `colorInverseSurface` — the latter
        // doesn't exist in Material 1.12+). Material's Theme.Material3.* and
        // .DayNight.* provide light + dark variants automatically, so we get
        // the right contrast on either theme without managing two palettes.
        val bgColor = ctx.resolveThemedColor(
            com.google.android.material.R.attr.colorSurfaceInverse
        )
        val textColor = ctx.resolveThemedColor(
            com.google.android.material.R.attr.colorOnSurfaceInverse
        )
        val actionColor = ctx.resolveThemedColor(
            com.google.android.material.R.attr.colorPrimaryInverse
        )

        // Rounded shape comes from the drawable (which already uses
        // ?attr/colorSurfaceInverse internally); setBackgroundTint asserts
        // the same colour at the Snackbar layer to override the framework
        // backgroundTint that would otherwise repaint over our drawable.
        view.background = androidx.appcompat.content.res.AppCompatResources
            .getDrawable(ctx, R.drawable.bg_snackbar_m3)
        setBackgroundTint(bgColor)
        view.elevation = 6f * ctx.resources.displayMetrics.density

        // Snackbar.setTextColor / setActionTextColor push a ColorStateList
        // through ViewCompat that survives view re-measures — these are the
        // documented Snackbar styling hooks, safer than touching the
        // internal snackbar_text / snackbar_action TextView directly.
        setTextColor(textColor)
        setActionTextColor(actionColor)
    }

    init {
        when (context) {
            is AppCompatActivity -> {
                val decor = context.window.decorView
                decor.currentWindowInsetsSnapshot()?.let {
                    if (surface.insets != it) {
                        surface.insets = it
                    }
                }
                decor.setOnInsertsChangedListener {
                    if (surface.insets != it) {
                        surface.insets = it
                    }
                }
            }
        }
    }
}
