package com.github.kr328.clash

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.design.model.Behavior
import com.github.kr328.clash.design.store.UiStore.Companion.mainActivityAlias

internal fun Context.createAppSettingsBehavior(): Behavior {
    val context = this

    return object : Behavior {
        override var autoRestart: Boolean
            get() {
                val status = context.packageManager.getComponentEnabledSetting(
                    RestartReceiver::class.componentName
                )

                return status == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            set(value) {
                val status = if (value) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

                context.packageManager.setComponentEnabledSetting(
                    RestartReceiver::class.componentName,
                    status,
                    PackageManager.DONT_KILL_APP,
                )
            }
    }
}

internal fun Context.updateMainActivityAliasVisibility(hide: Boolean) {
    val newState = if (hide) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    packageManager.setComponentEnabledSetting(
        mainActivityAlias,
        newState,
        PackageManager.DONT_KILL_APP
    )

    if (hide) {
        ShortcutManagerCompat.removeAllDynamicShortcuts(this)
    }
}

internal fun Context.isExpandedSettingsWidth(): Boolean {
    val configuration = resources.configuration
    val sizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(
        configuration.screenWidthDp.toFloat(),
        configuration.screenHeightDp.toFloat()
    )

    return sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
}
