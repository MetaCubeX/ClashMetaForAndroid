package universe.agentic.clash
import androidx.compose.material.Icon

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import nota.ui.theme.shellPressBackground
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import nota.ui.theme.ShellShapes
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
import nota.ui.ios.LocalShellIosDark
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

@Composable
fun IosScreenScaffold(
    insets: Insets,
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    tab: ShellTab? = null,
    onTab: (ShellTab) -> Unit = {},
    embedded: Boolean = false,
    showBack: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val colors = IosTheme.colors
    val density = LocalDensity.current
    val bottomInset = with(density) { insets.bottom.toDp() }
    val barHeight = if (embedded || tab != null) IosTabTitleHeight else IosNavBarHeight
    val back = showBack ?: (!embedded && tab == null)
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(
                start = with(density) { insets.start.toDp() },
                top = with(density) { insets.top.toDp() },
                end = with(density) { insets.end.toDp() },
                bottom = if (embedded || tab != null) 0.dp else bottomInset,
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(barHeight))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
            ) {
                content()
            }
            if (!embedded && tab != null) {
                ShellTabBar(selected = tab, onSelect = onTab, bottomInset = bottomInset)
            }
        }
        IosNavBar(
            title = title,
            onBack = onBack,
            trailing = trailing,
            showBack = back,
        )
    }
}

@Composable
fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    horizontalInset: Dp = 16.dp,
    containerColor: Color = IosTheme.colors.grouped,
) {
    val colors = IosTheme.colors
    SearchField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier.padding(horizontal = horizontalInset, vertical = IosContentTop),
        variant = SearchFieldVariant.Overlay,
        dimens = shellIosSearchDimens(),
        leadingIcon = { IosSearchGlyph() },
        textStyle = IosType.body,
        containerColor = containerColor,
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
    Canvas(Modifier.size(18.dp)) {
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
    val leading = if (row.leadingIcon != null) {
        @Composable {
            IosLeadingTile(icon = row.leadingIcon)
        }
    } else {
        null
    }
    ShellPreferenceItem(
        title = row.title,
        modifier = row.modifier,
        summary = row.subtitle,
        spokenDescription = row.spokenDescription,
        onClick = if (clickable) row.onClick else null,
        enabled = row.enabled,
        showArrow = clickable && row.disclosure && !custom,
        showDivider = index < count - 1,
        groupPosition = shellIosGroupPosition(index, count),
        spec = shellIosPreferenceSpec(),
        colors = colors,
        leading = leading,
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
 * Grouped switch row. The track is 40×22 and uses the accent color when on.
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
                colors = shellIosSwitchColors(tint = IosTheme.colors.blue),
                toggleOnClick = false,
            )
        },
    )
}

internal val LocalIosSheetDismiss = staticCompositionLocalOf<(() -> Unit) -> Unit> { { } }

private const val SheetEnterMs = 300
private const val SheetExitMs = 200

data class IosAction(
    val title: String,
    val destructive: Boolean = false,
    val marked: Boolean = false,
    val onClick: () -> Unit,
)

data class IosMenuGroup(
    val title: String? = null,
    val actions: List<IosAction>,
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
                    exclusive = true,
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
                    color = if (action.destructive) colors.red else colors.label,
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
                    color = colors.label,
                    onClick = { dismiss {} },
                )
            }
        }
    }
}

/**
 * Short action list anchored to the calling control. No scrim and no cancel row.
 * Place it inside the anchor so the menu opens beside that control.
 */
@Composable
fun IosPopupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<IosAction> = emptyList(),
    groups: List<IosMenuGroup> = emptyList(),
    scrollable: Boolean = false,
) {
    val shown = when {
        groups.isNotEmpty() -> groups.filter { it.actions.isNotEmpty() }
        actions.isNotEmpty() -> listOf(IosMenuGroup(actions = actions))
        else -> emptyList()
    }
    if (!expanded || shown.isEmpty()) return
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }
    val gap = with(density) { 4.dp.roundToPx() }
    val shadow = with(density) { 12.dp.roundToPx() }
    val provider = remember(margin, gap, shadow) {
        IosPopupPositionProvider(margin, gap, shadow)
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        IosPopupMenuCard(groups = shown, onDismiss = onDismiss, scrollable = scrollable)
    }
}

