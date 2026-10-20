package com.github.kr328.clash.design.ios

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.UiStore
import nota.ui.ios.LocalShellIosDark
import nota.ui.theme.LocalShellPressTint

@Immutable
data class IosColors(
    val background: Color,
    val grouped: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val track: Color,
    val segment: Color,
    val pressed: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    val onBlue: Color,
)

private val LightColors = IosColors(
    background = Color(0xFFF2F2F7),
    grouped = Color.White,
    label = Color.Black,
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    track = Color(0x1F767680),
    segment = Color.White,
    pressed = Color(0x14000000),
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    onBlue = Color.White,
)

private val DarkColors = IosColors(
    background = Color.Black,
    grouped = Color(0xFF1C1C1E),
    label = Color.White,
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0xFF38383A),
    track = Color(0x3D767680),
    segment = Color(0xFF636366),
    pressed = Color(0x14FFFFFF),
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    onBlue = Color.White,
)

val LocalIosColors = staticCompositionLocalOf { LightColors }

object IosTheme {
    val colors: IosColors
        @Composable get() = LocalIosColors.current
}

object IosType {
    val largeTitle = TextStyle(
        fontSize = 34.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
    )
    val title2 = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    )
    val headline = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val body = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    )
    val subhead = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    )
    val footnote = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    )
    val segment = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun ClashMetaXTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dark = remember(context) {
        val nightFlag = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        when (UiStore(context).darkMode) {
            DarkMode.ForceDark -> true
            DarkMode.ForceLight -> false
            DarkMode.Auto -> nightFlag == Configuration.UI_MODE_NIGHT_YES
        }
    }
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(
        LocalIosColors provides colors,
        LocalShellIosDark provides dark,
        LocalShellPressTint provides colors.pressed,
    ) {
        content()
    }
}
