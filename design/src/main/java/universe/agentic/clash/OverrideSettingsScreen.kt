package universe.agentic.clash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets

data class SettingRow(
    val title: String,
    val summary: String,
    val enabled: Boolean = true,
    val selectable: Boolean = false,
    val opensPage: Boolean = false,
    val options: List<String> = emptyList(),
    val selected: Int = -1,
    val onClick: () -> Unit = {},
    val onSelect: (Int) -> Unit = {},
)

data class SettingGroup(
    val title: String,
    val rows: List<SettingRow>,
)

@Composable
fun OverrideSettingsScreen(
    insets: Insets,
    title: String,
    groups: List<SettingGroup>,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    SettingsPage(
        insets = insets,
        title = title,
        groups = groups,
        onBack = onBack,
        onReset = onReset,
    )
}

@Composable
internal fun SettingsPage(
    insets: Insets,
    title: String,
    groups: List<SettingGroup>,
    onBack: () -> Unit,
    onReset: () -> Unit,
    scrollAnchor: String? = null,
) {
    var openKey by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    var viewTop by remember { mutableFloatStateOf(Float.NaN) }
    var anchorTop by remember { mutableFloatStateOf(Float.NaN) }
    var aligned by remember(scrollAnchor) { mutableStateOf(false) }
    LaunchedEffect(scrollAnchor, viewTop, anchorTop) {
        if (aligned || scrollAnchor.isNullOrEmpty()) return@LaunchedEffect
        if (viewTop.isNaN() || anchorTop.isNaN()) return@LaunchedEffect
        aligned = true
        val delta = (anchorTop - viewTop).toInt()
        var dest = (scroll.value + delta).coerceAtLeast(0)
        repeat(4) {
            if (dest <= scroll.maxValue) {
                scroll.scrollTo(dest)
                return@LaunchedEffect
            }
            withFrameNanos { }
            dest = (scroll.value + delta).coerceAtLeast(0)
        }
        scroll.scrollTo(dest.coerceAtMost(scroll.maxValue))
    }
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = { ResetButton(onReset) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewTop = it.positionInRoot().y }
                .verticalScroll(scroll)
                .padding(top = IosContentTop, bottom = 24.dp),
        ) {
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    Spacer(Modifier.height(12.dp))
                }
                SettingGroupBlock(
                    group = group,
                    openKey = openKey,
                    onOpen = { openKey = it },
                    onAnchor = if (group.title == scrollAnchor) {
                        { anchorTop = it }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingGroupBlock(
    group: SettingGroup,
    openKey: String?,
    onOpen: (String?) -> Unit,
    onAnchor: ((Float) -> Unit)? = null,
) {
    val colors = IosTheme.colors
    val count = group.rows.size
    if (count == 0) return
    val placed = if (onAnchor != null) {
        Modifier.onGloballyPositioned { onAnchor(it.positionInRoot().y) }
    } else {
        Modifier
    }
    Column(placed) {
    Text(
        text = group.title,
        style = IosType.section,
        color = colors.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
    group.rows.forEachIndexed { index, row ->
        val summary = row.summary
        val onSide = summary.fitsTrailingValue()
        val key = "${group.title}:$index"
        Box {
            IosPreferenceRow(
                row = IosDetail(
                    title = row.title,
                    subtitle = if (summary.isNotBlank() && !onSide) summary else null,
                    value = if (onSide) summary else null,
                    enabled = row.enabled,
                    disclosure = row.opensPage,
                    onClick = if (row.enabled) {
                        {
                            if (row.selectable) onOpen(key) else row.onClick()
                        }
                    } else {
                        null
                    },
                ),
                index = index,
                count = count,
            )
            if (row.selectable) {
                IosPopupMenu(
                    expanded = openKey == key,
                    onDismiss = { onOpen(null) },
                    actions = row.options.mapIndexed { option, label ->
                        IosAction(
                            title = label,
                            marked = option == row.selected,
                            onClick = { row.onSelect(option) },
                        )
                    },
                )
            }
        }
    }
    }
}

@Composable
private fun ResetButton(onReset: () -> Unit) {
    IosIconButton(
        icon = R.drawable.ic_baseline_restore,
        description = stringResource(R.string.reset),
        tint = IosTheme.colors.red,
        onClick = onReset,
    )
}
