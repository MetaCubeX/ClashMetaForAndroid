package com.github.kr328.clash.widget

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.github.kr328.clash.R

object WidgetAppearanceResolver {
    fun isNight(context: Context, appearance: WidgetAppearance): Boolean {
        return when (appearance) {
            WidgetAppearance.FollowSystem ->
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
            WidgetAppearance.Light -> false
            WidgetAppearance.Dark -> true
        }
    }

    @DrawableRes
    fun background(
        night: Boolean,
        transparency: WidgetTransparency,
    ): Int {
        return when (transparency) {
            WidgetTransparency.Low ->
                if (night) R.drawable.widget_bg_dark_low else R.drawable.widget_bg_light_low
            WidgetTransparency.Medium ->
                if (night) R.drawable.widget_bg_dark_medium else R.drawable.widget_bg_light_medium
            WidgetTransparency.High ->
                if (night) R.drawable.widget_bg_dark_high else R.drawable.widget_bg_light_high
        }
    }

    @ColorRes
    fun foreground(night: Boolean, running: Boolean): Int {
        return when {
            running && night -> R.color.widget_active_dark
            running -> R.color.widget_active_light
            night -> R.color.widget_inactive_dark
            else -> R.color.widget_inactive_light
        }
    }
}
