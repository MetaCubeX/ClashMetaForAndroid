package universe.agentic.clash
import androidx.compose.ui.res.painterResource

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import nota.ui.theme.rememberShellPressAlpha
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import nota.ui.ios.LocalShellIosDark
import nota.ui.ios.shellIosListRowColors
import nota.ui.ios.shellIosListRowSpec
import nota.ui.list.ShellListRow
import nota.ui.settings.PreferenceChevron
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import nota.ui.theme.ShellShapes
import nota.ui.theme.shellPressBackground

enum class IosSelection { None, Checked, Mixed }

data class IosDetail(
    val title: String,
    val modifier: Modifier = Modifier,
    val value: String? = null,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
    val subtitle: String? = null,
    val subtitleAnnotated: AnnotatedString? = null,
    val selected: Boolean = false,
    val trailing: String? = null,
    val trailingIcon: Int? = null,
    val trailingIconDescription: String? = null,
    val onTrailing: (() -> Unit)? = null,
    val selection: IosSelection = IosSelection.None,
    val disclosure: Boolean = true,
    val exclusive: Boolean = false,
    val checkable: Boolean = false,
    val emphasized: Boolean = false,
    val highlight: Boolean = true,
    val leadingIcon: Int? = null,
    val valueColor: Color? = null,
    val badge: String? = null,
    val badgeColor: Color? = null,
    val external: Boolean = false,
    val externalDescription: String? = null,
    val spokenDescription: String? = null,
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
            style = IosType.section,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
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
    val showChoice = row.exclusive
    val showCheck = row.checkable || (!showChoice && mark != IosSelection.None)
    val showExternal = row.external && !showChevron
    val showTrailingIcon = row.trailingIcon != null
    val afterValue = showChoice || showCheck || !row.trailing.isNullOrEmpty() ||
        showTrailingIcon || showChevron || showExternal
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!row.badge.isNullOrEmpty()) {
            IosStatusPill(text = row.badge, color = row.badgeColor ?: colors.blue)
            if (!row.value.isNullOrEmpty() || afterValue) {
                Spacer(Modifier.width(8.dp))
            }
        }
        if (!row.value.isNullOrEmpty()) {
            Text(
                text = row.value,
                style = IosType.footnote,
                color = row.valueColor ?: if (row.enabled) colors.secondaryLabel else colors.tertiaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            if (afterValue) {
                Spacer(Modifier.width(8.dp))
            }
        }
        if (showChoice) {
            IosChoiceMark(selected = mark == IosSelection.Checked)
            if (!row.trailing.isNullOrEmpty() || showChevron) {
                Spacer(Modifier.width(8.dp))
            }
        } else if (showCheck) {
            IosCheckBox(if (row.checkable) row.selection else mark)
            if (!row.trailing.isNullOrEmpty() || showChevron) {
                Spacer(Modifier.width(8.dp))
            }
        }
        if (!row.trailing.isNullOrEmpty()) {
            val trail = remember(row.trailing) { MutableInteractionSource() }
            val actionable = row.enabled && row.onTrailing != null
            Box(
                Modifier
                    .height(22.dp)
                    .wrapContentHeight(unbounded = true, align = Alignment.CenterVertically),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(ShellShapes.Pill)
                        .shellPressBackground(
                            ShellShapes.Pill,
                            enabled = actionable,
                            interactionSource = trail,
                        )
                        .then(
                            if (actionable) {
                                Modifier.clickable(
                                    interactionSource = trail,
                                    indication = null,
                                    onClick = row.onTrailing,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = row.trailing,
                        style = IosType.headline,
                        color = if (actionable) colors.blue else colors.tertiaryLabel,
                    )
                }
            }
        } else if (showTrailingIcon) {
            Icon(
                painter = painterResource(row.trailingIcon!!),
                contentDescription = row.trailingIconDescription,
                tint = chevronColor,
                modifier = Modifier.size(16.dp),
            )
        } else if (showChevron) {
            PreferenceChevron(color = chevronColor, chevronSize = 16.dp)
        } else if (showExternal) {
            ExternalLinkMark(
                color = chevronColor,
                description = row.externalDescription ?: stringResource(R.string.opens_in_browser),
            )
        }
    }
}