@Composable
private fun IosPopupMenuCard(
    groups: List<IosMenuGroup>,
    onDismiss: () -> Unit,
    scrollable: Boolean,
) {
    val colors = IosTheme.colors
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp - 48.dp
    val shapeModifier = if (scrollable) {
        Modifier
            .width(280.dp)
            .shadow(elevation = 4.dp, shape = ShellShapes.Popup, clip = false)
            .background(colors.grouped, ShellShapes.Popup)
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
    } else {
        Modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = 160.dp, max = 280.dp)
            .shadow(elevation = 4.dp, shape = ShellShapes.Popup, clip = false)
            .background(colors.grouped, ShellShapes.Popup)
            .padding(vertical = 4.dp)
    }
    Column(Modifier.padding(12.dp)) {
        Column(shapeModifier) {
            groups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) {
                    Spacer(Modifier.height(8.dp))
                }
                if (!group.title.isNullOrEmpty()) {
                    Text(
                        text = group.title,
                        style = IosType.caption1,
                        color = colors.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                    )
                }
                group.actions.forEachIndexed { index, action ->
                    val previous = group.actions.getOrNull(index - 1)
                    if (action.destructive && previous != null && !previous.destructive) {
                        Spacer(Modifier.height(8.dp))
                    }
                    IosPopupMenuRow(action) {
                        onDismiss()
                        action.onClick()
                    }
                }
            }
        }
    }
}

@Composable
private fun IosPopupMenuRow(
    action: IosAction,
    onClick: () -> Unit,
) {
    val colors = IosTheme.colors
    val interaction = remember(action.title, action.destructive, action.marked) {
        MutableInteractionSource()
    }
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .height(40.dp)
            .fillMaxWidth()
            .clip(ShellShapes.Control)
            .shellPressBackground(ShellShapes.Control, interactionSource = interaction)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = action.title,
            style = IosType.headline,
            color = if (action.destructive) colors.red else colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = if (action.marked) 30.dp else 0.dp),
        )
        if (action.marked) {
            Box(Modifier.align(Alignment.CenterEnd)) {
                IosMenuCheck()
            }
        }
    }
}

private class IosPopupPositionProvider(
    private val margin: Int,
    private val gap: Int,
    private val shadow: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val visualW = (popupContentSize.width - shadow * 2).coerceAtLeast(0)
        val visualH = (popupContentSize.height - shadow * 2).coerceAtLeast(0)
        val spaceBelow = windowSize.height - margin - (anchorBounds.bottom + gap)
        val spaceAbove = anchorBounds.top - gap - margin
        val visualY = if (visualH <= spaceBelow || spaceBelow >= spaceAbove) {
            anchorBounds.bottom + gap
        } else {
            anchorBounds.top - gap - visualH
        }
        val preferredX = if (layoutDirection == LayoutDirection.Rtl) {
            anchorBounds.left
        } else {
            anchorBounds.right - visualW
        }
        val maxX = (windowSize.width - margin - visualW).coerceAtLeast(margin)
        val maxY = (windowSize.height - margin - visualH).coerceAtLeast(margin)
        return IntOffset(
            preferredX.coerceIn(margin, maxX) - shadow,
            visualY.coerceIn(margin, maxY) - shadow,
        )
    }
}

