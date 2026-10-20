package nota.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Right-pointing chevron drawn as vector strokes (ref: mz_right_arrow).
 * Character glyphs such as `›` are forbidden.
 */
@Composable
internal fun PreferenceChevron(
    color: Color,
    chevronSize: Dp,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.size(chevronSize)) {
        val width = size.width
        val height = size.height
        val strokeWidth = (width * 0.1f).coerceAtLeast(1.25f)
        val path =
            Path().apply {
                val insetX = width * 0.3f
                val insetY = height * 0.22f
                if (rtl) {
                    moveTo(width - insetX, insetY)
                    lineTo(insetX, height / 2f)
                    lineTo(width - insetX, height - insetY)
                } else {
                    moveTo(insetX, insetY)
                    lineTo(width - insetX, height / 2f)
                    lineTo(insetX, height - insetY)
                }
            }
        drawPath(
            path = path,
            color = color,
            style =
                Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
        )
    }
}
