package com.github.kr328.clash.design.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.ProfilesDesign
import com.github.kr328.clash.design.theme.AppDimensions
import com.github.kr328.clash.design.theme.AppShapes
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.util.toRelativeTimeString
import com.github.kr328.clash.service.model.Profile
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.icon.icons.useful.New
import top.yukonga.miuix.kmp.icon.icons.useful.Update
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun ProfilesScreen(design: ProfilesDesign) {
    val scrollBehavior = MiuixScrollBehavior()
    val profiles = design.profiles
    val ctx = LocalContext.current

    // 模式：0 = 激活模式, 1 = 编辑模式
    val isEditMode = design.modeIndex == 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = MLang.profiles_page_title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 24.dp),
                        onClick = { (ctx as? android.app.Activity)?.finish() }) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Back,
                            contentDescription = MLang.action_back
                        )
                    }
                },
                actions = {
                    IconButton(
                        modifier = Modifier.padding(end = 16.dp),
                        onClick = { design.requestCreate() }) {
                        Icon(
                            imageVector = MiuixIcons.Useful.New,
                            contentDescription = MLang.action_add
                        )
                    }
                    IconButton(
                        modifier = Modifier.padding(end = 24.dp),
                        onClick = { design.requestUpdateAll() },
                        enabled = !design.allUpdating
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Update,
                            contentDescription = MLang.action_update_all
                        )
                    }
                }
            )
        },
        content = { paddingValues ->
            if (profiles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // 模式切换
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = AppDimensions.Padding.medium,
                                vertical = AppDimensions.Padding.small
                            )
                    ) {
                        SuperDropdown(
                            title = MLang.mode_title,
                            summary = MLang.mode_summary,
                            items = listOf(MLang.mode_activate, MLang.mode_edit),
                            selectedIndex = design.modeIndex,
                            onSelectedIndexChange = { index ->
                                design.modeIndex = index
                                design.clearSelection()
                            }
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = MLang.empty_message,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = MLang.empty_hint,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = paddingValues
                ) {
                    // 模式切换
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = AppDimensions.Padding.medium,
                                    vertical = AppDimensions.Padding.small
                                )
                        ) {
                            SuperDropdown(
                                title = MLang.mode_title,
                                summary = MLang.mode_summary,
                                items = listOf(MLang.mode_activate, MLang.mode_edit),
                                selectedIndex = design.modeIndex,
                                onSelectedIndexChange = { index ->
                                    design.modeIndex = index
                                    design.clearSelection()
                                }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // 配置文件列表
                    items(profiles, key = { it.uuid }) { p ->
                        ProfileCard(
                            design = design,
                            profile = p,
                            isEditMode = isEditMode
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    )

    // 对话框状态
    val showDialog = design.showDialog
    val dialogProfile = design.dialogProfile

    // 未保存配置对话框
    if (showDialog && dialogProfile != null) {
        SuperDialog(
            title = MLang.unsaved_title,
            summary = MLang.unsaved_summary,
            show = remember { mutableStateOf(true) },
            onDismissRequest = { design.showDialog = false }
        ) {
            TextButton(
                text = MLang.action_edit,
                onClick = {
                    design.requests.trySend(ProfilesDesign.Request.Edit(dialogProfile))
                    design.showDialog = false
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 编辑模式操作菜单
    if (design.showMenuDialog && design.menuProfile != null) {
        val profile = design.menuProfile!!
        SuperBottomSheet(
            show = remember { mutableStateOf(true) },
            title = profile.name,
            onDismissRequest = { design.showMenuDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 更新配置（仅远程配置）
                if (profile.imported && profile.type != Profile.Type.File) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SuperArrow(
                            title = MLang.action_update_config,
                            summary = MLang.action_update_summary,
                            onClick = {
                                design.requests.trySend(ProfilesDesign.Request.Update(profile))
                                design.showMenuDialog = false
                            }
                        )
                    }
                }

                // 编辑配置
                Card(modifier = Modifier.fillMaxWidth()) {
                    SuperArrow(
                        title = MLang.action_edit_config,
                        summary = MLang.action_edit_summary,
                        onClick = {
                            design.requests.trySend(ProfilesDesign.Request.Edit(profile))
                            design.showMenuDialog = false
                        }
                    )
                }

                // 复制配置（仅已导入配置）
                if (profile.imported) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SuperArrow(
                            title = MLang.action_copy_config,
                            summary = MLang.action_copy_summary,
                            onClick = {
                                design.requests.trySend(ProfilesDesign.Request.Duplicate(profile))
                                design.showMenuDialog = false
                            }
                        )
                    }
                }

                // 删除配置
                Card(modifier = Modifier.fillMaxWidth()) {
                    SuperArrow(
                        title = MLang.action_delete_config,
                        summary = MLang.action_delete_summary,
                        onClick = {
                            design.requests.trySend(ProfilesDesign.Request.Delete(profile))
                            design.showMenuDialog = false
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ProfileCard(
    design: ProfilesDesign,
    profile: Profile,
    isEditMode: Boolean
) {
    val p = profile
    val updatedText = remember(p.updatedAt) { p.updatedAt.toRelativeTimeString() }
    val isActive = p.active
    val isPending = p.pending
    val used = p.upload + p.download
    val total = p.total
    val progress = if (total > 0) used.toFloat() / total else 0f

    // 构建标题（名称 + 未保存标签）
    val titleWithBadge = @Composable {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = p.name,
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isPending) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFFE53935).copy(alpha = 0.12f),
                            shape = AppShapes.small
                        )
                        .padding(
                            horizontal = AppDimensions.Padding.small,
                            vertical = AppDimensions.Padding.xxs
                        )
                ) {
                    Text(
                        text = MLang.unsaved_label,
                        style = MiuixTheme.textStyles.body2,
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // 构建摘要（类型 + 更新时间 + 流量/到期信息）
    val summaryText = buildString {
        append(
            when (p.type) {
                Profile.Type.File -> MLang.type_local
                Profile.Type.Url -> MLang.type_url
                Profile.Type.External -> MLang.type_external
            }
        )
        append(" · ")
        append(updatedText)
        if (total > 0) {
            append("\n${used.toBytesString()} / ${total.toBytesString()}")
            if (p.expire > 0) {
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date(p.expire))
                append(" · ${String.format(MLang.expires_at, dateStr)}")
            }
        } else if (p.expire > 0) {
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date(p.expire))
            append("\n${String.format(MLang.expires_at, dateStr)}")
        }
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .border(
                width = AppDimensions.Border.normal,
                color = if (isActive) MiuixTheme.colorScheme.primary else Color.Transparent,
                shape = AppShapes.extraLarge
            )
            .clip(AppShapes.extraLarge)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp),
            onClick = {
                if (isEditMode) {
                    design.menuProfile = p
                    design.showMenuDialog = true
                } else if (isPending) {
                    design.dialogProfile = p
                    design.showDialog = true
                } else {
                    design.requestActive(p)
                }
            },
            insideMargin = PaddingValues(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                // 使用简化的布局：标题行 + Checkbox 固定占位
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        titleWithBadge()
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    // 固定大小的占位区域，避免切换模式时大小变化
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isEditMode) {
                            Checkbox(
                                checked = isActive,
                                onCheckedChange = { design.requestActive(p) }
                            )
                        }
                    }
                }

                // 摘要信息
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = summaryText,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )

                // 流量进度条
                if (total > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(AppShapes.small),
                        color = when {
                            progress > 0.9f -> Color(0xFFE53935)
                            progress > 0.7f -> Color(0xFFFB8C00)
                            else -> MiuixTheme.colorScheme.primary
                        },
                        trackColor = MiuixTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

