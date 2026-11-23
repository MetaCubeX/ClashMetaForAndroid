package universe.agentic.clash

import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import nota.ui.theme.ShellShapes
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import nota.ui.theme.rememberShellPressAlpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets

enum class ShellTab(val label: Int) {
    Proxies(R.string.tab_proxies),
    Profiles(R.string.tab_profiles),
    Dashboard(R.string.tab_dashboard),
    Apps(R.string.tab_apps),
    Settings(R.string.tab_settings),
}

private val ShellPageEnterEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val ShellPageEnterInterpolator = PathInterpolator(0f, 0f, 0.58f, 1f)
private const val ShellPageEnterMs = 200

private class ShellPageMotion {
    var first = true
    var last: ShellTab? = null
}

@Composable
fun ShellTabBar(
    selected: ShellTab,
    onSelect: (ShellTab) -> Unit,
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.grouped)
            .padding(bottom = bottomInset),
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.separator),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShellTab.entries.forEach { tab ->
                val active = tab == selected
                val label = stringResource(tab.label)
                val interaction = remember(tab) { MutableInteractionSource() }
                val press = rememberShellPressAlpha(interaction)
                val tint = if (active) colors.label else colors.secondaryLabel
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = press }
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(tab) },
                        )
                        .semantics {
                            role = Role.Tab
                            this.selected = active
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(48.dp)
                            .height(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(ShellShapes.Pill)
                                    .background(colors.label.copy(alpha = 0.12f)),
                            )
                        }
                        TabGlyph(tab = tab, selected = active, color = tint)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = label,
                        color = tint,
                        style = IosType.caption1.copy(
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabGlyph(tab: ShellTab, selected: Boolean, color: Color) {
    val strokeWidth = if (selected) 2.25.dp else 1.5.dp
    Canvas(Modifier.size(if (selected) 24.dp else 22.dp)) {
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val s = size.minDimension
        fun x(fraction: Float) = s * fraction
        fun y(fraction: Float) = s * fraction
        when (tab) {
            ShellTab.Proxies -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x(0.22f), y(0.46f)),
                    size = Size(x(0.56f), y(0.38f)),
                    cornerRadius = CornerRadius(x(0.08f)),
                    style = stroke,
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = -180f,
                    useCenter = false,
                    topLeft = Offset(x(0.32f), y(0.16f)),
                    size = Size(x(0.36f), y(0.40f)),
                    style = stroke,
                )
            }
            ShellTab.Profiles -> {
                val path = Path().apply {
                    moveTo(x(0.16f), y(0.40f))
                    lineTo(x(0.40f), y(0.40f))
                    lineTo(x(0.48f), y(0.28f))
                    lineTo(x(0.84f), y(0.28f))
                    lineTo(x(0.84f), y(0.78f))
                    lineTo(x(0.16f), y(0.78f))
                    close()
                }
                drawPath(path, color, style = stroke)
            }
            ShellTab.Dashboard -> {
                val path = Path().apply {
                    moveTo(x(0.56f), y(0.14f))
                    lineTo(x(0.28f), y(0.54f))
                    lineTo(x(0.48f), y(0.54f))
                    lineTo(x(0.40f), y(0.86f))
                    lineTo(x(0.74f), y(0.42f))
                    lineTo(x(0.52f), y(0.42f))
                    close()
                }
                drawPath(path, color, style = stroke)
            }
            ShellTab.Apps -> {
                val cell = x(0.28f)
                val gap = x(0.12f)
                val origin = x(0.16f)
                val radius = CornerRadius(x(0.06f))
                for (row in 0..1) {
                    for (col in 0..1) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(origin + col * (cell + gap), origin + row * (cell + gap)),
                            size = Size(cell, cell),
                            cornerRadius = radius,
                            style = stroke,
                        )
                    }
                }
            }
            ShellTab.Settings -> {
                val lines = listOf(0.28f to 0.64f, 0.50f to 0.38f, 0.72f to 0.70f)
                lines.forEach { (yy, knob) ->
                    drawLine(
                        color = color,
                        start = Offset(x(0.18f), y(yy)),
                        end = Offset(x(0.82f), y(yy)),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = color,
                        radius = x(0.07f),
                        center = Offset(x(knob), y(yy)),
                        style = stroke,
                    )
                }
            }
        }
    }
}

@Composable
fun ShellPages(
    insets: Insets,
    tab: ShellTab,
    onTab: (ShellTab) -> Unit,
    proxy: View?,
    profiles: View?,
    apps: View?,
    settings: View?,
    dashboard: @Composable () -> Unit,
) {
    val bottom = with(LocalDensity.current) { insets.bottom.toDp() }
    val dashboardVisible = tab == ShellTab.Dashboard
    val motion = remember { ShellPageMotion() }
    val firstFrame = motion.first
    if (firstFrame) {
        motion.first = false
        motion.last = tab
    }
    val enter = remember(tab) { Animatable(if (firstFrame) 1f else 0f) }
    LaunchedEffect(tab) {
        if (firstFrame) return@LaunchedEffect
        enter.animateTo(1f, tween(ShellPageEnterMs, easing = ShellPageEnterEasing))
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(IosTheme.colors.background),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Box(
                if (dashboardVisible) Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = enter.value
                    }
                // A 0dp box does not clip. requiredSize hit targets would paint at the
                // window origin and show through while the incoming page fades in.
                else Modifier
                    .size(0.dp)
                    .drawWithContent { }
                    .clearAndSetSemantics { },
            ) {
                dashboard()
            }
            AndroidView(
                modifier = if (dashboardVisible) Modifier.size(0.dp).clearAndSetSemantics { }
                else Modifier.fillMaxSize(),
                factory = { FrameLayout(it) },
                update = { frame ->
                    if (dashboardVisible) {
                        motion.last = ShellTab.Dashboard
                    } else if (motion.last != tab) {
                        motion.last = tab
                        frame.animate().cancel()
                        frame.alpha = 0f
                        frame.animate()
                            .alpha(1f)
                            .setDuration(ShellPageEnterMs.toLong())
                            .setInterpolator(ShellPageEnterInterpolator)
                            .start()
                    }
                    val shown = when (tab) {
                        ShellTab.Proxies -> proxy
                        ShellTab.Profiles -> profiles
                        ShellTab.Apps -> apps
                        ShellTab.Settings -> settings
                        ShellTab.Dashboard -> null
                    }
                    val keep = listOfNotNull(proxy, profiles, apps, settings)
                    for (index in frame.childCount - 1 downTo 0) {
                        if (frame.getChildAt(index) !in keep) frame.removeViewAt(index)
                    }
                    keep.forEach { child ->
                        if (child.parent !== frame) {
                            (child.parent as? ViewGroup)?.removeView(child)
                            frame.addView(
                                child,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )
                        }
                        child.visibility = if (child === shown) View.VISIBLE else View.INVISIBLE
                    }
                },
            )
        }
        ShellTabBar(selected = tab, onSelect = onTab, bottomInset = bottom)
    }
}
