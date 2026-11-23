package nota.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The only corner radii in the app: 0, 4, 8, 12, 16, and a pill.
 * Screens take a [ShellShapes] value instead of calling `RoundedCornerShape` with a raw number.
 */
object ShellCorners {
    val None = 0.dp
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
}

object ShellShapes {
    val None: Shape = RectangleShape
    val Tag: Shape = RoundedCornerShape(ShellCorners.Xs)
    val Control: Shape = RoundedCornerShape(ShellCorners.Sm)
    val Popup: Shape = RoundedCornerShape(ShellCorners.Md)
    val Container: Shape = RoundedCornerShape(ShellCorners.Lg)
    val SheetTop: Shape = RoundedCornerShape(
        topStart = ShellCorners.Lg,
        topEnd = ShellCorners.Lg,
    )
    val Pill: Shape = RoundedCornerShape(percent = 50)
}