@Composable
private fun ExternalLinkMark(color: Color, description: String) {
    Canvas(
        Modifier
            .size(16.dp)
            .semantics { contentDescription = description },
    ) {
        val stroke = 1.5.dp.toPx()
        val inset = size.minDimension * 0.16f
        val right = size.width - inset
        val bottom = size.height - inset
        val boxRight = size.width * 0.68f
        val boxTop = size.height * 0.36f
        drawLine(color, Offset(inset, boxTop), Offset(inset, bottom), stroke, StrokeCap.Round)
        drawLine(color, Offset(inset, bottom), Offset(boxRight, bottom), stroke, StrokeCap.Round)
        drawLine(color, Offset(boxRight, bottom), Offset(boxRight, boxTop + size.height * 0.16f), stroke, StrokeCap.Round)
        drawLine(color, Offset(inset, boxTop), Offset(boxRight - size.width * 0.16f, boxTop), stroke, StrokeCap.Round)
        val tip = Offset(right, inset)
        drawLine(color, Offset(size.width * 0.40f, size.height * 0.60f), tip, stroke, StrokeCap.Round)
        drawLine(color, tip, Offset(right - size.width * 0.30f, inset), stroke, StrokeCap.Round)
        drawLine(color, tip, Offset(right, inset + size.height * 0.30f), stroke, StrokeCap.Round)
    }
}

internal fun IosDetail.hasCustomTrailing(): Boolean =
    exclusive ||
        checkable ||
        resolvedSelection() != IosSelection.None ||
        !value.isNullOrEmpty() ||
        !trailing.isNullOrEmpty() ||
        trailingIcon != null ||
        !badge.isNullOrEmpty() ||
        external

