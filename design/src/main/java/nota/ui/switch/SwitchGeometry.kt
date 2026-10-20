package nota.ui.switch

import kotlin.math.abs

data class ThumbGeometry(
    val thumbWidth: Float,
    val thumbHeight: Float,
    val thumbCorner: Float,
    /** Offset from the track's left edge. */
    val thumbOffsetX: Float,
)

/**
 * @param checked Host intent; geometry uses [progress] as the sole interpolation source.
 * @param progress 0 = fully unchecked, 1 = fully checked; mid-animation values allowed.
 *
 * The thumb keeps its on-state size unless [SwitchTrackSpec.thumbMorphEnabled] is set — see that
 * flag for the AAR / token-sheet split.
 */
fun resolveThumbGeometry(
    checked: Boolean,
    progress: Float,
    spec: SwitchTrackSpec = SwitchTrackSpec(),
): ThumbGeometry {
    val p = progress.coerceIn(0f, 1f)
    val morph = if (spec.thumbMorphEnabled) p else 1f
    val thumbHeight = lerp(spec.thumbOffHeight, spec.thumbOnHeight, morph)
    val thumbWidth = lerp(spec.thumbOffWidth, spec.thumbOnWidth, morph)
    val thumbCorner = lerp(spec.thumbOffCorner, spec.thumbOnCorner, morph)
    val travel = spec.trackWidth - 2f * spec.horizontalInset - thumbWidth
    val thumbOffsetX = spec.horizontalInset + travel * p
    return ThumbGeometry(
        thumbWidth = thumbWidth,
        thumbHeight = thumbHeight,
        thumbCorner = thumbCorner,
        thumbOffsetX = thumbOffsetX,
    )
}

/**
 * Control box (dp). Flyme measures the switch at `mSwitchWidth × mSwitchHeight` = 40 × 22
 * (`Switch.java:481-483`); [SwitchTrackSpec.minTouchTarget] expands it on both axes.
 */
fun switchTouchTargetWidth(spec: SwitchTrackSpec = SwitchTrackSpec()): Float =
    maxOf(spec.minTouchTarget, spec.trackWidth)

fun switchTouchTargetHeight(spec: SwitchTrackSpec = SwitchTrackSpec()): Float =
    maxOf(spec.minTouchTarget, spec.trackHeight)

/**
 * Horizontal travel (dp) for thumb drag → progress mapping.
 * Uses on-state thumb width (Switch.java getThumbScrollRange uses constant mThumbWidth = to_width).
 */
fun switchThumbTravel(spec: SwitchTrackSpec = SwitchTrackSpec()): Float =
    (spec.trackWidth - 2f * spec.horizontalInset - spec.thumbOnWidth).coerceAtLeast(0f)

/** Maps a horizontal drag delta (same unit as [travel]) onto progress in `0f..1f`. */
fun progressAfterDrag(
    startProgress: Float,
    dragDelta: Float,
    travel: Float,
): Float {
    if (travel <= 0f) return startProgress.coerceIn(0f, 1f)
    return (startProgress + dragDelta / travel).coerceIn(0f, 1f)
}

/**
 * Resolve checked intent after drag release — Flyme/AOSP `stopDrag` semantics.
 *
 * 1. If `|velocity| > minFlingVelocity`, direction wins (RTL flips sign).
 * 2. Else midpoint: `progress > 0.5` → on (strict `>`, matches Meizu `getTargetCheckedState`).
 */
fun checkedAfterRelease(
    progress: Float,
    velocityPxPerSec: Float = 0f,
    minFlingVelocityPxPerSec: Float = 0f,
    rtl: Boolean = false,
): Boolean {
    if (minFlingVelocityPxPerSec > 0f && abs(velocityPxPerSec) > minFlingVelocityPxPerSec) {
        return if (rtl) velocityPxPerSec < 0f else velocityPxPerSec > 0f
    }
    return progress.coerceIn(0f, 1f) > 0.5f
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction
