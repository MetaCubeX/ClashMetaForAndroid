package universe.agentic.ui.search

import kotlin.math.max
import kotlin.math.min

/** Inner Row end padding: [spaceXs] when clear visible, else [spaceM]. */
fun innerEndPadding(
    showClear: Boolean,
    spaceXs: Float,
    spaceM: Float,
): Float = if (showClear) spaceXs else spaceM

/** Filled applies outer margin; Overlay and Plain do not. */
fun hasFilledOuterMargin(variant: SearchFieldVariant): Boolean =
    variant == SearchFieldVariant.Filled

/** Filled and Overlay use surfaceContainerHigh pill. */
fun usesFilledBackground(variant: SearchFieldVariant): Boolean =
    variant == SearchFieldVariant.Filled || variant == SearchFieldVariant.Overlay

/**
 * Parent-owned clear end-zone geometry.
 *
 * - [layoutWidth] — width reported to the Row (= touch; horizontal ownership).
 * - [layoutHeight] — height reported to the Row (≤ row; must not stretch chrome).
 * - [touchSize] — clickable edge length (may overflow vertically only).
 */
data class ClearSlot(
    val layoutWidth: Float,
    val layoutHeight: Float,
    val touchSize: Float,
)

fun resolveClearLayoutSize(
    dimens: SearchFieldDimens,
    rowHeight: Float? = null,
): Float {
    val effectiveRow = rowHeight ?: dimens.barHeightDefault
    return min(dimens.clearButtonSize, effectiveRow)
}

fun resolveClearLayoutHeight(
    dimens: SearchFieldDimens,
    rowHeight: Float? = null,
): Float = resolveClearLayoutSize(dimens, rowHeight)

fun resolveClearLayoutWidth(dimens: SearchFieldDimens): Float = resolveClearTouchSize(dimens)

fun resolveClearTouchSize(dimens: SearchFieldDimens): Float =
    max(dimens.touchTargetMin, dimens.clearButtonSize)

fun resolveClearSlot(
    dimens: SearchFieldDimens,
    rowHeight: Float? = null,
): ClearSlot {
    val touch = resolveClearTouchSize(dimens)
    return ClearSlot(
        layoutWidth = touch,
        layoutHeight = resolveClearLayoutHeight(dimens, rowHeight),
        touchSize = touch,
    )
}

fun resolveClearTrailingGap(
    showClear: Boolean,
    hasTrailing: Boolean,
    spaceXs: Float,
): Float = if (showClear && hasTrailing) spaceXs else 0f
