package nota.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline

/**
 * Press tint for copied list rows and preference cards.
 *
 * The library reads this from `LocalShellTheme`. This module supplies the tint
 * through [LocalShellPressTint] so screens can follow `UiStore.darkMode`.
 */
val LocalShellPressTint = staticCompositionLocalOf { Color(0x1A767680) }

private val PressEasing = CubicBezierEasing(0.4f, 0f, 0.58f, 1f)
private const val PressDurationMs = 100
private const val ReleaseDurationMs = 300
private const val ForegroundPressMs = 80
private const val ForegroundReleaseMs = 150

/**
 * Foreground press for tabs and icon toggles: the glyph fades to 40% and does not gain a plate.
 */
@Composable
fun rememberShellPressAlpha(
    interactionSource: MutableInteractionSource,
    pressedAlpha: Float = 0.4f,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (pressed) ForegroundPressMs else ForegroundReleaseMs,
            easing = PressEasing,
        ),
        label = "shellPressForeground",
    )
    return 1f - (1f - pressedAlpha) * progress
}

@Composable
fun Modifier.shellPressBackground(
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    tint: Color? = null,
): Modifier {
    if (!enabled) return this

    val resolved = tint ?: LocalShellPressTint.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (pressed) PressDurationMs else ReleaseDurationMs,
            easing = PressEasing,
        ),
        label = "shellPressBackground",
    )

    return drawBehind {
        if (progress <= 0f) return@drawBehind
        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            color = resolved.copy(alpha = resolved.alpha * progress),
        )
    }
}
