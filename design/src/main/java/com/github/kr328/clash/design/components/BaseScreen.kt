package com.github.kr328.clash.design.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.theme.AppDimensions
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.icon.icons.useful.New
import top.yukonga.miuix.kmp.utils.getWindowSize
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 标准页面脚手架，提供统一的页面结构，包含 TopAppBar、返回按钮和操作按钮
 */
@Composable
fun StandardScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            modifier = Modifier.padding(start = AppDimensions.spacing_xxl),
                            onClick = onBack
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Useful.Back,
                                contentDescription = MLang.action_back
                            )
                        }
                    }
                },
                actions = actions
            )
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}

/**
 * 带 LazyColumn 的标准列表页面，在 StandardScreen 基础上提供了 LazyColumn 容器，并支持空状态显示
 */
@Composable
fun StandardListScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    emptyState: (@Composable (PaddingValues) -> Unit)? = null,
    isEmpty: Boolean = false,
    contentPaddingTop: androidx.compose.ui.unit.Dp = AppDimensions.spacing_lg,
    content: LazyListScope.() -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            modifier = Modifier.padding(start = AppDimensions.spacing_xxl),
                            onClick = onBack
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Useful.Back,
                                contentDescription = MLang.action_back
                            )
                        }
                    }
                },
                actions = actions
            )
        }
    ) { paddingValues ->
        if (isEmpty && emptyState != null) {
            emptyState(paddingValues)
        } else {
            LazyColumn(
                modifier = Modifier
                    .height(getWindowSize().height.dp)
                    .padding(top = contentPaddingTop)
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues
            ) {
                content()
            }
        }
    }
}

/**
 * 编辑器页面脚手架，专门用于编辑器类型的页面，右上角固定显示添加按钮
 */
@Composable
fun EditorScreen(
    title: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    showAddButton: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    StandardScreen(
        title = title,
        onBack = onBack,
        actions = {
            if (showAddButton) {
                IconButton(
                    modifier = Modifier.padding(end = AppDimensions.spacing_xxl),
                    onClick = onAdd
                ) {
                    Icon(
                        imageVector = MiuixIcons.Useful.New,
                        contentDescription = MLang.action_add
                    )
                }
            }
        },
        content = content
    )
}

/**
 * 标准空状态容器，用于在列表页面中显示空状态
 */
@Composable
fun EmptyStateContainer(
    paddingValues: PaddingValues,
    message: String,
    hint: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(message = message, hint = hint)
    }
}

