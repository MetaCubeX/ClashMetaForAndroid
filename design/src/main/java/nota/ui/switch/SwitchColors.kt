package nota.ui.switch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class SwitchColors(
    val checkedTrackColor: Color,
    val uncheckedTrackColor: Color,
    val checkedThumbColor: Color,
    val uncheckedThumbColor: Color,
    val disabledCheckedTrackColor: Color,
    val disabledUncheckedTrackColor: Color,
    val disabledCheckedThumbColor: Color,
    val disabledUncheckedThumbColor: Color,
)

object SwitchDefaults {
    /**
     * Light fallbacks. iOS screens pass [nota.ui.ios.shellIosSwitchColors].
     */
    @Composable
    fun colors(
        checkedTrackColor: Color = Color(0xFF34C759),
        uncheckedTrackColor: Color = Color(0xFFE9E9EA),
        checkedThumbColor: Color = Color.White,
        uncheckedThumbColor: Color = Color.White,
        disabledAlpha: Float = 0.25f,
        disabledCheckedTrackColor: Color = checkedTrackColor.copy(alpha = disabledAlpha),
        disabledUncheckedTrackColor: Color = uncheckedTrackColor.copy(alpha = disabledAlpha),
        disabledThumbAlpha: Float = 1f,
        disabledCheckedThumbColor: Color = checkedThumbColor.copy(
            alpha = checkedThumbColor.alpha * disabledThumbAlpha,
        ),
        disabledUncheckedThumbColor: Color = uncheckedThumbColor.copy(
            alpha = uncheckedThumbColor.alpha * disabledThumbAlpha,
        ),
    ): SwitchColors =
        SwitchColors(
            checkedTrackColor = checkedTrackColor,
            uncheckedTrackColor = uncheckedTrackColor,
            checkedThumbColor = checkedThumbColor,
            uncheckedThumbColor = uncheckedThumbColor,
            disabledCheckedTrackColor = disabledCheckedTrackColor,
            disabledUncheckedTrackColor = disabledUncheckedTrackColor,
            disabledCheckedThumbColor = disabledCheckedThumbColor,
            disabledUncheckedThumbColor = disabledUncheckedThumbColor,
        )
}
