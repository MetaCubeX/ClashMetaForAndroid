package nota.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ListRowColors(
    val containerColor: Color,
    val titleColor: Color,
    val summaryColor: Color,
    val trailingColor: Color,
    val dividerColor: Color,
    val disabledTitleColor: Color,
    val disabledSummaryColor: Color,
    val disabledTrailingColor: Color,
)

object ListRowDefaults {
    private const val DisabledContentAlpha = 0.38f

    /**
     * Light iOS fallbacks. Callers that follow the app theme pass [nota.ui.ios.shellIosListRowColors].
     */
    @Composable
    fun colors(
        containerColor: Color = Color.White,
        titleColor: Color = Color.Black,
        summaryColor: Color = Color(0x993C3C43),
        trailingColor: Color = Color(0xFFC7C7CC),
        dividerColor: Color = Color(0xFFC6C6C8),
        disabledAlpha: Float = DisabledContentAlpha,
        disabledTitleColor: Color = titleColor.copy(alpha = disabledAlpha),
        disabledSummaryColor: Color = summaryColor.copy(alpha = disabledAlpha),
        disabledTrailingColor: Color = summaryColor.copy(alpha = disabledAlpha),
    ): ListRowColors =
        ListRowColors(
            containerColor = containerColor,
            titleColor = titleColor,
            summaryColor = summaryColor,
            trailingColor = trailingColor,
            dividerColor = dividerColor,
            disabledTitleColor = disabledTitleColor,
            disabledSummaryColor = disabledSummaryColor,
            disabledTrailingColor = disabledTrailingColor,
        )
}
