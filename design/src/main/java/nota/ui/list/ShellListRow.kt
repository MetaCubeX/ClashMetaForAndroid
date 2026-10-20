@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package nota.ui.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nota.ui.theme.SHELL_LINE_HEIGHT_RATIO
import nota.ui.theme.shellPressBackground

@Composable
fun ShellListRow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    variant: ListRowVariant = ListRowVariant.SingleLine,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    spec: ListRowSpec = ListRowSpec(),
    colors: ListRowColors = ListRowDefaults.colors(),
    content: @Composable RowScope.() -> Unit,
) {
    ShellListRowImpl(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        enabled = enabled,
        showDivider = showDivider,
        onLongClick = onLongClick,
        leading = leading,
        trailing = trailing,
        spec = spec,
        colors = colors,
        content = content,
    )
}

/**
 * Convenience overload — title + optional summary with Origin double-line typography.
 * When [variant] is [ListRowVariant.DoubleLine], callers may still use the slot API and
 * place two lines inside [content] themselves.
 */
@Composable
fun ShellListRow(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryAnnotated: AnnotatedString? = null,
    variant: ListRowVariant =
        if (summary.isNullOrEmpty() && summaryAnnotated.isNullOrEmpty()) {
            ListRowVariant.SingleLine
        } else {
            ListRowVariant.DoubleLine
        },
    enabled: Boolean = true,
    showDivider: Boolean = false,
    showArrow: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    spec: ListRowSpec = ListRowSpec(),
    colors: ListRowColors = ListRowDefaults.colors(),
) {
    val effectiveVariant =
        when {
            showArrow && variant == ListRowVariant.SingleLine -> ListRowVariant.SingleLineArrow
            else -> variant
        }
    val effectiveTrailing =
        trailing ?: if (showArrow && variant == ListRowVariant.DoubleLine) {
            val trailingColor = if (enabled) colors.trailingColor else colors.disabledTrailingColor
            { ListRowChevron(color = trailingColor, size = spec.arrowSize) }
        } else {
            null
        }

    ShellListRowImpl(
        onClick = onClick,
        modifier = modifier,
        variant = effectiveVariant,
        enabled = enabled,
        showDivider = showDivider,
        onLongClick = onLongClick,
        leading = leading,
        trailing = effectiveTrailing,
        spec = spec,
        colors = colors,
    ) {
        ListRowTitleSummaryContent(
            title = title,
            summary = summary,
            summaryAnnotated = summaryAnnotated,
            spec = spec,
            colors = colors,
            enabled = enabled,
            useDoubleLineLayout = variant == ListRowVariant.DoubleLine,
        )
    }
}

@Composable
private fun ShellListRowImpl(
    onClick: (() -> Unit)?,
    modifier: Modifier,
    variant: ListRowVariant,
    enabled: Boolean,
    showDivider: Boolean,
    onLongClick: (() -> Unit)?,
    leading: @Composable (() -> Unit)?,
    trailing: @Composable (() -> Unit)?,
    spec: ListRowSpec,
    colors: ListRowColors,
    content: @Composable RowScope.() -> Unit,
) {
    val verticalPadding = resolveListRowVerticalPaddingForVariant(variant, spec)
    val trailingColor = if (enabled) colors.trailingColor else colors.disabledTrailingColor
    val interactionSource = remember { MutableInteractionSource() }
    val isInteractive = onClick != null || onLongClick != null

    val effectiveTrailing =
        trailing ?: if (variant == ListRowVariant.SingleLineArrow) {
            { ListRowChevron(color = trailingColor, size = spec.arrowSize) }
        } else {
            null
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.containerColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = spec.minHeight.dp)
                    .shellPressBackground(
                        enabled = isInteractive && enabled,
                        interactionSource = interactionSource,
                    )
                    .listRowClickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    .padding(
                        horizontal = spec.horizontalPadding.dp,
                        vertical = verticalPadding.dp,
                    )
                    .semantics {
                        if (!enabled) disabled()
                        if (isInteractive) role = Role.Button
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(spec.leadingGap.dp))
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )

            if (effectiveTrailing != null) {
                Spacer(Modifier.width(spec.trailingGap.dp))
                effectiveTrailing()
            }
        }

        if (showDivider) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = spec.dividerInsetStart.dp, end = spec.dividerInsetEnd.dp)
                        .height(spec.dividerThickness.dp)
                        .background(colors.dividerColor),
            )
        }
    }
}

@Composable
private fun RowScope.ListRowTitleSummaryContent(
    title: String,
    summary: String?,
    summaryAnnotated: AnnotatedString?,
    spec: ListRowSpec,
    colors: ListRowColors,
    enabled: Boolean,
    useDoubleLineLayout: Boolean,
) {
    val titleColor = if (enabled) colors.titleColor else colors.disabledTitleColor
    val summaryColor = if (enabled) colors.summaryColor else colors.disabledSummaryColor
    val annotated = summaryAnnotated?.takeIf { it.isNotEmpty() }
    val hasSummary = annotated != null || !summary.isNullOrEmpty()

    val titleLineHeight = spec.titleTextSize * SHELL_LINE_HEIGHT_RATIO
    val summaryLineHeight = spec.summaryTextSize * SHELL_LINE_HEIGHT_RATIO

    if (useDoubleLineLayout) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style =
                    TextStyle(
                        color = titleColor,
                        fontSize = spec.titleTextSize.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = titleLineHeight.sp,
                    ),
            )
            if (hasSummary) {
                Spacer(Modifier.height(spec.titleSummaryGap.dp))
                val summaryStyle = TextStyle(
                    color = summaryColor,
                    fontSize = spec.summaryTextSize.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = summaryLineHeight.sp,
                )
                if (annotated != null) {
                    BasicText(text = annotated, style = summaryStyle)
                } else {
                    BasicText(text = summary.orEmpty(), style = summaryStyle)
                }
            }
        }
    } else {
        BasicText(
            text = title,
            modifier = Modifier.weight(1f),
            style =
                TextStyle(
                    color = titleColor,
                    fontSize = spec.titleTextSize.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = titleLineHeight.sp,
                ),
        )
    }
}

private fun Modifier.listRowClickable(
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
): Modifier =
    when {
        onClick != null && onLongClick != null ->
            combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        onClick != null ->
            clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
        onLongClick != null ->
            pointerInput(enabled, onLongClick) {
                if (!enabled) return@pointerInput
                detectTapGestures(onLongPress = { onLongClick() })
            }
        else -> this
    }

@Composable
private fun ListRowChevron(
    color: androidx.compose.ui.graphics.Color,
    size: Float,
) {
    Canvas(Modifier.size(size.dp)) {
        val strokeWidth = (size.dp.toPx() * 0.09f).coerceAtLeast(1f)
        val path =
            Path().apply {
                val w = this@Canvas.size.width
                val h = this@Canvas.size.height
                moveTo(w * 0.32f, h * 0.22f)
                lineTo(w * 0.68f, h * 0.5f)
                lineTo(w * 0.32f, h * 0.78f)
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