@Composable
fun IosEmptyState(
    message: String,
    title: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(48.dp)) {
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            val inset = 8.dp.toPx()
            drawRoundRect(
                color = colors.secondaryLabel.copy(alpha = 0.45f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = stroke,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (title != null) {
            Text(text = title, style = IosType.headline, color = colors.label)
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = IosType.footnote,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = message,
                style = IosType.headline,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
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

@Composable
fun IosConfirmOverlay(
    title: String,
    message: String,
    confirm: String,
    destructive: Boolean = false,
    onFinished: (Boolean) -> Unit,
) {
    IosConfirmDialog(
        title = title,
        message = message,
        confirm = confirm,
        destructive = destructive,
        registerBack = {},
        onFinished = onFinished,
    )
}

suspend fun iosConfirm(
    context: Context,
    title: String,
    message: String,
    confirm: String,
    destructive: Boolean = false,
): Boolean {
    val activity = context.findActivity() as? ComponentActivity ?: return legacyConfirm(
        context, title, message, confirm, destructive,
    )
    return suspendCancellableCoroutine { cont ->
        val gate = AtomicBoolean(false)
        val back = AtomicReference<(() -> Unit)?>(null)
        var dialog: android.app.Dialog? = null
        fun finish(value: Boolean) {
            if (gate.compareAndSet(false, true) && cont.isActive) cont.resume(value)
            dialog?.dismiss()
        }
        dialog = mountIosDialog(activity, onBack = { back.get()?.invoke() }) {
            IosConfirmDialog(
                title = title,
                message = message,
                confirm = confirm,
                destructive = destructive,
                registerBack = { back.set(it) },
                onFinished = { finish(it) },
            )
        }
        val shown = dialog
        cont.invokeOnCancellation { shown?.dismiss() }
        shown?.show()
    }
}

internal fun mountIosDialog(
    activity: ComponentActivity,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
): android.app.Dialog {
    val dialog = android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
    val night = activity.iosForceAwareDark()
    dialog.window?.applyIosDialogBars(night)
    dialog.setOnShowListener { dialog.window?.applyIosDialogBars(night) }
    val composeView = ComposeView(activity).apply {
        setViewTreeLifecycleOwner(activity)
        setViewTreeViewModelStoreOwner(activity)
        setViewTreeSavedStateRegistryOwner(activity)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent { AgenticClashTheme(content) }
    }
    dialog.setContentView(composeView)
    dialog.setCancelable(false)
    dialog.setOnKeyListener { _, keyCode, event ->
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBack()
            true
        } else {
            false
        }
    }
    return dialog
}

@Composable
internal fun IosDialogWindow() {
    val view = LocalView.current
    val night = LocalShellIosDark.current
    SideEffect {
        val window = (view as? DialogWindowProvider)?.window
            ?: (view.parent as? DialogWindowProvider)?.window
        window?.applyIosDialogBars(night)
    }
}

private fun Window.applyIosDialogBars(night: Boolean) {
    setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    setDimAmount(0f)
    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    setWindowAnimations(0)
    statusBarColor = android.graphics.Color.TRANSPARENT
    navigationBarColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= 28) {
        attributes = attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    if (Build.VERSION.SDK_INT >= 29) {
        isStatusBarContrastEnforced = false
        isNavigationBarContrastEnforced = false
    }
    WindowCompat.setDecorFitsSystemWindows(this, false)
    WindowInsetsControllerCompat(this, decorView).apply {
        isAppearanceLightStatusBars = !night
        isAppearanceLightNavigationBars = !night
    }
}

@Composable
private fun IosConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    destructive: Boolean,
    registerBack: ((() -> Unit)?) -> Unit,
    onFinished: (Boolean) -> Unit,
) {
    val colors = IosTheme.colors
    val visible = remember { Animatable(0f) }
    var pending by remember { mutableStateOf<Boolean?>(null) }
    val close by rememberUpdatedState { value: Boolean ->
        if (pending == null) pending = value
    }
    LaunchedEffect(Unit) {
        visible.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(pending) {
        val value = pending ?: return@LaunchedEffect
        visible.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
        onFinished(value)
    }
    DisposableEffect(Unit) {
        registerBack { close(false) }
        onDispose { registerBack(null) }
    }
    IosDialogChrome(progress = visible.value, onScrim = { close(false) }) {
        Text(text = title, style = IosType.title3, color = colors.label)
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(text = message, style = IosType.body, color = colors.label)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DialogPillButton(
                text = stringResource(R.string.cancel),
                container = colors.track,
                content = colors.label,
                onClick = { close(false) },
                modifier = Modifier.weight(1f),
            )
            if (destructive) {
                DialogPillButton(
                    text = confirm,
                    container = Color.Transparent,
                    content = colors.red,
                    onClick = { close(true) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                DialogPillButton(
                    text = confirm,
                    container = colors.blue,
                    content = colors.onBlue,
                    pressTint = Color.Black.copy(alpha = 0.10f),
                    onClick = { close(true) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun IosDialogChrome(
    progress: Float,
    onScrim: () -> Unit,
    aboveKeyboard: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = IosTheme.colors
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val bars = WindowInsets.navigationBars.asPaddingValues()
    val imeBottom = if (aboveKeyboard) {
        WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }
    val bottom = 16.dp + maxOf(imeBottom, bars.calculateBottomPadding())
    val layoutDirection = LocalLayoutDirection.current
    val scrim = remember { MutableInteractionSource() }
    val stickToBottom = portrait || imeBottom > 0.dp
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(colors.scrim)
                .clickable(interactionSource = scrim, indication = null, onClick = onScrim),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp + bars.calculateStartPadding(layoutDirection),
                    end = 16.dp + bars.calculateEndPadding(layoutDirection),
                    top = 16.dp,
                    bottom = bottom,
                ),
            contentAlignment = if (stickToBottom) Alignment.BottomCenter else Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * 16.dp.toPx()
                    }
                    .clip(ShellShapes.Container)
                    .background(colors.grouped)
                    .padding(12.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun DialogPillButton(
    text: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pressTint: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(ShellShapes.Pill)
            .background(container)
            .shellPressBackground(ShellShapes.Pill, interactionSource = interaction, tint = pressTint)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = IosType.headline,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}

private suspend fun legacyConfirm(
    context: Context,
    title: String,
    message: String,
    confirm: String,
    destructive: Boolean,
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
        val color = if (night) 0xFFFF6B63.toInt() else 0xFFE5484D.toInt()
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

/**
 * A bottom panel that slides up over a scrim.
 * The panel itself is [panel]; it should paint to the screen's bottom edge
 * and pad its last control by the navigation-bar inset.
 */
@Composable
internal fun IosSheetFrame(
    onDismiss: () -> Unit,
    attemptDismiss: () -> Boolean = { true },
    panel: @Composable BoxScope.(close: () -> Unit) -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var travel by remember { mutableIntStateOf(100_000) }
    val onDismissState = rememberUpdatedState(onDismiss)
    val attemptState = rememberUpdatedState(attemptDismiss)
    val fraction = progress.value
    val slidePx = ((1f - fraction) * travel).roundToInt()
    val close = { if (!closing && attemptState.value()) closing = true }
    LaunchedEffect(closing) {
        progress.animateTo(
            targetValue = if (closing) 0f else 1f,
            animationSpec = tween(
                durationMillis = if (closing) SheetExitMs else SheetEnterMs,
                easing = FastOutSlowInEasing,
            ),
        )
        if (closing) onDismissState.value()
    }
    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        IosDialogWindow()
        val view = LocalView.current
        SideEffect {
            val window = (view as? DialogWindowProvider)?.window
                ?: (view.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0f)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setWindowAnimations(0)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fraction }
                    .background(IosTheme.colors.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { close() },
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        if (coords.size.height > 0) travel = coords.size.height
                    }
                    .offset { IntOffset(0, slidePx) },
            ) {
                panel(close)
            }
            overlay()
        }
    }
}

@Composable
fun IosSheet(
    onDismiss: () -> Unit,
    fill: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    IosSheetHost(onDismiss, fill) {
        IosSheetCard(fill, content)
    }
}

@Composable
private fun IosSheetHost(
    onDismiss: () -> Unit,
    fill: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val composeBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val (barTop, barBottom) = activitySystemBars()
    val bottom = maxOf(composeBottom, barBottom)
    val scroll = rememberScrollState()
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
            animationSpec = tween(
                durationMillis = if (closing) SheetExitMs else SheetEnterMs,
                easing = FastOutSlowInEasing,
            ),
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
        IosDialogWindow()
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
                val maxSheet = (maxHeight - bottom - barTop - 8.dp).coerceAtLeast(120.dp)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = fraction)
                        .background(IosTheme.colors.scrim)
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
                        }
                        .offset { IntOffset(0, slidePx) }
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .clip(ShellShapes.SheetTop)
                        .background(IosTheme.colors.grouped)
                        .padding(bottom = bottom)
                        .then(
                            if (fill) {
                                Modifier.height(maxSheet)
                            } else {
                                Modifier.heightIn(max = maxSheet).verticalScroll(scroll)
                            },
                        ),
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun iosSheetBottom(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var bottomPx by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        fun publish(insets: androidx.core.view.WindowInsetsCompat?) {
            if (insets == null) return
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.navigationBars() or
                    androidx.core.view.WindowInsetsCompat.Type.systemGestures(),
            )
            if (bars.bottom > bottomPx) bottomPx = bars.bottom
        }
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            publish(ViewCompat.getRootWindowInsets(view))
            val decor = view.context.findActivity()?.window?.decorView
            if (decor != null) publish(ViewCompat.getRootWindowInsets(decor))
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    val resourcePx = remember(view) {
        val id = view.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (id > 0) view.resources.getDimensionPixelSize(id) else 0
    }
    val compose = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val (_, activityBottom) = activitySystemBars()
    val measured = with(density) { maxOf(bottomPx, resourcePx).toDp() }
    return maxOf(compose, activityBottom, measured)
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
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.systemGestures() or
                WindowInsetsCompat.Type.displayCutout(),
        )
        bars.top to bars.bottom
    }
    return with(density) { topPx.toDp() to bottomPx.toDp() }
}

internal fun Context.findActivity(): Activity? {
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
    fill: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (fill) Modifier.fillMaxHeight() else Modifier)
            .clip(ShellShapes.SheetTop)
            .background(IosTheme.colors.grouped)
            .padding(bottom = 16.dp),
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
    val interaction = remember(title, marked) { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .shellPressBackground(interactionSource = interaction)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = title, style = IosType.headline, color = color)
        if (marked) {
            Box(Modifier.align(Alignment.CenterEnd)) {
                IosMenuCheck()
            }
        }
    }
}
