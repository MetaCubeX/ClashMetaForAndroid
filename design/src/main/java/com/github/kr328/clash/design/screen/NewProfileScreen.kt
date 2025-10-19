package com.github.kr328.clash.design.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.NewProfileDesign
import com.github.kr328.clash.design.model.ProfileProvider
import com.github.kr328.clash.design.util.toBytesString
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.icon.icons.useful.Save
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.getWindowSize
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun NewProfileScreen(design: NewProfileDesign) {
    val providers = design.currentProviders()
    val scrollBehavior = MiuixScrollBehavior()
    val files = design.currentFiles()

    // 根据当前选择的 provider 判断是否可以保存
    val localProviders = if (providers.isEmpty()) listOf(
        ProfileProvider.File(design.context),
        ProfileProvider.Url(design.context)
    ) else providers
    val selected = localProviders.getOrNull(design.providerIndex)

    val canSave = when (selected) {
        is ProfileProvider.File -> files.isNotEmpty() && design.fileName.isNotBlank() && !design.isSaving
        is ProfileProvider.Url -> design.urlName.isNotBlank() && design.url.isNotBlank() && !design.isSaving
        else -> false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = MLang.new_profile_page_title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 24.dp),
                        onClick = { design.requests.trySend(NewProfileDesign.Request.Cancel) }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Back,
                            contentDescription = MLang.action_back
                        )
                    }
                },
                actions = {
                    IconButton(
                        modifier = Modifier.padding(end = 24.dp),
                        onClick = {
                            when (selected) {
                                is ProfileProvider.File -> {
                                    design.requests.trySend(NewProfileDesign.Request.SaveFile(design.fileName.trim()))
                                }

                                is ProfileProvider.Url -> {
                                    val hourOptions = listOf(
                                        0L,
                                        1L,
                                        3L,
                                        6L,
                                        12L,
                                        24L
                                    )
                                    val hours = hourOptions.getOrNull(design.urlHoursIndex) ?: 0L
                                    design.requests.trySend(
                                        NewProfileDesign.Request.SaveUrl(
                                            design.urlName.trim(),
                                            design.url.trim(),
                                            hours
                                        )
                                    )
                                }

                                else -> {}
                            }
                        },
                        enabled = canSave
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Save,
                            contentDescription = MLang.action_save
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .height(getWindowSize().height.dp)
                .padding(top = 16.dp)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 8.dp)
                ) {
                    SuperDropdown(
                        title = MLang.import_method,
                        summary = MLang.import_method_summary,
                        items = localProviders.map { it.name },
                        selectedIndex = design.providerIndex,
                        onSelectedIndexChange = { design.providerIndex = it }
                    )
                }
            }

            // 根据选择的 provider 显示标题
            item {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    },
                    label = "provider_title"
                ) { provider ->
                    when (provider) {
                        is ProfileProvider.File -> {
                            SmallTitle(
                                text = MLang.section_file,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        is ProfileProvider.Url -> {
                            SmallTitle(
                                text = MLang.section_url,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        is ProfileProvider.External -> {
                            SmallTitle(
                                text = MLang.section_external,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        else -> {}
                    }
                }
            }

            item {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    },
                    label = "provider_content",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)  // 固定高度，避免切换时的大小变化
                ) { provider ->
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (provider) {
                            is ProfileProvider.File -> {
                                FilesSection(design)
                            }

                            is ProfileProvider.Url -> {
                                UrlSection(design)
                            }

                            is ProfileProvider.External -> {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Column(
                                        Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(provider.name, style = MiuixTheme.textStyles.body1)
                                        Text(provider.summary, style = MiuixTheme.textStyles.body2)
                                        Row(
                                            horizontalArrangement = Arrangement.End,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            TextButton(
                                                text = MLang.action_open,
                                                onClick = { design.requestProvider(provider) },
                                                colors = ButtonDefaults.textButtonColorsPrimary()
                                            )
                                        }
                                    }
                                }
                            }

                            null -> {
                                // 不应出现
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DeterminateLinearProgress(
    modifier: Modifier = Modifier,
    fraction: Float,
    color: Color = Color.Blue,
    trackColor: Color = Color.LightGray
) {
    Canvas(modifier = modifier) {
        drawRect(trackColor, size = size)
        val w = size.width
        val h = size.height
        val fw = (w * fraction.coerceIn(0f, 1f))
        if (fw > 0f) {
            drawRect(
                color = color,
                topLeft = Offset(0f, 0f),
                size = Size(fw, h)
            )
        }
    }
}

@Composable
private fun DownloadProgressCard(design: NewProfileDesign) {
    AnimatedVisibility(
        visible = design.downloadVisible && design.downloadMax > 0,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (design.downloadTitle.isNotBlank()) design.downloadTitle else MLang.loading,
                    style = MiuixTheme.textStyles.body1
                )
                DeterminateLinearProgress(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    fraction = (design.downloadProgress.toFloat() / design.downloadMax.toFloat()).coerceIn(0f, 1f),
                    color = MiuixTheme.colorScheme.primary,
                    trackColor = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                )
            }
        }
    }
}

@Composable
private fun FilesSection(design: NewProfileDesign) {
    val files = design.currentFiles()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {


        // 配置文件
        Card {
            Column(Modifier.padding(12.dp)) {
                TextField(
                    value = design.fileName,
                    onValueChange = { design.fileName = it },
                    label = MLang.name_label,
                    useLabelAsPlaceholder = true,
                    textStyle = MiuixTheme.textStyles.headline2
                )
            }
            if (files.isNotEmpty()) {
                // 显示已选择的文件列表
                files.forEachIndexed { index, f ->
                    BasicComponent(
                        title = f.name,
                        summary = f.size.toBytesString(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (index < files.size - 1) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MiuixTheme.colorScheme.dividerLine
                        )
                    }
                }
            }

            // 导入操作（仅在新建模式下显示）
            if (!design.isEditMode) {
                com.github.kr328.clash.design.components.SettingRowArrow(
                    title = if (files.isEmpty()) MLang.import_config else MLang.reimport,
                    summary = if (files.isEmpty()) MLang.import_hint else MLang.reimport_hint,
                    rightText = MLang.action_import,
                    onClick = { design.requests.trySend(NewProfileDesign.Request.ImportFile(null)) }
                )
            }
        }


        DownloadProgressCard(design)


    }
}

@Composable
private fun UrlSection(design: NewProfileDesign) {
    val hourOptions = listOf(
        MLang.interval_none to 0L,
        MLang.interval_1h to 1L,
        MLang.interval_3h to 3L,
        MLang.interval_6h to 6L,
        MLang.interval_12h to 12L,
        MLang.interval_24h to 24L
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 配置信息
        Card {
            Column(Modifier.padding(12.dp)) {
                TextField(
                    value = design.urlName,
                    onValueChange = { design.urlName = it },
                    label = MLang.name_label,
                    useLabelAsPlaceholder = true,
                    textStyle = MiuixTheme.textStyles.headline2
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = design.url,
                    onValueChange = { design.url = it },
                    label = MLang.url_label,
                    useLabelAsPlaceholder = true,
                    textStyle = MiuixTheme.textStyles.headline2
                )
            }

            SuperDropdown(
                title = MLang.auto_update,
                summary = MLang.auto_update_summary,
                items = hourOptions.map { it.first },
                selectedIndex = design.urlHoursIndex,
                onSelectedIndexChange = { design.urlHoursIndex = it }
            )
        }

        DownloadProgressCard(design)
    }
}

