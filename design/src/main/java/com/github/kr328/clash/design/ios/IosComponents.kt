package com.github.kr328.clash.design.ios

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import nota.ui.ios.shellIosListRowColors
import nota.ui.ios.shellIosListRowSpec
import nota.ui.list.ShellListRow
import nota.ui.settings.PreferenceChevron

enum class IosSelection { None, Checked, Mixed }

data class IosDetail(
    val title: String,
    val value: String? = null,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
    val subtitle: String? = null,
    val subtitleAnnotated: AnnotatedString? = null,
    val selected: Boolean = false,
    val trailing: String? = null,
    val onTrailing: (() -> Unit)? = null,
    val selection: IosSelection = IosSelection.None,
    val disclosure: Boolean = true,
)

internal fun IosDetail.resolvedSelection(): IosSelection = when (selection) {
    IosSelection.Mixed -> IosSelection.Mixed
    IosSelection.Checked -> IosSelection.Checked
    IosSelection.None -> if (selected) IosSelection.Checked else IosSelection.None
}

@Composable
fun IosInsetGroup(
    title: String,
    rows: List<IosDetail>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val colors = IosTheme.colors
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = IosType.footnote,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 6.dp),
        )
        rows.forEachIndexed { index, row ->
            IosPreferenceRow(row = row, index = index, count = rows.size)
        }
    }
}

@Composable
internal fun IosDetailTrailing(
    row: IosDetail,
    showChevron: Boolean,
    chevronColor: Color,
) {
    val colors = IosTheme.colors
    val mark = row.resolvedSelection()
    val valueFollows = mark != IosSelection.None || !row.trailing.isNullOrEmpty() || showChevron
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!row.value.isNullOrEmpty()) {
            Text(
                text = row.value,
                style = IosType.body,
                color = if (row.enabled) colors.secondaryLabel else colors.tertiaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            if (valueFollows) {
                Spacer(Modifier.width(8.dp))
            }
        }
        if (mark != IosSelection.None) {
            IosSelectionMark(mark)
            if (!row.trailing.isNullOrEmpty() || showChevron) {
                Spacer(Modifier.width(8.dp))
            }
        }
        if (!row.trailing.isNullOrEmpty()) {
            val trail = remember(row.trailing) { MutableInteractionSource() }
            val trailPressed by trail.collectIsPressedAsState()
            Text(
                text = row.trailing,
                style = IosType.body,
                color = if (row.enabled && row.onTrailing != null) colors.blue else colors.tertiaryLabel,
                modifier = Modifier
                    .background(if (trailPressed && row.onTrailing != null) colors.pressed else Color.Transparent)
                    .then(
                        if (row.enabled && row.onTrailing != null) {
                            Modifier.clickable(
                                interactionSource = trail,
                                indication = null,
                                onClick = row.onTrailing,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        } else if (showChevron) {
            PreferenceChevron(color = chevronColor, chevronSize = 13.dp)
        }
    }
}

internal fun IosDetail.hasCustomTrailing(): Boolean =
    resolvedSelection() != IosSelection.None || !value.isNullOrEmpty() || !trailing.isNullOrEmpty()

@Composable
internal fun IosSelectionMark(selection: IosSelection) {
    if (selection == IosSelection.None) return
    val color = IosTheme.colors.blue
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (selection == IosSelection.Mixed) {
            val y = size.height / 2f
            drawLine(
                color = color,
                start = Offset(size.width * 0.22f, y),
                end = Offset(size.width * 0.78f, y),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.18f, size.height * 0.52f)
                lineTo(size.width * 0.40f, size.height * 0.74f)
                lineTo(size.width * 0.82f, size.height * 0.28f)
            }
            drawPath(path, color, style = stroke)
        }
    }
}

/**
 * Plain list row. Padding and the hairline come from the copied iOS list spec,
 * so the parent list should not add another 16dp of horizontal padding.
 * [showDivider] is for every row except the last.
 */
@Composable
fun IosListRow(row: IosDetail, showDivider: Boolean = false) {
    val colors = shellIosListRowColors()
    val custom = row.hasCustomTrailing()
    val clickable = row.enabled && row.onClick != null
    val showChevron = clickable && row.disclosure && row.trailing.isNullOrEmpty()
    val annotated = row.subtitleAnnotated?.takeIf { it.isNotEmpty() }
    ShellListRow(
        title = row.title,
        summary = if (annotated == null) row.subtitle else null,
        summaryAnnotated = annotated,
        onClick = if (clickable) row.onClick else null,
        enabled = row.enabled,
        showDivider = showDivider,
        showArrow = clickable && row.disclosure && !custom,
        spec = shellIosListRowSpec(),
        colors = colors,
        trailing = if (custom) {
            {
                IosDetailTrailing(
                    row = row,
                    showChevron = showChevron,
                    chevronColor = if (row.enabled) colors.trailingColor else colors.disabledTrailingColor,
                )
            }
        } else {
            null
        },
    )
}

@Composable
fun IosSegmentedControl(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.track)
            .padding(2.dp),
    ) {
        items.forEachIndexed { index, label ->
            val active = index == selected
            val interaction = remember(index) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .then(
                        if (active) {
                            Modifier.shadow(1.dp, RoundedCornerShape(7.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) colors.segment else Color.Transparent)
                    .semantics { role = Role.Tab }
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = IosType.segment,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

enum class IosButtonStyle { Filled, Destructive }

@Composable
fun IosButton(
    text: String,
    style: IosButtonStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background = when (style) {
        IosButtonStyle.Filled -> colors.blue
        IosButtonStyle.Destructive -> colors.red.copy(alpha = 0.12f)
    }
    val content = when (style) {
        IosButtonStyle.Filled -> colors.onBlue
        IosButtonStyle.Destructive -> colors.red
    }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) background.copy(alpha = background.alpha * 0.82f) else background)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = IosType.headline, color = content)
    }
}

