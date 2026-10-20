package com.github.kr328.clash.design.ios

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.AppSetCheckState
import com.github.kr328.clash.design.model.AppSetIds
import com.github.kr328.clash.design.model.appSetMembership
import com.github.kr328.clash.design.ui.Insets

private val accessSorts = listOf(
    AppInfoSort.Label,
    AppInfoSort.PackageName,
    AppInfoSort.InstallTime,
    AppInfoSort.UpdateTime,
)

@Composable
fun AccessControlScreen(
    insets: Insets,
    title: String,
    query: String,
    keyword: String,
    apps: List<AppInfo>,
    selection: Set<String>,
    systemPackages: Set<String>,
    domesticPackages: Set<String>,
    loading: Boolean,
    hideSystem: Boolean,
    sort: AppInfoSort,
    reversed: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onHideSystem: (Boolean) -> Unit,
    onToggleApp: (String) -> Unit,
    onToggleSet: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectInvert: () -> Unit,
    onSort: (AppInfoSort) -> Unit,
    onReverse: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    // Keyword only hides rows. design.apps stays the loadAccessControlApps list.
    val visible = remember(apps, keyword) {
        if (keyword.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(keyword, ignoreCase = true) ||
                    app.packageName.contains(keyword, ignoreCase = true)
            }
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    val sortLabels = listOf(
        stringResource(R.string.name),
        stringResource(R.string.package_name),
        stringResource(R.string.install_time),
        stringResource(R.string.update_time),
    )

    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = {
            BarIconButton(
                icon = R.drawable.ic_baseline_more_vert,
                description = stringResource(R.string.more),
                onClick = { menuOpen = true },
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            IosSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.search),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "access-filter") {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        SectionLabel(stringResource(R.string.filter))
                        IosSwitchRow(
                            title = hideSystemAppsLabel(),
                            summary = null,
                            checked = hideSystem,
                            index = 0,
                            count = 1,
                            onCheckedChange = onHideSystem,
                        )
                    }
                }
                if (loading) {
                    item(key = "access-progress") {
                        IosProgressRow(stringResource(R.string.loading))
                    }
                } else {
                    item(key = "access-sets") {
                        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            SectionLabel(stringResource(R.string.app_sets))
                            SetRow(
                                id = AppSetIds.SYSTEM,
                                title = stringResource(R.string.system_apps),
                                members = systemPackages,
                                selection = selection,
                                index = 0,
                                count = 2,
                                onToggleSet = onToggleSet,
                            )
                            SetRow(
                                id = AppSetIds.DOMESTIC,
                                title = stringResource(R.string.app_set_domestic),
                                members = domesticPackages,
                                selection = selection,
                                index = 1,
                                count = 2,
                                onToggleSet = onToggleSet,
                            )
                        }
                    }
                    item(key = "access-selected") {
                        SectionLabel(
                            pluralStringResource(
                                R.plurals.selected_apps_count,
                                selection.size,
                                selection.size,
                            ),
                        )
                    }
                    if (visible.isEmpty()) {
                        item(key = "access-empty") {
                            IosEmptyState(
                                message = if (keyword.isEmpty()) {
                                    stringResource(R.string.app_set_summary_empty)
                                } else {
                                    stringResource(R.string.search_empty)
                                },
                            )
                        }
                    } else {
                        itemsIndexed(visible, key = { _, app -> app.packageName }) { index, app ->
                            val checked = app.packageName in selection
                            IosListRow(
                                row = IosDetail(
                                    title = app.label,
                                    subtitle = app.packageName,
                                    selection = if (checked) {
                                        IosSelection.Checked
                                    } else {
                                        IosSelection.None
                                    },
                                    disclosure = false,
                                    onClick = { onToggleApp(app.packageName) },
                                ),
                                showDivider = index < visible.lastIndex,
                            )
                        }
                    }
                }
            }
        }
        if (menuOpen) {
            IosActionSheet(
                actions = listOf(
                    IosAction(stringResource(R.string.select_all), onClick = onSelectAll),
                    IosAction(stringResource(R.string.select_none), onClick = onSelectNone),
                    IosAction(stringResource(R.string.select_invert), onClick = onSelectInvert),
                    IosAction(stringResource(R.string.sort)) { sortOpen = true },
                    IosAction(stringResource(R.string.reverse), marked = reversed, onClick = onReverse),
                    IosAction(stringResource(R.string.import_from_clipboard), onClick = onImport),
                    IosAction(stringResource(R.string.export_to_clipboard), onClick = onExport),
                ),
                onDismiss = { menuOpen = false },
                cancelLabel = stringResource(R.string.cancel),
            )
        }
        if (sortOpen) {
            IosOptionSheet(
                title = stringResource(R.string.sort),
                options = sortLabels,
                selected = accessSorts.indexOf(sort).coerceAtLeast(0),
                onSelect = { index -> onSort(accessSorts[index]) },
                onDismiss = { sortOpen = false },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = IosType.footnote,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 32.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun SetRow(
    id: String,
    title: String,
    members: Set<String>,
    selection: Set<String>,
    index: Int,
    count: Int,
    onToggleSet: (String) -> Unit,
) {
    val membership = appSetMembership(members, selection)
    val enabled = members.isNotEmpty()
    val summary = if (!enabled) {
        stringResource(R.string.app_set_summary_empty)
    } else {
        stringResource(R.string.app_set_summary, membership.total, membership.selectedCount)
    }
    IosPreferenceRow(
        row = IosDetail(
            title = title,
            subtitle = summary,
            enabled = enabled,
            selection = when (membership.state) {
                AppSetCheckState.Checked -> IosSelection.Checked
                AppSetCheckState.Indeterminate -> IosSelection.Mixed
                AppSetCheckState.Unchecked -> IosSelection.None
            },
            disclosure = false,
            onClick = if (enabled) {
                { onToggleSet(id) }
            } else {
                null
            },
        ),
        index = index,
        count = count,
    )
}

@Composable
private fun hideSystemAppsLabel(): String {
    val config = LocalConfiguration.current
    @Suppress("DEPRECATION")
    val locale = if (Build.VERSION.SDK_INT >= 24) config.locales[0] else config.locale
    return when (locale.language) {
        "zh" -> when (locale.country) {
            "TW", "HK", "MO" -> "隱藏系統應用"
            else -> "隐藏系统应用"
        }
        else -> "Hide system apps"
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
