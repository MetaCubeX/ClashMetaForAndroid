package universe.agentic.clash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontWeight
import nota.ui.theme.shellPressBackground
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import nota.ui.theme.ShellShapes
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppsStrategyConfig
import java.util.UUID

data class AppSetCardUi(
    val id: String,
    val name: String,
    val description: String,
    val builtin: Boolean,
    val members: String,
)

@Composable
fun AppsStrategyScreen(
    insets: Insets,
    title: String,
    configs: List<AppsStrategyConfig>,
    activeUuid: UUID?,
    sets: List<AppSetCardUi>,
    covered: Map<UUID, String>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onActive: (UUID?) -> Unit,
    onChoose: (UUID) -> Unit,
    onEdit: (AppsStrategyConfig) -> Unit,
    onCreateSet: () -> Unit,
    onEditSet: (String) -> Unit,
    onDeleteSet: (String) -> Unit,
    onOpenAccess: (UUID) -> Unit,
    embedded: Boolean = false,
) {
    val colors = IosTheme.colors
    var section by remember { mutableStateOf(0) }

    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        embedded = embedded,
        trailing = if (section == 2) {
            null
        } else {
            {
                IosIconButton(
                    icon = R.drawable.ic_baseline_add,
                    description = stringResource(R.string._new),
                    onClick = if (section == 0) onCreate else onCreateSet,
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(IosContentTop))
            IosSegmentedControl(
                items = listOf(
                    stringResource(R.string.strategy_section_list),
                    stringResource(R.string.strategy_section_sets),
                    stringResource(R.string.strategy_section_access),
                ),
                selected = section,
                onSelect = { section = it },
            )
            Spacer(Modifier.height(IosContentTop))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
            if (section == 0) {
            // Section 1: Custom Split Strategies
            item(key = "section-strategies-header") {
                SectionLabel(stringResource(R.string.split_strategies))
            }

            if (configs.isEmpty()) {
                item(key = "strategies-empty") {
                    Box(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clip(ShellShapes.Container)
                            .background(colors.grouped)
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.not_selected),
                            style = IosType.footnote,
                            color = colors.secondaryLabel,
                        )
                    }
                }
            } else {
                item(key = "strategies-grouped-list") {
                    Column(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clip(ShellShapes.Container)
                            .background(colors.grouped),
                    ) {
                        configs.forEachIndexed { index, config ->
                            val active = config.uuid == activeUuid
                            val countText = if (config.mode == AccessControlMode.AcceptAll) {
                                stringResource(R.string.allow_all_apps)
                            } else {
                                pluralStringResource(
                                    R.plurals.selected_apps_count,
                                    config.packages.size,
                                    config.packages.size,
                                )
                            }

                            StrategyCardContent(
                                title = config.name,
                                modeBadge = stringResource(config.mode.shortLabelRes()),
                                subtitle = countText,
                                covered = covered[config.uuid].orEmpty(),
                                active = active,
                                onClick = { if (!active) onActive(config.uuid) },
                                actions = {
                                    IosIconButton(
                                        icon = R.drawable.ic_baseline_edit,
                                        description = stringResource(R.string.edit),
                                        onClick = { onEdit(config) },
                                    )
                                    IosIconButton(
                                        icon = R.drawable.ic_baseline_apps,
                                        description = stringResource(R.string.choose_apps),
                                        onClick = { onChoose(config.uuid) },
                                    )
                                },
                            )

                            if (index < configs.lastIndex) {
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
                }
            }
            }
            if (section == 1) {
                item(key = "app-set-management") {
                    AppSetManagement(
                        sets = sets,
                        onEdit = onEditSet,
                        onDelete = onDeleteSet,
                    )
                }
            }
            if (section == 2) {
                item(key = "access-entry") {
                    val config = configs.find { it.uuid == activeUuid }
                    if (config == null) {
                        Box(
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth()
                                .clip(ShellShapes.Container)
                                .background(colors.grouped)
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.not_selected),
                                style = IosType.footnote,
                                color = colors.secondaryLabel,
                            )
                        }
                    } else {
                        AccessEntry(
                            title = config.name,
                            detail = if (config.mode == AccessControlMode.AcceptAll) {
                                stringResource(R.string.allow_all_apps)
                            } else {
                                pluralStringResource(
                                    R.plurals.selected_apps_count,
                                    config.packages.size,
                                    config.packages.size,
                                )
                            },
                            covered = covered[config.uuid].orEmpty(),
                            onOpen = { onOpenAccess(config.uuid) },
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun AppSetManagement(
    sets: List<AppSetCardUi>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = IosTheme.colors
    if (sets.isEmpty()) {
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(ShellShapes.Container)
                .background(colors.grouped)
                .padding(vertical = 24.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.list_empty),
                style = IosType.footnote,
                color = colors.secondaryLabel,
            )
        }
        return
    }
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(ShellShapes.Container)
            .background(colors.grouped),
    ) {
        sets.forEachIndexed { index, set ->
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IosLeadingTile(icon = R.drawable.ic_baseline_apps)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = set.name,
                                style = IosType.headline,
                                color = colors.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            IosStatusPill(
                                text = stringResource(
                                    if (set.builtin) R.string.app_set_builtin else R.string.app_set_custom,
                                ),
                                color = colors.track,
                                contentColor = colors.label,
                            )
                        }
                        if (set.description.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = set.description,
                                style = IosType.footnote,
                                color = colors.secondaryLabel,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IosIconButton(
                        icon = R.drawable.ic_baseline_edit,
                        description = stringResource(R.string.edit_app_set),
                        onClick = { onEdit(set.id) },
                    )
                    if (!set.builtin) {
                        IosIconButton(
                            icon = R.drawable.ic_baseline_delete,
                            description = stringResource(R.string.delete_app_set),
                            onClick = { onDelete(set.id) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = set.members,
                    style = IosType.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index < sets.lastIndex) {
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
}

@Composable
private fun AccessEntry(
    title: String,
    detail: String,
    covered: String,
    onOpen: () -> Unit,
) {
    val colors = IosTheme.colors
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(ShellShapes.Container)
            .background(colors.grouped)
            .padding(16.dp),
    ) {
        Text(text = title, style = IosType.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(text = detail, style = IosType.footnote, color = colors.secondaryLabel)
        if (covered.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(text = covered, style = IosType.footnote, color = colors.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        IosButton(
            text = stringResource(R.string.open_access_list),
            style = IosButtonStyle.Filled,
            onClick = onOpen,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = IosType.section,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun StrategyCardContent(
    title: String,
    modeBadge: String? = null,
    subtitle: String,
    covered: String = "",
    active: Boolean,
    onClick: () -> Unit,
    actions: @Composable () -> Unit,
) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Column(
        Modifier
            .fillMaxWidth()
            .background(if (active) colors.label.copy(alpha = 0.12f) else Color.Transparent)
            .shellPressBackground(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        // Top row: Title + Badges on left, Right Checkmark on right
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = IosType.headline.copy(
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (modeBadge != null) {
                    Spacer(Modifier.width(8.dp))
                    IosStatusPill(
                        text = modeBadge,
                        color = colors.track,
                        contentColor = colors.label,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            IosChoiceMark(selected = active)
        }

        Spacer(Modifier.height(8.dp))

        // Bottom row: subtitle on left, action icon buttons on right
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = subtitle,
                    style = IosType.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (covered.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = covered,
                        style = IosType.footnote,
                        color = colors.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                actions()
            }
        }
    }
}

private fun AccessControlMode.shortLabelRes(): Int = when (this) {
    AccessControlMode.AcceptAll -> R.string.strategy_mode_allow_all
    AccessControlMode.AcceptSelected -> R.string.strategy_mode_allow_selected
    AccessControlMode.DenySelected -> R.string.strategy_mode_deny_selected
}
