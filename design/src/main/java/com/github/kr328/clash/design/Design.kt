package com.github.kr328.clash.design

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.Surface
import com.github.kr328.clash.design.ui.ToastDuration
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
            Snackbar.make(
                root,
                message,
                when (duration) {
                    ToastDuration.Short -> Snackbar.LENGTH_SHORT
                    ToastDuration.Long -> Snackbar.LENGTH_LONG
                    ToastDuration.Indefinite -> Snackbar.LENGTH_INDEFINITE
                }
            ).apply(configure).apply {
                val dark = isDarkMode()
                setBackgroundTint(if (dark) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt())
                setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
                setActionTextColor(if (dark) 0xFF0A84FF.toInt() else 0xFF007AFF.toInt())
            }.show()
        }
    }

    private fun isDarkMode(): Boolean {
        val nightFlag = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return when (UiStore(context).darkMode) {
            DarkMode.ForceDark -> true
            DarkMode.ForceLight -> false
            DarkMode.Auto -> nightFlag == Configuration.UI_MODE_NIGHT_YES
        }
    }

    init {
        when (context) {
            is AppCompatActivity -> {
                context.window.decorView.setOnInsertsChangedListener {
                    if (surface.insets != it) {
                        surface.insets = it
                    }
                }
            }
        }
    }
}