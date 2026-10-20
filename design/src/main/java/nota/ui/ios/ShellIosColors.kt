package nota.ui.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import nota.ui.list.ListRowColors
import nota.ui.settings.PreferenceItemColors
import nota.ui.switch.SwitchColors

/**
 * Set by ClashMetaXTheme from `UiStore.darkMode`.
 * The library used `isSystemInDarkTheme()`, which ignores the in-app override.
 */
val LocalShellIosDark = staticCompositionLocalOf { false }

internal fun iosColor(argb: Long): Color = Color(argb)

@Composable
fun shellIosSwitchColors(tint: Color? = null): SwitchColors {
    val dark = LocalShellIosDark.current
    val onTrack = tint ?: iosColor(if (dark) ShellIosPalette.GreenDark else ShellIosPalette.Green)
    val offTrack = iosColor(if (dark) ShellIosPalette.SwitchOffDark else ShellIosPalette.SwitchOff)
    val thumb = iosColor(ShellIosPalette.Card)
    return SwitchColors(
        checkedTrackColor = onTrack,
        uncheckedTrackColor = offTrack,
        checkedThumbColor = thumb,
        uncheckedThumbColor = thumb,
        disabledCheckedTrackColor = onTrack.copy(alpha = 0.4f),
        disabledUncheckedTrackColor = offTrack.copy(alpha = 0.4f),
        disabledCheckedThumbColor = thumb.copy(alpha = 0.8f),
        disabledUncheckedThumbColor = thumb.copy(alpha = 0.8f),
    )
}

@Composable
fun shellIosPreferenceColors(): PreferenceItemColors {
    val dark = LocalShellIosDark.current
    val title = iosColor(if (dark) ShellIosPalette.LabelDark else ShellIosPalette.Label)
    val summary = iosColor(if (dark) ShellIosPalette.SecondaryLabelDark else ShellIosPalette.SecondaryLabel)
    val arrow = iosColor(if (dark) ShellIosPalette.ChevronDark else ShellIosPalette.Chevron)
    return PreferenceItemColors(
        titleColor = title,
        summaryColor = summary,
        arrowColor = arrow,
        dividerColor = iosColor(if (dark) ShellIosPalette.SeparatorDark else ShellIosPalette.Separator),
        containerColor = iosColor(if (dark) ShellIosPalette.CardDark else ShellIosPalette.Card),
        disabledTitleColor = title.copy(alpha = 0.4f),
        disabledSummaryColor = summary.copy(alpha = 0.4f),
        disabledArrowColor = arrow.copy(alpha = 0.4f),
    )
}

@Composable
fun shellIosListRowColors(): ListRowColors {
    val dark = LocalShellIosDark.current
    val title = iosColor(if (dark) ShellIosPalette.LabelDark else ShellIosPalette.Label)
    val summary = iosColor(if (dark) ShellIosPalette.SecondaryLabelDark else ShellIosPalette.SecondaryLabel)
    val trailing = iosColor(if (dark) ShellIosPalette.ChevronDark else ShellIosPalette.Chevron)
    return ListRowColors(
        containerColor = iosColor(if (dark) ShellIosPalette.CardDark else ShellIosPalette.Card),
        titleColor = title,
        summaryColor = summary,
        trailingColor = trailing,
        dividerColor = iosColor(if (dark) ShellIosPalette.SeparatorDark else ShellIosPalette.Separator),
        disabledTitleColor = title.copy(alpha = 0.4f),
        disabledSummaryColor = summary.copy(alpha = 0.4f),
        disabledTrailingColor = trailing.copy(alpha = 0.4f),
    )
}

