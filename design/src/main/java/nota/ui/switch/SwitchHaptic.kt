package nota.ui.switch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

private var switchHapticHandler: (() -> Unit)? = null

internal fun performSwitchHaptic() {
    switchHapticHandler?.invoke()
}

@Composable
internal fun BindSwitchHaptic() {
    val hapticFeedback = LocalHapticFeedback.current
    DisposableEffect(hapticFeedback) {
        switchHapticHandler = {
            // Compose 1.7 only has LongPress and TextHandleMove.
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        onDispose {
            switchHapticHandler = null
        }
    }
}
