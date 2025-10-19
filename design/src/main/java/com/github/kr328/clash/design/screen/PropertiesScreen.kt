package com.github.kr328.clash.design.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.PropertiesDesign
import com.github.kr328.clash.service.model.Profile
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.icon.icons.useful.Save
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.util.concurrent.TimeUnit

@Composable
fun PropertiesScreen(design: PropertiesDesign) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val currentProfile = design.profile

    Scaffold(
        topBar = {
            TopAppBar(
                title = MLang.properties_page_title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 16.dp),
                        onClick = { (context as? android.app.Activity)?.finish() }
                    ) {
                        Icon(MiuixIcons.Useful.Back, MLang.action_back)
                    }
                },
                actions = {
                    if (design.progressing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            modifier = Modifier.padding(end = 16.dp),
                            onClick = { design.requestCommit() }
                        ) {
                            Icon(MiuixIcons.Useful.Save, MLang.action_save)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))

                // 提示信息
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = MLang.tips_properties,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // 名称输入
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    onClick = { design.inputName() }
                ) {
                    BasicComponent(
                        title = MLang.label_name,
                        summary = currentProfile?.name ?: MLang.profile_name_label
                    )
                }
            }

            // URL 输入
            if (currentProfile?.type != Profile.Type.File) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        onClick = { design.inputUrl() }
                    ) {
                        BasicComponent(
                            title = MLang.url_label,
                            summary = currentProfile?.source?.takeIf { it.isNotBlank() }
                                ?: MLang.accept_http_content
                        )
                    }
                }
            }

            // 自动更新间隔
            if (currentProfile?.type != Profile.Type.File) {
                item {
                    val interval = currentProfile?.interval ?: 0
                    val intervalText = if (interval == 0L) {
                        MLang.disabled
                    } else {
                        String.format(MLang.format_minutes, TimeUnit.MILLISECONDS.toMinutes(interval))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        onClick = { design.inputInterval() }
                    ) {
                        BasicComponent(
                            title = MLang.auto_update_label,
                            summary = intervalText
                        )
                    }
                }
            }

            // 浏览文件
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    onClick = { design.requestBrowseFiles() }
                ) {
                    BasicComponent(
                        title = MLang.browse_files,
                        summary = MLang.browse_files_summary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 进度对话框
        if (design.progressing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = design.fetchStatusText,
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                }
            }
        }
    }
}