internal val IosNavBarHeight = 44.dp

@Composable
fun IosNavBar(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(IosNavBarHeight)
            .background(colors.background),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .semantics { role = Role.Button }
                .clickable(interactionSource = interaction, indication = null, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = colors.blue,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            style = IosType.headline,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = if (trailing == null) 56.dp else 96.dp),
        )
        if (trailing != null) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .heightIn(min = 44.dp)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                trailing()
            }
        }
    }
}

@Composable
fun IosNavIcon(
    icon: Painter,
    description: String,
    onClick: () -> Unit,
    tint: Color = IosTheme.colors.blue,
) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(44.dp)
            .background(if (pressed) colors.pressed else Color.Transparent)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

data class TrafficSample(val up: Float, val down: Float)

@Composable
fun IosModePicker(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            val interaction = remember(index) { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            active -> colors.blue
                            pressed -> colors.pressed
                            else -> colors.grouped
                        },
                    )
                    .semantics { role = Role.Tab }
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = IosType.headline,
                    color = if (active) colors.onBlue else colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
                )
            }
        }
    }
}

@Composable
fun TrafficSparkline(
    samples: List<TrafficSample>,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        if (samples.size < 2) return@Canvas
        val max = samples.maxOf { maxOf(it.up, it.down) }.coerceAtLeast(1f)
        fun stroke(select: (TrafficSample) -> Float, color: Color) {
            val path = Path()
            samples.forEachIndexed { index, sample ->
                val x = index / samples.lastIndex.toFloat() * size.width
                val y = size.height - (select(sample) / max) * (size.height - 4f) - 2f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        stroke({ it.up }, colors.green)
        stroke({ it.down }, colors.blue)
    }
}

/**
 * Full-bleed row with 44dp icons beside it. The row click stays on [row].
 * [beforeAction] is an extra icon drawn before the main action, such as update.
 * [below] sits under the row and above the divider, for a usage bar.
 */
@Composable
fun IosSideActionRow(
    row: IosDetail,
    actionIcon: Painter,
    actionDescription: String,
    onAction: () -> Unit,
    showDivider: Boolean,
    beforeAction: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val colors = IosTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.grouped)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                IosListRow(row, showDivider = false)
            }
            if (beforeAction != null) beforeAction()
            IosNavIcon(
                icon = actionIcon,
                description = actionDescription,
                onClick = onAction,
            )
        }
        if (below != null) below()
        if (showDivider) {
            Box(
                Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.separator),
            )
        }
    }
}

/** True when the text fits the 160dp trailing slot. A CJK character counts as two Latin units. */
internal fun String.fitsTrailingValue(): Boolean {
    if (isBlank() || contains('\n')) return false
    var units = 0
    for (ch in this) {
        units += if (ch.code <= 0x00FF) 1 else 2
        if (units > 16) return false
    }
    return true
}

/** Lets a URL wrap after a slash instead of leaving one path segment on the next line. */
internal fun String.breakAfterSlash(): String = replace("/", "/\u200b")
