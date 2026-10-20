package com.github.kr328.clash.design.ios

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.Insets
import kotlinx.coroutines.suspendCancellableCoroutine
import nota.ui.ios.shellIosGroupPosition
import nota.ui.ios.shellIosPreferenceColors
import nota.ui.ios.shellIosPreferenceSpec
import nota.ui.ios.shellIosSearchDimens
import nota.ui.ios.shellIosSwitchColors
import nota.ui.ios.shellIosSwitchSpec
import nota.ui.settings.ShellPreferenceItem
import nota.ui.switch.ShellSwitch
import universe.agentic.ui.search.SearchField
import universe.agentic.ui.search.SearchFieldVariant
import kotlin.coroutines.resume

@Composable
fun IosScreenScaffold(
    insets: Insets,
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = IosTheme.colors
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(
                start = with(density) { insets.start.toDp() },
                top = with(density) { insets.top.toDp() },
                end = with(density) { insets.end.toDp() },
                bottom = with(density) { insets.bottom.toDp() },
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(IosNavBarHeight))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
            ) {
                content()
            }
        }
        IosNavBar(title = title, onBack = onBack, trailing = trailing)
    }
}

@Composable
fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    SearchField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        variant = SearchFieldVariant.Overlay,
        dimens = shellIosSearchDimens(),
        leadingIcon = { IosSearchGlyph() },
        textStyle = IosType.body,
        containerColor = colors.track,
        contentColor = colors.label,
        placeholderColor = colors.tertiaryLabel,
        cursorColor = colors.blue,
        clearTint = colors.tertiaryLabel,
        clearContentDescription = stringResource(R.string.search_clear),
    )
}

@Composable
private fun IosSearchGlyph() {
    val color = IosTheme.colors.tertiaryLabel
    Canvas(Modifier.size(15.dp)) {
        val stroke = 1.5.dp.toPx()
        val radius = size.minDimension * 0.32f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(center.x + radius * 0.7f, center.y + radius * 0.7f),
            end = Offset(size.width * 0.82f, size.height * 0.82f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Inset grouped preference row. [index] and [count] pick the corner and the hairline.
 * A single call is a card by itself.
 */
@Composable
fun IosPreferenceRow(
    row: IosDetail,
    index: Int = 0,
    count: Int = 1,
) {
    val colors = shellIosPreferenceColors()
    val custom = row.hasCustomTrailing()
    val clickable = row.enabled && row.onClick != null
    val showChevron = clickable && row.disclosure && row.trailing.isNullOrEmpty()
    ShellPreferenceItem(
        title = row.title,
        summary = row.subtitle,
        onClick = if (clickable) row.onClick else null,
        enabled = row.enabled,
        showArrow = clickable && row.disclosure && !custom,
        showDivider = index < count - 1,
        groupPosition = shellIosGroupPosition(index, count),
        spec = shellIosPreferenceSpec(),
        colors = colors,
        trailing = if (custom) {
            {
                IosDetailTrailing(
                    row = row,
                    showChevron = showChevron,
                    chevronColor = if (row.enabled) colors.arrowColor else colors.disabledArrowColor,
                )
            }
        } else {
            null
        },
    )
}

/**
 * Grouped switch row. The switch is the copied 51×31 control.
 * Tapping the title toggles it. Press feedback is the row tint, not a ripple.
 */
@Composable
fun IosSwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    enabled: Boolean = true,
    index: Int = 0,
    count: Int = 1,
    onCheckedChange: (Boolean) -> Unit,
) {
    ShellPreferenceItem(
        title = title,
        summary = summary,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else {
            null
        },
        enabled = enabled,
        showArrow = false,
        showDivider = index < count - 1,
        groupPosition = shellIosGroupPosition(index, count),
        spec = shellIosPreferenceSpec(),
        colors = shellIosPreferenceColors(),
        trailing = {
            ShellSwitch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                spec = shellIosSwitchSpec(),
                colors = shellIosSwitchColors(tint = IosTheme.colors.green),
            )
        },
    )
}

internal val LocalIosSheetDismiss = staticCompositionLocalOf<(() -> Unit) -> Unit> { { } }

private const val SheetMotionMs = 250

data class IosAction(
    val title: String,
    val destructive: Boolean = false,
    val marked: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun IosOptionSheet(
    title: String?,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    IosSheet(onDismiss) {
        val dismiss = LocalIosSheetDismiss.current
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = IosType.footnote,
                color = IosTheme.colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        options.forEachIndexed { index, label ->
            IosListRow(
                IosDetail(
                    title = label,
                    selected = index == selected,
                    disclosure = false,
                    onClick = { dismiss { onSelect(index) } },
                ),
            )
        }
    }
}

@Composable
fun IosActionSheet(
    actions: List<IosAction>,
    onDismiss: () -> Unit,
    cancelLabel: String? = null,
    title: String? = null,
) {
    val colors = IosTheme.colors
    IosSheetHost(onDismiss) {
        val dismiss = LocalIosSheetDismiss.current
        IosSheetCard {
            if (!title.isNullOrEmpty()) {
                Text(
                    text = title,
                    style = IosType.footnote,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(title) { detectTapGestures(onTap = {}) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(colors.separator),
                )
            }
            actions.forEachIndexed { index, action ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(colors.separator),
                    )
                }
                IosSheetButton(
                    title = action.title,
                    color = if (action.destructive) colors.red else colors.blue,
                    marked = action.marked,
                    onClick = { dismiss(action.onClick) },
                )
            }
        }
        if (!cancelLabel.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            IosSheetCard {
                IosSheetButton(
                    title = cancelLabel,
                    color = colors.blue,
                    onClick = { dismiss {} },
                )
            }
        }
    }
}

@Composable
fun IosEmptyState(
    message: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = IosType.body, color = IosTheme.colors.secondaryLabel)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            IosButton(text = action, style = IosButtonStyle.Filled, onClick = onAction)
        }
    }
}

