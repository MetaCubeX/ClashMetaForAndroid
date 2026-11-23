package universe.agentic.clash

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.UiStore
import nota.ui.ios.LocalShellIosDark
import nota.ui.theme.LocalShellPressTint
import nota.ui.theme.ShellCorners

@Immutable
data class IosColors(
    val background: Color,
    val grouped: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val track: Color,
    val segment: Color,
    val pressed: Color,
    val blue: Color,
    val green: Color,
    val orange: Color,
    val teal: Color,
    val red: Color,
    val onBlue: Color,
    val scrim: Color,
    val choiceRing: Color,
)

private val LightColors = IosColors(
    background = Color(0xFFF3F4F6),
    grouped = Color.White,
    label = Color(0xFF1A1A1A),
    secondaryLabel = Color(0xFF8A8A8A),
    tertiaryLabel = Color(0xFFB0B0B0),
    separator = Color(0x14000000),
    track = Color(0xFFEEEEEE),
    segment = Color.White,
    pressed = Color(0x1A767680),
    blue = Color(0xFF587894),
    green = Color(0xFF12A150),
    orange = Color(0xFFFF7A1A),
    teal = Color(0xFF0E9F8A),
    red = Color(0xFFE5484D),
    onBlue = Color.White,
    scrim = Color(0x29000000),
    choiceRing = Color(0xFFC4C4C4),
)

private val DarkColors = IosColors(
    background = Color(0xFF121212),
    grouped = Color(0xFF1E1E1E),
    label = Color(0xFFF5F5F5),
    secondaryLabel = Color(0xFF9A9A9A),
    tertiaryLabel = Color(0xFF6E6E6E),
    separator = Color(0x14FFFFFF),
    track = Color(0xFF2A2A2A),
    segment = Color(0xFF3A3A3A),
    pressed = Color(0x24767680),
    blue = Color(0xFF6A87A1),
    green = Color(0xFF3DDB86),
    orange = Color(0xFFFF9A4A),
    teal = Color(0xFF3DCFB6),
    red = Color(0xFFFF6B63),
    onBlue = Color(0xFF1A1A1A),
    scrim = Color(0x7A000000),
    choiceRing = Color(0xFF5A5A5A),
)

object IosMetric {
    val card = ShellCorners.Lg
    val feature = ShellCorners.Lg
    val sheet = ShellCorners.Lg
    val control = ShellCorners.Sm
}

val LocalIosColors = staticCompositionLocalOf { LightColors }

object IosTheme {
    val colors: IosColors
        @Composable get() = LocalIosColors.current
}

object IosType {
    val largeTitle = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    )
    val title2 = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val title3 = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val headline = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    )
    val body = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    )
    val subhead = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    )
    val section = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    )
    val footnote = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    )
    val segment = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    )
    val caption1 = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    )
    val caption2 = TextStyle(
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun AgenticClashTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dark = remember(context) {
        val nightFlag = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        when (UiStore(context).darkMode) {
            DarkMode.ForceDark -> true
            DarkMode.ForceLight -> false
            DarkMode.Auto -> nightFlag == Configuration.UI_MODE_NIGHT_YES
        }
    }
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(
        LocalIosColors provides colors,
        LocalShellIosDark provides dark,
        LocalShellPressTint provides colors.pressed,
    ) {
        content()
    }
}
