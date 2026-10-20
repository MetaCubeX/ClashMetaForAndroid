package com.github.kr328.clash.design.ios

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
) {
    var sheet by remember { mutableStateOf<SettingRow?>(null) }
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = { ResetButton(onReset) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    Spacer(Modifier.height(22.dp))
                }
                SettingGroupBlock(group) { row ->
                    if (row.selectable) {
                        sheet = row
                    } else {
                        row.onClick()
                    }
                }
            }
        }
        val current = sheet
        if (current != null) {
            IosOptionSheet(
                title = current.title,
                options = current.options,
                selected = current.selected,
                onSelect = current.onSelect,
                onDismiss = { sheet = null },
            )
        }
    }
}

@Composable
private fun SettingGroupBlock(
    group: SettingGroup,
    onOpen: (SettingRow) -> Unit,
) {
    val colors = IosTheme.colors
    val count = group.rows.size
    if (count == 0) return
    Text(
        text = group.title,
        style = IosType.footnote,
        color = colors.secondaryLabel,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 6.dp),
    )
    group.rows.forEachIndexed { index, row ->
        val summary = row.summary
        val onSide = summary.fitsTrailingValue()
        IosPreferenceRow(
            row = IosDetail(
                title = row.title,
                subtitle = if (summary.isNotBlank() && !onSide) summary else null,
                value = if (onSide) summary else null,
                enabled = row.enabled,
                onClick = if (row.enabled) {
                    { onOpen(row) }
                } else {
                    null
                },
            ),
            index = index,
            count = count,
        )
    }
}

@Composable
private fun ResetButton(onReset: () -> Unit) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .background(if (pressed) colors.pressed else Color.Transparent)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onReset)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(R.string.reset), style = IosType.body, color = colors.red)
    }
}