@Composable
fun IosStatusPill(text: String, color: Color, contentColor: Color = Color.White) {
    Box(
        Modifier
            .height(18.dp)
            .clip(ShellShapes.Pill)
            .background(color)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = IosType.caption2,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Green under 80 ms, orange under 160 ms, red after that. Zero and timeouts stay uncolored. */
fun delayTone(delay: Int, colors: IosColors): Color? {
    if (delay <= 0 || delay > Short.MAX_VALUE) return null
    return when {
        delay < 80 -> colors.green
        delay < 160 -> colors.orange
        else -> colors.red
    }
}

/**
 * Leading glyph of a row. Nothing is drawn behind it: the glyph sits in the row's 28dp slot in a
 * colour of its own, so rows next to each other can be told apart at a glance.
 */
@Composable
internal fun IosLeadingTile(icon: Int) {
    val hue = leadingGlyphHue(icon)
    Box(
        Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (LocalShellIosDark.current) hue.dark else hue.light,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Each colour keeps 3:1 against the card of its theme. */
private enum class LeadingGlyphHue(val light: Color, val dark: Color) {
    Blue(Color(0xFF007AFF), Color(0xFF0A84FF)),
    Green(Color(0xFF28A745), Color(0xFF30D158)),
    Orange(Color(0xFFE07400), Color(0xFFFF9F0A)),
    Purple(Color(0xFFAF52DE), Color(0xFFBF5AF2)),
    Teal(Color(0xFF1A9DB5), Color(0xFF40C8E0)),
    Indigo(Color(0xFF5856D6), Color(0xFF7D7AFF)),
    Brown(Color(0xFFA2845E), Color(0xFFAC8E68)),
    Gray(Color(0xFF8E8E93), Color(0xFF98989D)),
}

/**
 * One colour per glyph, wherever it appears, chosen so that the rows that sit next to each other
 * on the settings, about and home pages differ. A glyph that is not listed is gray.
 */
private fun leadingGlyphHue(icon: Int): LeadingGlyphHue = when (icon) {
    R.drawable.ic_baseline_settings -> LeadingGlyphHue.Gray
    R.drawable.ic_baseline_vpn_lock -> LeadingGlyphHue.Green
    R.drawable.ic_baseline_edit -> LeadingGlyphHue.Orange
    R.drawable.ic_baseline_meta -> LeadingGlyphHue.Purple
    R.drawable.ic_baseline_sync -> LeadingGlyphHue.Blue
    R.drawable.ic_baseline_extension -> LeadingGlyphHue.Teal
    R.drawable.ic_outline_info -> LeadingGlyphHue.Blue
    R.drawable.ic_baseline_qr_code -> LeadingGlyphHue.Indigo
    R.drawable.ic_baseline_apps -> LeadingGlyphHue.Indigo
    R.drawable.ic_baseline_cloud_upload -> LeadingGlyphHue.Blue
    R.drawable.ic_baseline_cloud_download -> LeadingGlyphHue.Green
    R.drawable.ic_baseline_assignment -> LeadingGlyphHue.Orange
    R.drawable.ic_outline_folder -> LeadingGlyphHue.Brown
    R.drawable.ic_outline_article -> LeadingGlyphHue.Teal
    else -> LeadingGlyphHue.Gray
}

@Composable
fun IosChoiceMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = IosTheme.colors
    val stroke by animateFloatAsState(
        targetValue = if (selected) 4f else 1.5f,
        animationSpec = tween(durationMillis = 200),
        label = "choiceStroke",
    )
    val ring by animateColorAsState(
        targetValue = if (selected) colors.blue else colors.choiceRing,
        animationSpec = tween(durationMillis = 200),
        label = "choiceRing",
    )
    Canvas(modifier.size(20.dp)) {
        val width = stroke.dp.toPx()
        drawCircle(
            color = ring,
            radius = (size.minDimension - width) / 2f,
            style = Stroke(width),
        )
    }
}

@Composable
fun IosCheckBox(selection: IosSelection) {
    val colors = IosTheme.colors
    val shape = ShellShapes.Tag
    Box(
        Modifier
            .size(20.dp)
            .clip(shape)
            .then(
                if (selection == IosSelection.None) {
                    Modifier.border(1.5.dp, colors.choiceRing, shape)
                } else {
                    Modifier.background(colors.blue)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selection == IosSelection.None) return@Box
        Canvas(Modifier.size(12.dp)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (selection == IosSelection.Mixed) {
                val y = size.height / 2f
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.15f, y),
                    end = Offset(size.width * 0.85f, y),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            } else {
                val path = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.52f)
                    lineTo(size.width * 0.40f, size.height * 0.78f)
                    lineTo(size.width * 0.88f, size.height * 0.22f)
                }
                drawPath(path, Color.White, style = stroke)
            }
        }
    }
}

@Composable
internal fun IosMenuCheck() {
    val color = IosTheme.colors.secondaryLabel
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.15.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.52f)
            lineTo(size.width * 0.40f, size.height * 0.74f)
            lineTo(size.width * 0.84f, size.height * 0.26f)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
internal fun IosSelectionMark(selection: IosSelection) {
    if (selection == IosSelection.None) return
    IosCheckBox(selection)
}

/**
 * Circular icon button. The highlight is a circle behind the glyph.
 * The hit target stays 44dp and does not change the layout size of [modifier].
 */
@Composable
fun IosIconButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = IosTheme.colors.label,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val hit = 44.dp
    Box(modifier.size(hit), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(hit)
                .semantics { role = Role.Button }
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .shellPressBackground(CircleShape, interactionSource = interaction),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = description,
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

/**
 * Plain list row. Padding and the hairline come from the copied iOS list spec,
 * so the parent list should not add another 16dp of horizontal padding.
 * [showDivider] is for every row except the last.
 */
@Composable
fun IosListRow(
    row: IosDetail,
    showDivider: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = shellIosListRowColors()
    val custom = row.hasCustomTrailing()
    val clickable = row.enabled && row.onClick != null
    val showChevron = clickable && row.disclosure && row.trailing.isNullOrEmpty()
    val annotated = row.subtitleAnnotated?.takeIf { it.isNotEmpty() }
    val leading = if (row.leadingIcon != null) {
        @Composable {
            IosLeadingTile(icon = row.leadingIcon)
        }
    } else {
        null
    }
    ShellListRow(
        title = row.title,
        modifier = modifier,
        summary = if (annotated == null) row.subtitle else null,
        summaryAnnotated = annotated,
        onClick = if (clickable) row.onClick else null,
        enabled = row.enabled,
        showDivider = showDivider,
        showArrow = clickable && row.disclosure && !custom,
        spec = shellIosListRowSpec(),
        colors = colors,
        emphasized = row.emphasized,
        highlight = row.emphasized && row.highlight,
        leading = leading,
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
    horizontalInset: Dp = 16.dp,
) {
    val colors = IosTheme.colors
    val interactions = remember(items.size) { List(items.size) { MutableInteractionSource() } }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalInset)
            .height(40.dp)
            .wrapContentHeight(align = Alignment.CenterVertically, unbounded = true),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.Center)
                .clip(ShellShapes.Pill)
                .background(colors.track)
                .padding(3.dp),
        ) {
            items.forEachIndexed { index, label ->
                val active = index == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(ShellShapes.Pill)
                        .background(if (active) colors.segment else Color.Transparent)
                        .shellPressBackground(ShellShapes.Pill, interactionSource = interactions[index]),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = IosType.segment.copy(
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (active) colors.label else colors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(44.dp)) {
            items.forEachIndexed { index, _ ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { role = Role.Tab }
                        .clickable(
                            interactionSource = interactions[index],
                            indication = null,
                            onClick = { onSelect(index) },
                        ),
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
    val filled = style == IosButtonStyle.Filled
    val background = if (filled) colors.blue else Color.Transparent
    val content = when (style) {
        IosButtonStyle.Filled -> colors.onBlue
        IosButtonStyle.Destructive -> colors.red
    }
    val pressTint = if (filled) Color.Black.copy(alpha = 0.10f) else null
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(ShellShapes.Pill)
            .background(background)
            .shellPressBackground(ShellShapes.Pill, interactionSource = interaction, tint = pressTint)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = IosType.headline, color = content)
    }
}

@Composable
fun IosTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = IosTheme.colors.blue,
) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(ShellShapes.Pill)
            .shellPressBackground(ShellShapes.Pill, enabled = enabled, interactionSource = interaction)
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = IosType.headline,
            color = if (enabled) color else colors.tertiaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal val IosNavBarHeight = 48.dp
internal val IosTabTitleHeight = 48.dp

/** First control under a root or pushed title bar. Shared so tabs line up. */
internal val IosContentTop = 8.dp

@Composable
fun IosNavBar(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    showBack: Boolean = true,
) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (showBack) IosNavBarHeight else IosTabTitleHeight)
            .background(colors.background),
    ) {
        if (showBack) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .semantics { role = Role.Button }
                    .clickable(interactionSource = interaction, indication = null, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .shellPressBackground(CircleShape, interactionSource = interaction),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = colors.label,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Text(
            text = title,
            style = IosType.title2,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(
                    start = if (showBack) 48.dp else 16.dp,
                    end = if (trailing == null) 16.dp else 140.dp,
                ),
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
    tint: Color = IosTheme.colors.label,
    plate: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val alpha = rememberShellPressAlpha(interaction)
    Box(
        Modifier
            .size(44.dp)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .then(if (plate) Modifier.clip(CircleShape).shellPressBackground(CircleShape, interactionSource = interaction) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { if (!plate) this.alpha = alpha },
            )
        }
    }
}

/**
 * Refresh glyph. It starts turning on the click, keeps turning while [busy],
 * and still completes one revolution when the work finishes immediately.
 */
@Composable
fun IosRefreshIcon(
    icon: Painter,
    description: String,
    onClick: () -> Unit,
    busy: Boolean = false,
    tint: Color = IosTheme.colors.label,
) {
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(pulse) {
        if (!pulse) return@LaunchedEffect
        delay(800)
        pulse = false
    }
    val spinning = busy || pulse
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (!spinning) {
            rotation.snapTo(0f)
            return@LaunchedEffect
        }
        rotation.snapTo(0f)
        while (coroutineContext[Job]?.isActive == true) {
            rotation.animateTo(360f, tween(durationMillis = 800, easing = LinearEasing))
            rotation.snapTo(0f)
        }
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(44.dp)
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    if (spinning) return@clickable
                    pulse = true
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .shellPressBackground(CircleShape, interactionSource = interaction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = rotation.value },
            )
        }
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
    IosSegmentedControl(
        items = labels,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
fun IosChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(32.dp)
                .clip(ShellShapes.Pill)
                .background(if (selected) colors.blue else colors.track)
                .shellPressBackground(ShellShapes.Pill, interactionSource = interaction)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = IosType.subhead,
                color = if (selected) colors.onBlue else colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TrafficSparkline(
    samples: List<TrafficSample>,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    Canvas(modifier.fillMaxWidth().height(44.dp)) {
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
 * [menu] is drawn inside the action icon so a popup anchors to that icon.
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
    leading: (@Composable () -> Unit)? = null,
    emphasized: Boolean = false,
    menu: (@Composable () -> Unit)? = null,
    actionSpins: Boolean = false,
    actionBusy: Boolean = false,
) {
    val colors = IosTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.grouped)
            .then(
                if (emphasized) {
                    Modifier.drawBehind { drawRect(colors.label.copy(alpha = 0.12f)) }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) leading()
            Box(Modifier.weight(1f)) {
                IosListRow(row, showDivider = false)
            }
            if (beforeAction != null) beforeAction()
            Box {
                if (actionSpins) {
                    IosRefreshIcon(
                        icon = actionIcon,
                        description = actionDescription,
                        busy = actionBusy,
                        onClick = onAction,
                    )
                } else {
                    IosNavIcon(
                        icon = actionIcon,
                        description = actionDescription,
                        onClick = onAction,
                    )
                }
                menu?.invoke()
            }
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
