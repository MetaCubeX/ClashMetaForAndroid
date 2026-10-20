package nota.ui.ios

import nota.ui.list.ListRowSpec
import nota.ui.settings.PreferenceGroupPosition
import nota.ui.settings.PreferenceItemSpec
import nota.ui.settings.resolvePreferenceContentHorizontalPadding
import nota.ui.switch.SwitchTrackSpec
import universe.agentic.ui.search.SearchFieldDimens

/** UISwitch: 51×31 track, 27 thumb, 2 inset, 44 touch height. */
fun shellIosSwitchSpec(): SwitchTrackSpec =
    SwitchTrackSpec(
        trackHeight = 31f,
        trackWidth = 51f,
        thumbOnHeight = 27f,
        thumbOffHeight = 27f,
        thumbOnWidth = 27f,
        thumbOffWidth = 27f,
        thumbOnCorner = 13.5f,
        thumbOffCorner = 13.5f,
        thumbMorphEnabled = false,
        trackCorner = 15.5f,
        horizontalInset = 2f,
        minTouchTarget = 44f,
    )

/**
 * Inset grouped settings row.
 *
 * Card sits 16dp in from the screen. Text sits another 16dp in from the card,
 * so [resolvePreferenceContentHorizontalPadding] is 16. Corners are 10dp.
 * Hairline separators start at the title and end at the card's trailing edge.
 */
fun shellIosPreferenceSpec(): PreferenceItemSpec =
    PreferenceItemSpec(
        horizontalPadding = 16f,
        immersiveHorizontalPadding = 32f,
        immersiveCardInset = 16f,
        verticalPaddingTitleOnly = 11f,
        verticalPaddingWithSummary = 10f,
        titleSummaryGap = 2f,
        titleTextSize = 17f,
        summaryTextSize = 15f,
        summaryLineSpacingExtra = 0f,
        dividerThickness = 0.5f,
        dividerInsetStart = 32f,
        dividerInsetEnd = 16f,
        arrowSize = 13f,
        widgetMinWidth = 0f,
        widgetGap = 8f,
        widgetGapImmersive = 8f,
        leadingGap = 12f,
        iconSize = 29f,
        iconSizeSingleline = 29f,
        iconCornerRadius = 7f,
        minHeightTitleOnly = 44f,
        minHeightWithSummary = 58f,
        groupCornerRadius = 10f,
        groupPaddingOuter = 11f,
        groupPaddingInner = 11f,
        groupPaddingOuterWithSummary = 10f,
        groupPaddingInnerWithSummary = 10f,
    )

/** Plain list row: 17/15 type, 44 minimum, hairline under the title. */
fun shellIosListRowSpec(): ListRowSpec =
    ListRowSpec(
        horizontalPadding = 16f,
        verticalPaddingTitleOnly = 12f,
        verticalPaddingWithSummary = 8f,
        titleSummaryGap = 2f,
        titleTextSize = 17f,
        summaryTextSize = 15f,
        minHeight = 44f,
        dividerThickness = 0.5f,
        dividerInsetStart = 16f,
        dividerInsetEnd = 0f,
        arrowSize = 13f,
        trailingGap = 8f,
        leadingGap = 12f,
    )

/** Search bar: 36 tall, 10 corner, 8 inner padding. Fill still comes from the host theme. */
fun shellIosSearchDimens(): SearchFieldDimens =
    SearchFieldDimens(
        barHeightDefault = 36f,
        touchTargetMin = 44f,
        cornerPopup = 10f,
        spaceM = 8f,
        spaceS = 0f,
        spaceXs = 6f,
        iconSize = 15f,
        clearButtonSize = 20f,
    )

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
