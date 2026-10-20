@file:Suppress("FunctionName")

package universe.agentic.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clear (✕) control with parent-owned horizontal hit slot.
 *
 * - [layoutWidth] — measured width reported to the parent Row (= touch).
 * - [visualHeight] — measured height reported to the parent (keep ≤ row height).
 * - [touchTargetSize] — centered interactive extent; may overflow vertically via
 *   `requiredSize` without stretching the search row.
 */
@Composable
fun SearchFieldClearButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    layoutWidth: Dp = SearchFieldDimens.Default.touchTargetMin.dp,
    visualHeight: Dp = SearchFieldDimens.Default.clearButtonSize.dp,
    touchTargetSize: Dp = SearchFieldDimens.Default.touchTargetMin.dp,
    iconSize: Dp = SearchFieldDimens.Default.iconSize.dp,
    tint: Color = Color(0x993C3C43),
    contentDescription: String = "Clear",
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .width(layoutWidth)
                .height(visualHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .requiredSize(touchTargetSize)
                    .semantics { this.contentDescription = contentDescription }
                    .clip(CircleShape)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(iconSize)) {
                val inset = size.minDimension * 0.22f
                val strokeWidth = size.minDimension * 0.12f
                drawLine(
                    color = tint,
                    start = Offset(inset, inset),
                    end = Offset(size.width - inset, size.height - inset),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width - inset, inset),
                    end = Offset(inset, size.height - inset),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
