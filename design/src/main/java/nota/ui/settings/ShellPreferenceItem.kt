package nota.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nota.ui.theme.SHELL_LINE_HEIGHT_RATIO
import nota.ui.theme.shellPressBackground

/**
 * Settings preference row — title + optional summary + trailing widget + divider.
 * ref: mz_preference / mz_preference_singleline / immersive_mz_preference_singleline_*
 *
 * Pass [groupPosition] for Flyme immersive grouped cards (10dp rounded corners, inset
 * horizontally by [PreferenceItemSpec.immersiveCardInset]).
 */
@Composable
fun ShellPreferenceItem(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean? = null,
    showArrow: Boolean = true,
    groupPosition: PreferenceGroupPosition? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    spec: PreferenceItemSpec = PreferenceItemSpec(),
    colors: PreferenceItemColors = PreferenceItemDefaults.colors(),
) {
    val hasSummary = !summary.isNullOrEmpty()
    val paddingTop = resolvePreferencePaddingTop(hasSummary = hasSummary, spec = spec, groupPosition = groupPosition)
    val paddingBottom = resolvePreferencePaddingBottom(hasSummary = hasSummary, spec = spec, groupPosition = groupPosition)
    val horizontalPadding = resolvePreferenceContentHorizontalPadding(spec = spec, groupPosition = groupPosition)
    val widgetGap = resolvePreferenceWidgetGap(spec = spec, groupPosition = groupPosition)
    val minHeight = resolvePreferenceMinHeight(hasSummary = hasSummary, spec = spec)
    val effectiveShowDivider = showDivider ?: (groupPosition == null)

    val titleColor = if (enabled) colors.titleColor else colors.disabledTitleColor
    val summaryColor = if (enabled) colors.summaryColor else colors.disabledSummaryColor
    val arrowColor = if (enabled) colors.arrowColor else colors.disabledArrowColor
    val titleLineHeight = spec.titleTextSize * SHELL_LINE_HEIGHT_RATIO
    val summaryLineHeight = spec.summaryTextSize * SHELL_LINE_HEIGHT_RATIO + spec.summaryLineSpacingExtra

    val interactionSource = remember { MutableInteractionSource() }
    val groupShape = preferenceGroupShape(groupPosition, spec)
    val rowBackground =
        if (groupPosition != null) {
            Modifier
                .padding(horizontal = spec.immersiveCardInset.dp)
                .clip(groupShape)
                .background(colors.containerColor, groupShape)
        } else {
            Modifier
        }

    Column(modifier = modifier.fillMaxWidth()) {
        val rowModifier =
            Modifier
                .fillMaxWidth()
                .then(rowBackground)
                .shellPressBackground(
                    shape = groupShape,
                    enabled = onClick != null && enabled,
                    interactionSource = interactionSource,
                )
                .defaultMinSize(minHeight = minHeight.dp)
                .then(
                    if (onClick != null && enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(
                    start = horizontalPadding.dp,
                    top = paddingTop.dp,
                    end = horizontalPadding.dp,
                    bottom = paddingBottom.dp,
                )
                .semantics(mergeDescendants = true) {
                    if (onClick != null) role = Role.Button
                    if (!enabled) disabled()
                }

        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(spec.leadingGap.dp))
            }

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
                    Spacer(modifier = Modifier.height(spec.titleSummaryGap.dp))
                    PreferenceSummaryText(
                        text = summary.orEmpty(),
                        color = summaryColor,
                        fontSizeSp = spec.summaryTextSize,
                        lineHeightSp = summaryLineHeight,
                    )
                }
            }

            when {
                trailing != null -> {
                    Spacer(modifier = Modifier.width(widgetGap.dp))
                    Box(
                        modifier = Modifier.defaultMinSize(minWidth = spec.widgetMinWidth.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        trailing()
                    }
                }
                showArrow -> {
                    Spacer(modifier = Modifier.width(widgetGap.dp))
                    Box(
                        modifier = Modifier.defaultMinSize(minWidth = spec.widgetMinWidth.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PreferenceChevron(
                            color = arrowColor,
                            chevronSize = spec.arrowSize.dp,
                        )
                    }
                }
            }
        }

        if (effectiveShowDivider) {
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

/**
 * Keeps a one-character last line off preference summaries.
 * The adjustment uses the text that was actually drawn. If that line still
 * fits when the font is at least 92% of the requested size, it stays one line.
 * Otherwise the last line takes about two extra ems from the line above.
 */
@Composable
private fun PreferenceSummaryText(
    text: String,
    color: Color,
    fontSizeSp: Float,
    lineHeightSp: Float,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxPx = constraints.maxWidth
        var scale by remember(text, maxPx) { mutableFloatStateOf(1f) }
        var endPad by remember(text, maxPx) { mutableStateOf(0.dp) }
        BasicText(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = endPad),
            style =
                TextStyle(
                    color = color,
                    fontSize = (fontSizeSp * scale).sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = (lineHeightSp * scale).sp,
                ),
            onTextLayout = { result ->
                if (result.lineCount <= 1 || maxPx <= 0) return@BasicText
                val last = result.lineCount - 1
                val start = result.getLineStart(last)
                val end = result.getLineEnd(last, visibleEnd = true).coerceAtMost(text.length)
                if (text.substring(start, end).trim().length > 1) return@BasicText
                val natural = result.multiParagraph.maxIntrinsicWidth
                val fitted = if (natural > 0f) (maxPx - 4f) / natural else 0f
                val next = minOf(fitted, scale * 0.97f)
                if (next >= 0.92f && next < scale - 0.004f) {
                    scale = next
                    return@BasicText
                }
                if (endPad > 0.dp) return@BasicText
                val em = with(density) { (fontSizeSp * scale).sp.toPx() }
                val lastWidth = result.getLineRight(last) - result.getLineLeft(last)
                val guard = (lastWidth + em).coerceIn(0f, maxPx * 0.34f)
                endPad = with(density) { guard.toDp() }
            },
        )
    }
}

/**
 * ref: `ImmersiveListItemDrawable.inflate` builds the card with
 * `ShapeAppearanceModel.setAllCorners(RoundedCornerTreatment())` — a plain arc, not a
 * smooth corner, despite the `fd_sys_radius_corner_smooth_small` dimen name.
 */
private fun preferenceGroupShape(
    groupPosition: PreferenceGroupPosition?,
    spec: PreferenceItemSpec,
): Shape {
    val top = resolvePreferenceGroupCornerTop(groupPosition, spec).dp
    val bottom = resolvePreferenceGroupCornerBottom(groupPosition, spec).dp
    if (top == 0.dp && bottom == 0.dp) return RectangleShape
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomEnd = bottom,
        bottomStart = bottom,
    )
}
