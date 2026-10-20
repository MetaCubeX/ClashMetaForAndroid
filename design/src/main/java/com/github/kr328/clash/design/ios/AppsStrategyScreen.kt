package com.github.kr328.clash.design.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppsStrategyConfig
import java.util.UUID

@Composable
fun AppsStrategyScreen(
    insets: Insets,
    title: String,
    configs: List<AppsStrategyConfig>,
    activeUuid: UUID?,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onActive: (AppsStrategyConfig) -> Unit,
    onChoose: (AppsStrategyConfig) -> Unit,
    onEdit: (AppsStrategyConfig) -> Unit,
) {
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = {
            BarIconButton(
                icon = R.drawable.ic_baseline_add,
                description = stringResource(R.string._new),
                onClick = onCreate,
            )
        },
    ) {
        if (configs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                IosEmptyState(
                    message = stringResource(R.string.not_selected),
                    action = stringResource(R.string._new),
                    onAction = onCreate,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                itemsIndexed(configs, key = { _, config -> config.uuid }) { index, config ->
                    val mode = stringResource(config.mode.labelRes())
                    val subtitle = if (config.mode == AccessControlMode.AcceptAll) {
                        mode
                    } else {
                        val count = pluralStringResource(
                            R.plurals.selected_apps_count,
                            config.packages.size,
                            config.packages.size,
                        )
                        "$mode\n$count"
                    }
                    StrategyRow(
                        title = config.name,
                        subtitle = subtitle,
                        active = config.uuid == activeUuid,
                        showDivider = index < configs.lastIndex,
                        onActive = { onActive(config) },
                        onChoose = { onChoose(config) },
                        onEdit = { onEdit(config) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StrategyRow(
    title: String,
    subtitle: String,
    active: Boolean,
    showDivider: Boolean,
    onActive: () -> Unit,
    onChoose: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = IosTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.grouped)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                IosListRow(
                    row = IosDetail(
                        title = title,
                        subtitle = subtitle,
                        selection = if (active) IosSelection.Checked else IosSelection.None,
                        disclosure = false,
                        onClick = onActive,
                    ),
                    showDivider = false,
                )
            }
            BarIconButton(
                icon = R.drawable.ic_baseline_apps,
                description = stringResource(R.string.choose_apps),
                onClick = onChoose,
            )
            BarIconButton(
                icon = R.drawable.ic_baseline_edit,
                description = stringResource(R.string.edit),
                onClick = onEdit,
            )
        }
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

@Composable
private fun BarIconButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
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
            painter = painterResource(icon),
            contentDescription = description,
            tint = colors.blue,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun AccessControlMode.labelRes(): Int = when (this) {
    AccessControlMode.AcceptAll -> R.string.allow_all_apps
    AccessControlMode.AcceptSelected -> R.string.allow_selected_apps
    AccessControlMode.DenySelected -> R.string.deny_selected_apps
}