@Composable
fun IosProgressRow(label: String) {
    val color = IosTheme.colors.blue
    val rotation by rememberInfiniteTransition(label = "ios-progress").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "ios-progress-rotation",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            Modifier
                .size(18.dp)
                .rotate(rotation),
        ) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Text(
            text = label,
            style = IosType.footnote,
            color = IosTheme.colors.secondaryLabel,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
fun IosFractionBar(fraction: Float, modifier: Modifier = Modifier) {
    val colors = IosTheme.colors
    val clamped = fraction.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = colors.track, cornerRadius = radius)
        if (clamped > 0f) {
            drawRoundRect(
                color = colors.blue,
                size = Size(size.width * clamped, size.height),
                cornerRadius = radius,
            )
        }
    }
}

suspend fun iosConfirm(
    context: Context,
    title: String,
    message: String,
    confirm: String,
    destructive: Boolean = false,
): Boolean = suspendCancellableCoroutine { cont ->
    val dialog = AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(confirm) { _, _ ->
            if (cont.isActive) cont.resume(true)
        }
        .setNegativeButton(context.getString(R.string.cancel)) { _, _ ->
            if (cont.isActive) cont.resume(false)
        }
        .setOnCancelListener {
            if (cont.isActive) cont.resume(false)
        }
        .create()
    cont.invokeOnCancellation { dialog.dismiss() }
    dialog.setOnShowListener {
        if (!destructive) return@setOnShowListener
        val night = context.iosForceAwareDark()
        val color = if (night) 0xFFFF453A.toInt() else 0xFFFF3B30.toInt()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(color)
    }
    dialog.show()
}

private fun Context.iosForceAwareDark(): Boolean {
    val nightFlag = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return when (UiStore(this).darkMode) {
        DarkMode.ForceDark -> true
        DarkMode.ForceLight -> false
        DarkMode.Auto -> nightFlag == Configuration.UI_MODE_NIGHT_YES
    }
}

@Composable
fun IosSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    IosSheetHost(onDismiss) {
        IosSheetCard(content)
    }
}

@Composable
private fun IosSheetHost(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val composeBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val (barTop, barBottom) = activitySystemBars()
    val bottom = 8.dp + maxOf(composeBottom, barBottom)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var lift by remember { mutableStateOf(0.dp) }
    val progress = remember { Animatable(0f) }
    var travel by remember { mutableIntStateOf(100_000) }
    val closingState = remember { mutableStateOf(false) }
    val followUp = remember { mutableStateOf<(() -> Unit)?>(null) }
    val onDismissState = rememberUpdatedState(onDismiss)
    val requestClose = remember {
        { after: () -> Unit ->
            followUp.value = after
            if (!closingState.value) {
                closingState.value = true
            }
        }
    }
    val closing = closingState.value
    val fraction = progress.value
    val slidePx = ((1f - fraction) * travel).roundToInt()
    LaunchedEffect(closing) {
        progress.animateTo(
            targetValue = if (closing) 0f else 1f,
            animationSpec = tween(durationMillis = SheetMotionMs, easing = FastOutSlowInEasing),
        )
        if (closing) {
            followUp.value?.invoke()
            onDismissState.value()
        }
    }
    Dialog(
        onDismissRequest = { requestClose {} },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view as? DialogWindowProvider)?.window
                ?: (view.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0f)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setWindowAnimations(0)
        }
        CompositionLocalProvider(LocalIosSheetDismiss provides requestClose) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val maxSheet = (maxHeight - bottom - lift - barTop - 8.dp).coerceAtLeast(120.dp)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = fraction)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { requestClose {} },
                        ),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { coords ->
                            if (coords.size.height > 0) travel = coords.size.height
                            val loc = IntArray(2)
                            view.getLocationOnScreen(loc)
                            val screenH = view.resources.displayMetrics.heightPixels
                            val pos = coords.positionInRoot()
                            val bottomOnScreen = loc[1] + pos.y + coords.size.height
                            val overflow = bottomOnScreen - screenH
                            if (overflow > 1f) {
                                val extra = with(density) { overflow.toDp() }
                                if (extra > lift) lift = extra
                            }
                        }
                        .offset { IntOffset(0, slidePx) }
                        .padding(start = 8.dp, end = 8.dp, bottom = bottom + lift)
                        .fillMaxWidth()
                        .heightIn(max = maxSheet)
                        .verticalScroll(scroll),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun activitySystemBars(): Pair<Dp, Dp> {
    val context = LocalContext.current
    val density = LocalDensity.current
    val decor = context.findActivity()?.window?.decorView
    val root = decor?.let { ViewCompat.getRootWindowInsets(it) }
    val (topPx, bottomPx) = if (root == null) {
        0 to 0
    } else {
        val bars = root.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        bars.top to bars.bottom
    }
    return with(density) { topPx.toDp() to bottomPx.toDp() }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (true) {
        when (current) {
            is Activity -> return current
            is ContextWrapper -> current = current.baseContext
            else -> return null
        }
    }
}

@Composable
private fun IosSheetCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IosTheme.colors.grouped),
        content = content,
    )
}

@Composable
private fun IosSheetButton(
    title: String,
    color: Color,
    marked: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = IosTheme.colors
    val interaction = remember(title, marked) { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(if (pressed) colors.pressed else Color.Transparent)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = title, style = IosType.body, color = color)
        if (marked) {
            Box(Modifier.align(Alignment.CenterEnd)) {
                IosSelectionMark(IosSelection.Checked)
            }
        }
    }
}
