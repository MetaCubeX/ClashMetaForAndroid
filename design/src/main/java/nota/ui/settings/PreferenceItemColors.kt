package nota.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class PreferenceItemColors(
    val titleColor: Color,
    val summaryColor: Color,
    val arrowColor: Color,
    val dividerColor: Color,
    val containerColor: Color,
    val disabledTitleColor: Color,
    val disabledSummaryColor: Color,
    val disabledArrowColor: Color,
)

object PreferenceItemDefaults {
    /**
     * Light iOS fallbacks. Callers that follow the app theme pass [nota.ui.ios.shellIosPreferenceColors].
     */
    @Composable
    fun colors(
        titleColor: Color = Color.Black,
        summaryColor: Color = Color(0x993C3C43),
        arrowColor: Color = Color(0xFFC7C7CC),
        dividerColor: Color = Color(0xFFC6C6C8),
        containerColor: Color = Color.White,
        disabledAlpha: Float = 0.4f,
        disabledTitleColor: Color = titleColor.copy(alpha = disabledAlpha),
        disabledSummaryColor: Color = summaryColor.copy(alpha = disabledAlpha),
        disabledArrowColor: Color = arrowColor.copy(alpha = disabledAlpha),
    ): PreferenceItemColors =
        PreferenceItemColors(
            titleColor = titleColor,
            summaryColor = summaryColor,
            arrowColor = arrowColor,
            dividerColor = dividerColor,
            containerColor = containerColor,
            disabledTitleColor = disabledTitleColor,
            disabledSummaryColor = disabledSummaryColor,
            disabledArrowColor = disabledArrowColor,
        )
}
