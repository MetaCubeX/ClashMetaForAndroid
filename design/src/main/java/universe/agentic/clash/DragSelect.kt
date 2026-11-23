package universe.agentic.clash

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Drag-select on the trailing checkbox column.
 * A finger that starts in the last 64dp selects or clears a contiguous range.
 * The first row decides which. Rows outside that range stay as they were.
 * A finger that starts further left scrolls the list as usual.
 */
@Composable
fun Modifier.checkboxDragSelect(
    listState: LazyListState,
    order: List<String>,
    selected: Set<String>,
    onSelected: (Set<String>) -> Unit,
): Modifier {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val orderState = rememberUpdatedState(order)
    val selectedState = rememberUpdatedState(selected)
    val onSelectedState = rememberUpdatedState(onSelected)
    val columnPx = with(density) { 64.dp.toPx() }
    val edgePx = with(density) { 56.dp.toPx() }
    return pointerInput(columnPx, edgePx) {
        fun indexAt(y: Float): Int? {
            val keys = orderState.value
            val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                y >= item.offset && y < item.offset + item.size
            } ?: return null
            val key = hit.key as? String ?: return null
            val index = keys.indexOf(key)
            return index.takeIf { it >= 0 }
        }

        coroutineScope {
        val scope = this
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            if (down.position.x < size.width - columnPx) return@awaitEachGesture
            val anchor = indexAt(down.position.y) ?: return@awaitEachGesture
            down.consume()
            val keys = orderState.value
            val baseline = selectedState.value.toSet()
            val selecting = keys[anchor] !in baseline
            var end = anchor
            fun apply(nextEnd: Int) {
                val lo = min(anchor, nextEnd)
                val hi = max(anchor, nextEnd)
                val next = baseline.toMutableSet()
                for (index in lo..hi) {
                    val key = keys[index]
                    if (selecting) next.add(key) else next.remove(key)
                }
                onSelectedState.value(next)
            }
            apply(end)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                change.consume()
                val y = change.position.y
                val depth = when {
                    y < edgePx -> (edgePx - y) / edgePx
                    y > size.height - edgePx -> (y - (size.height - edgePx)) / edgePx
                    else -> 0f
                }
                if (depth > 0f) {
                    val direction = if (y < edgePx) -1f else 1f
                    val distance = direction * (8f + 28f * depth)
                    scope.launch { listState.scrollBy(distance) }
                }
                val current = indexAt(y.coerceIn(0f, size.height.toFloat())) ?: continue
                if (current == end) continue
                end = current
                apply(end)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        }
    }
}
