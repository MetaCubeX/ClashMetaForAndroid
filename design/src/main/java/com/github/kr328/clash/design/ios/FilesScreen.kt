package com.github.kr328.clash.design.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
    var menu by remember { mutableStateOf<File?>(null) }
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                itemsIndexed(files, key = { _, file -> file.id }) { index, file ->
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
                        IosSideActionRow(
                            row = detail,
                            actionIcon = painterResource(R.drawable.ic_baseline_more_vert),
                            actionDescription = stringResource(R.string.more),
                            onAction = { menu = file },
                            showDivider = index < files.lastIndex,
                        )
                    }
                }
            }
        }
        menu?.let { file ->
            IosActionSheet(
                actions = fileActions(
                    file = file,
                    currentInBaseDir = currentInBaseDir,
                    configurationEditable = configurationEditable,
                    onImport = onImport,
                    onExport = onExport,
                    onRename = onRename,
                    onDelete = onDelete,
                ),
                onDismiss = { menu = null },
                cancelLabel = stringResource(R.string.cancel),
                title = file.name,
            )
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
            tint = IosTheme.colors.blue,
            modifier = Modifier.size(22.dp),
        )
    }
}
