package com.github.kr328.clash.design.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.FilesDesign
import com.github.kr328.clash.design.util.toBytesString
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.icon.icons.useful.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.getWindowSize
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun FilesScreen(design: FilesDesign) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val files = design.currentFiles()
    val editable = design.editable()

    Scaffold(
        topBar = {
            TopAppBar(
                title = MLang.files_page_title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 24.dp),
                        onClick = { design.requests.trySend(FilesDesign.Request.PopStack) }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Back,
                            contentDescription = MLang.action_back
                        )
                    }
                }
            )
        },
        content = { inner ->
            LazyColumn(
                modifier = Modifier
                    .height(getWindowSize().height.dp)
                    .padding(top = 16.dp)
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = inner
            ) {
                items(files, key = { it.id }) { file ->
                    var showMenu by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (file.isDirectory) {
                                            design.requests.trySend(FilesDesign.Request.OpenDirectory(file))
                                        } else {
                                            design.requests.trySend(FilesDesign.Request.OpenFile(file))
                                        }
                                    }
                            ) {
                                Text(
                                    text = file.name,
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = file.size.toBytesString(),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = MiuixIcons.Useful.More,
                                    contentDescription = MLang.action_more,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (showMenu) {
                        SuperBottomSheet(
                            show = remember { mutableStateOf(true) },
                            title = file.name,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (editable) {
                                BasicComponent(
                                    title = MLang.action_rename,
                                    onClick = {
                                        design.requests.trySend(FilesDesign.Request.RenameFile(file))
                                        showMenu = false
                                    }
                                )
                            }
                            BasicComponent(
                                title = MLang.action_delete,
                                onClick = {
                                    design.requests.trySend(FilesDesign.Request.DeleteFile(file))
                                    showMenu = false
                                }
                            )
                            BasicComponent(
                                title = MLang.action_import,
                                onClick = {
                                    design.requests.trySend(FilesDesign.Request.ImportFile(file))
                                    showMenu = false
                                }
                            )
                            BasicComponent(
                                title = MLang.action_export,
                                onClick = {
                                    design.requests.trySend(FilesDesign.Request.ExportFile(file))
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    )
}

