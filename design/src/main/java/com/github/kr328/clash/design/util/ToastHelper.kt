package com.github.kr328.clash.design.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.github.kr328.clash.design.ui.ToastConfiguration
import com.github.kr328.clash.design.ui.ToastDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MiuixToastHelper(private val context: Context) {

    suspend fun showToast(
        message: CharSequence,
        duration: ToastDuration,
        configure: ToastConfiguration.() -> Unit = {}
    ) = withContext(Dispatchers.Main) {
        val config = ToastConfiguration().apply(configure)

        // Always show simple system toast without any actions
        // Toast操作已被弃用，直接显示消息，action始终为null
        val finalMessage = message

        showSimpleToast(finalMessage, duration)
    }

    private fun showSimpleToast(message: CharSequence, duration: ToastDuration) {
        val toast = android.widget.Toast(context)

        // Create custom view
        val toastView = createToastView(message.toString())

        @Suppress("DEPRECATION")
        toast.view = toastView
        toast.duration = when (duration) {
            ToastDuration.Short -> android.widget.Toast.LENGTH_SHORT
            ToastDuration.Long -> android.widget.Toast.LENGTH_LONG
            ToastDuration.Indefinite -> android.widget.Toast.LENGTH_LONG
        }
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 100)
        toast.show()
    }


    private fun createToastView(message: String): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 24, 48, 24)
            gravity = Gravity.CENTER_VERTICAL

            // Create background with Miuix-style rounded corners and shadow
            val background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#323232")) // Dark background similar to Miuix
                setStroke(1, Color.parseColor("#40FFFFFF"))
            }
            this.background = background

            // Add shadow
            elevation = 8f
        }

        // Add message text
        val messageText = TextView(context).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        layout.addView(messageText)


        return layout
    }
}

suspend fun Context.showMiuixToast(
    message: CharSequence,
    duration: ToastDuration,
    configure: ToastConfiguration.() -> Unit = {}
) {
    val helper = MiuixToastHelper(this)
    helper.showToast(message, duration, configure)
}