package nota.ui.switch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun ShellSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    spec: SwitchTrackSpec = SwitchTrackSpec(),
    interactionSource: MutableInteractionSource? = null,
) {
    BindSwitchHaptic()

    val progress = remember { Animatable(if (checked) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    LaunchedEffect(checked, dragging) {
        if (!dragging) {
            val target = if (checked) 1f else 0f
            if (abs(progress.value - target) < 0.001f) {
                progress.snapTo(target)
            } else {
                progress.animateTo(
                    targetValue = target,
                    animationSpec =
                        spring(
                            dampingRatio = SwitchAnimationSpec.springDamping(checked),
                            stiffness = SwitchAnimationSpec.SpringStiffness,
                        ),
                )
            }
        }
    }

    val touchTargetWidth = switchTouchTargetWidth(spec)
    val touchTargetHeight = switchTouchTargetHeight(spec)
    val travelDp = switchThumbTravel(spec)
    val travelPx = with(density) { travelDp.dp.toPx() }
    // Compose 1.7 ViewConfiguration has no minimumFlingVelocity. 50dp matches the
    // platform scaled minimum fling velocity the library compared against.
    val minFling = with(density) { 50.dp.toPx() }
    val rtl = layoutDirection == LayoutDirection.Rtl

    val activeTrackColor =
        if (enabled) colors.checkedTrackColor else colors.disabledCheckedTrackColor
    val inactiveTrackColor =
        if (enabled) colors.uncheckedTrackColor else colors.disabledUncheckedTrackColor
    val activeThumbColor =
        if (enabled) colors.checkedThumbColor else colors.disabledCheckedThumbColor
    val inactiveThumbColor =
        if (enabled) colors.uncheckedThumbColor else colors.disabledUncheckedThumbColor

    val draggableState =
        rememberDraggableState { delta ->
            val dragAmount = if (rtl) -delta else delta
            val next =
                progressAfterDrag(
                    startProgress = progress.value,
                    dragDelta = dragAmount,
                    travel = travelPx,
                )
            scope.launch { progress.snapTo(next) }
        }

    val switchModifier =
        modifier
            .semantics(mergeDescendants = false) {
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                if (!enabled) disabled()
            }
            .then(
                if (onCheckedChange != null && enabled) {
                    Modifier
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Horizontal,
                            enabled = true,
                            interactionSource = actualInteractionSource,
                            startDragImmediately = false,
                            onDragStarted = { dragging = true },
                            onDragStopped = { velocity ->
                                val target =
                                    checkedAfterRelease(
                                        progress = progress.value,
                                        velocityPxPerSec = velocity,
                                        minFlingVelocityPxPerSec = minFling,
                                        rtl = rtl,
                                    )
                                scope.launch {
                                    // Meizu stopDrag → setChecked(..., useAnim=false): snap to edge.
                                    progress.snapTo(if (target) 1f else 0f)
                                    dragging = false
                                    val callback = currentOnCheckedChange
                                    if (callback != null && target != currentChecked) {
                                        performSwitchHaptic()
                                        callback(target)
                                    }
                                }
                            },
                        )
                        .clickable(
                            interactionSource = actualInteractionSource,
                            indication = null,
                            role = Role.Switch,
                            onClick = {
                                performSwitchHaptic()
                                currentOnCheckedChange?.invoke(!currentChecked)
                            },
                        )
                } else {
                    Modifier
                },
            )
            .size(width = touchTargetWidth.dp, height = touchTargetHeight.dp)
            .wrapContentSize(Alignment.Center)

    Box(modifier = switchModifier) {
        Canvas(Modifier.size(spec.trackWidth.dp, spec.trackHeight.dp)) {
            val p = progress.value
            val geometry = resolveThumbGeometry(checked = checked, progress = p, spec = spec)

            val trackColor = lerp(inactiveTrackColor, activeTrackColor, p)
            val thumbColor = lerp(inactiveThumbColor, activeThumbColor, p)

            // mc_switch_anim_track.xml is a GradientDrawable capsule, not a smooth corner.
            drawRoundRect(
                color = trackColor,
                cornerRadius = CornerRadius(spec.trackCorner.dp.toPx()),
            )

            val thumbWidthPx = geometry.thumbWidth.dp.toPx()
            val thumbHeightPx = geometry.thumbHeight.dp.toPx()
            val thumbCornerPx = geometry.thumbCorner.dp.toPx()
            val thumbTop = (size.height - thumbHeightPx) / 2f
            val thumbLeft = geometry.thumbOffsetX.dp.toPx()
            // DrawScope does not mirror; Switch.java:866-876 lays the thumb out from the right.
            // iOS thumb corner is half the thumb size, so a round rect is the circle.
            val mirroredLeft = if (rtl) size.width - thumbLeft - thumbWidthPx else thumbLeft
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(mirroredLeft, thumbTop),
                size = Size(thumbWidthPx, thumbHeightPx),
                cornerRadius = CornerRadius(thumbCornerPx),
            )
        }
    }
}
