package universe.agentic.clash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import nota.ui.theme.ShellShapes
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import nota.ui.theme.rememberShellPressAlpha
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.File
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.toBytesString

@Composable
fun FilesScreen(
    insets: Insets,
    title: String,
    files: List<File>,
    currentInBaseDir: Boolean,
    configurationEditable: Boolean,
    now: Long,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (File) -> Unit,
    onImport: (File) -> Unit,
    onExport: (File) -> Unit,
    onRename: (File) -> Unit,
    onDelete: (File) -> Unit,
) {
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = if (currentInBaseDir) {
            null
        } else {
            {
                BarIcon(stringResource(R.string._new), onNew)
            }
        },
    ) {
        if (files.isEmpty()) {
            IosEmptyState(message = stringResource(R.string.list_empty))
        } else {
            val context = LocalContext.current
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = IosContentTop),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(ShellShapes.Container)
                        .background(IosTheme.colors.grouped),
                ) {
                files.forEachIndexed { index, file ->
                    val actions = fileActions(
                        file = file,
                        currentInBaseDir = currentInBaseDir,
                        configurationEditable = configurationEditable,
                        onImport = onImport,
                        onExport = onExport,
                        onRename = onRename,
                        onDelete = onDelete,
                    )
                    val elapsed = if (file.isDirectory || file.lastModified <= 0L) {
                        null
                    } else {
                        (now - file.lastModified).elapsedIntervalString(context)
                    }
                    val detail = IosDetail(
                        title = file.name,
                        subtitle = if (file.isDirectory) null else file.size.toBytesString(),
                        value = elapsed,
                        disclosure = actions.isEmpty(),
                        onClick = { onOpen(file) },
                    )
                    if (actions.isEmpty()) {
                        IosListRow(detail, showDivider = index < files.lastIndex)
                    } else {
                        var open by remember(file.id) { mutableStateOf(false) }
                        IosSideActionRow(
                            row = detail,
                            actionIcon = painterResource(R.drawable.ic_baseline_more_vert),
                            actionDescription = stringResource(R.string.more),
                            onAction = { open = true },
                            showDivider = index < files.lastIndex,
                            menu = {
                                IosPopupMenu(
                                    expanded = open,
                                    onDismiss = { open = false },
                                    actions = actions,
                                )
                            },
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun fileActions(
    file: File,
    currentInBaseDir: Boolean,
    configurationEditable: Boolean,
    onImport: (File) -> Unit,
    onExport: (File) -> Unit,
    onRename: (File) -> Unit,
    onDelete: (File) -> Unit,
): List<IosAction> {
    val importTitle = stringResource(R.string.import_)
    val exportTitle = stringResource(R.string.export)
    val renameTitle = stringResource(R.string.rename)
    val deleteTitle = stringResource(R.string.delete)
    return buildList {
        if (!file.isDirectory && (!currentInBaseDir || configurationEditable)) {
            add(IosAction(importTitle) { onImport(file) })
        }
        if (!file.isDirectory && file.size > 0) {
            add(IosAction(exportTitle) { onExport(file) })
        }
        if (!currentInBaseDir) {
            add(IosAction(renameTitle) { onRename(file) })
            add(IosAction(deleteTitle, destructive = true) { onDelete(file) })
        }
    }
}

@Composable
private fun BarIcon(description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed = rememberShellPressAlpha(interaction)
    Box(
        Modifier
            .size(44.dp)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = description,
            tint = IosTheme.colors.label,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { alpha = pressed },
        )
    }
}
