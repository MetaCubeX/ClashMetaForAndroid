package com.github.kr328.clash.design.theme

import android.app.Activity
import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.UiStore
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 亮色主题配色 */
private val LightColorScheme = lightColorScheme()

/** 暗色主题配色 */
private val DarkColorScheme = darkColorScheme()

enum class ThemeMode { Auto, Light, Dark }

@Composable
fun YumeTheme(
    themeMode: ThemeMode? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // 若未显式传入 themeMode，则从全局 UiStore 读取
    val effectiveThemeMode = themeMode ?: run {
        val ctx = LocalContext.current
        val store = UiStore(ctx)
        when (store.darkMode) {
            DarkMode.Auto -> ThemeMode.Auto
            DarkMode.ForceLight -> ThemeMode.Light
            DarkMode.ForceDark -> ThemeMode.Dark
        }
    }

    val isDark = when (effectiveThemeMode) {
        ThemeMode.Auto -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (isDark) DarkColorScheme else LightColorScheme

    val navBarColor = Color.Transparent
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 使用 WindowCompat API（兼容所有版本）
            WindowCompat.setDecorFitsSystemWindows(window, false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+: 使用 WindowInsetsController
                window.insetsController?.setSystemBarsAppearance(
                    if (navBarColor.luminance() > 0.5f) WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                // API 29及以下: 设置导航栏颜色和亮度
                @Suppress("DEPRECATION")
                window.navigationBarColor = navBarColor.toArgb()
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightNavigationBars = navBarColor.luminance() > 0.5f
            }
        }
    }

    MiuixTheme(
        colors = colors,
    ) {
        content()
    }
}