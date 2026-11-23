package nota.ui.ios

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import nota.ui.list.ListRowSpec
import nota.ui.settings.PreferenceGroupPosition
import nota.ui.settings.PreferenceItemSpec
import nota.ui.settings.resolvePreferenceContentHorizontalPadding
import nota.ui.switch.SwitchTrackSpec
import nota.ui.theme.ShellCorners
import nota.ui.theme.ShellShapes
import universe.agentic.ui.search.SearchFieldDimens

/** Flat switch: 40×22 track, 18 thumb. Accent color comes from the caller. */
fun shellIosSwitchSpec(): SwitchTrackSpec =
    SwitchTrackSpec(
        trackHeight = 22f,
        trackWidth = 40f,
        thumbOnHeight = 18f,
        thumbOffHeight = 18f,
        thumbOnWidth = 18f,
        thumbOffWidth = 18f,
        thumbOnCorner = 9f,
        thumbOffCorner = 9f,
        thumbMorphEnabled = false,
        trackCorner = 11f,
        horizontalInset = 2f,
        minTouchTarget = 44f,
    )

/**
 * Inset grouped settings row.
 *
 * Card sits 16dp in from the screen. Text sits another 16dp in from the card.
 * Corners are 16dp. Grouped rows are separated by a 0.5dp hairline that starts at the text.
 * Title-only padding is 12dp. A 28dp leading icon stays inside the 52dp minimum.
 */
fun shellIosPreferenceSpec(): PreferenceItemSpec =
    PreferenceItemSpec(
        horizontalPadding = 16f,
        immersiveHorizontalPadding = 32f,
        immersiveCardInset = 16f,
        verticalPaddingTitleOnly = 12f,
        verticalPaddingWithSummary = 10f,
        titleSummaryGap = 2f,
        titleTextSize = 16f,
        summaryTextSize = 13f,
        summaryLineSpacingExtra = 0f,
        dividerThickness = 0f,
        dividerInsetStart = 32f,
        dividerInsetEnd = 16f,
        arrowSize = 16f,
        widgetMinWidth = 0f,
        widgetGap = 8f,
        widgetGapImmersive = 8f,
        leadingGap = 12f,
        iconSize = 28f,
        iconSizeSingleline = 28f,
        iconCornerRadius = 8f,
        minHeightTitleOnly = 52f,
        minHeightWithSummary = 64f,
        groupCornerRadius = 16f,
        groupPaddingOuter = 12f,
        groupPaddingInner = 12f,
        groupPaddingOuterWithSummary = 12f,
        groupPaddingInnerWithSummary = 10f,
        groupedDividerThickness = 0.5f,
    )

/** Plain list row: 16/13 type, 52 minimum, faint divider under long lists. Title-only padding is 8dp so a 36dp icon stays inside that minimum. */
fun shellIosListRowSpec(): ListRowSpec =
    ListRowSpec(
        horizontalPadding = 16f,
        verticalPaddingTitleOnly = 8f,
        verticalPaddingWithSummary = 10f,
        titleSummaryGap = 2f,
        titleTextSize = 16f,
        summaryTextSize = 13f,
        minHeight = 52f,
        dividerThickness = 0.5f,
        dividerInsetStart = 16f,
        dividerInsetEnd = 0f,
        arrowSize = 16f,
        trailingGap = 8f,
        leadingGap = 12f,
    )

/** Search bar: 40 tall pill. Fill still comes from the host theme. */
fun shellIosSearchDimens(): SearchFieldDimens =
    SearchFieldDimens(
        barHeightDefault = 40f,
        touchTargetMin = 44f,
        cornerPopup = 20f,
        spaceM = 12f,
        spaceS = 0f,
        spaceXs = 8f,
        iconSize = 18f,
        clearButtonSize = 20f,
    )

/** Card corner for one row inside an inset group. Middle rows stay square. */
fun shellIosGroupShape(index: Int, count: Int): Shape {
    val radius = ShellCorners.Lg
    return when (shellIosGroupPosition(index, count)) {
        PreferenceGroupPosition.Alone -> ShellShapes.Container
        PreferenceGroupPosition.Head -> RoundedCornerShape(topStart = radius, topEnd = radius)
        PreferenceGroupPosition.Foot -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
        PreferenceGroupPosition.Middle -> ShellShapes.None
    }
}

/** Head / middle / foot / alone for an inset group of [count] rows. */
fun shellIosGroupPosition(index: Int, count: Int): PreferenceGroupPosition {
    require(count > 0) { "count must be positive" }
    require(index in 0 until count) { "index $index out of 0 until $count" }
    return when {
        count == 1 -> PreferenceGroupPosition.Alone
        index == 0 -> PreferenceGroupPosition.Head
        index == count - 1 -> PreferenceGroupPosition.Foot
        else -> PreferenceGroupPosition.Middle
    }
}
