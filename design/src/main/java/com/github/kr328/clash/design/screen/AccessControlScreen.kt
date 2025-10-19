package com.github.kr328.clash.design.screen

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.github.kr328.clash.design.AccessControlDesign
import com.github.kr328.clash.design.components.EmptyState
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.model.AppInfoSort
import dev.oom_wg.purejoy.mlang.MLang
import dev.oom_wg.purejoy.mlang.MLang.more_title
import dev.oom_wg.purejoy.mlang.MLang.reverse_order
import dev.oom_wg.purejoy.mlang.MLang.reverse_order_summary
import dev.oom_wg.purejoy.mlang.MLang.show_system
import dev.oom_wg.purejoy.mlang.MLang.show_system_summary
import dev.oom_wg.purejoy.mlang.MLang.sort_method
import dev.oom_wg.purejoy.mlang.MLang.sort_method_summary
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.icon.icons.useful.Delete
import top.yukonga.miuix.kmp.icon.icons.useful.More
import top.yukonga.miuix.kmp.icon.icons.useful.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessControlScreen(design: AccessControlDesign) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current

    var searchText by rememberSaveable { mutableStateOf("") }
    var showMoreBottomSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(searchText) {
        design.query = searchText
    }

    // 使用本地状态跟踪 UiStore 的值，确保 UI 可以响应变化
    var showSystemApps by remember { mutableStateOf(design.uiStore.accessControlSystemApp) }
    var reverseOrder by remember { mutableStateOf(design.uiStore.accessControlReverse) }
    var sortBy by remember { mutableStateOf(design.uiStore.accessControlSort) }

    val filteredApps by remember {
        derivedStateOf {
            val base = if (showSystemApps) {
                design.apps
            } else {
                design.apps.filter { !it.isSystemApp }
            }

            if (searchText.isBlank()) {
                base
            } else {
                base.filter {
                    it.label.contains(searchText, ignoreCase = true) ||
                            it.packageName.contains(searchText, ignoreCase = true)
                }
            }
        }
    }

    val sortedApps by remember {
        derivedStateOf {
            val comparator = compareByDescending<AppInfo> { design.selected.containsKey(it.packageName) }
                .then(sortBy)
            if (reverseOrder) {
                filteredApps.sortedWith(comparator.reversed())
            } else {
                filteredApps.sortedWith(comparator)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = MLang.access_control_page_title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 24.dp),
                        onClick = { (context as? android.app.Activity)?.finish() }
                    ) {
                        Icon(MiuixIcons.Useful.Back, MLang.action_back)
                    }
                },
                actions = {
                    IconButton(
                        modifier = Modifier.padding(end = 24.dp),
                        onClick = { showMoreBottomSheet = true }
                    ) {
                        Icon(MiuixIcons.Useful.More, MLang.action_more)
                    }
                }
            )
        }
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current

        Column(modifier = Modifier.fillMaxSize()) {
            // 搜索栏
            InputField(
                query = searchText,
                onQueryChange = { searchText = it },
                label = MLang.search_label,
                leadingIcon = {
                    Icon(
                        imageVector = MiuixIcons.Useful.Search,
                        contentDescription = MLang.action_search,
                        modifier = Modifier
                            .size(44.dp)
                            .padding(start = 16.dp, end = 8.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        searchText.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Delete,
                            tint = MiuixTheme.colorScheme.onSurface,
                            contentDescription = MLang.action_clear,
                            modifier = Modifier
                                .size(44.dp)
                                .padding(start = 8.dp, end = 16.dp)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(
                        top = paddingValues.calculateTopPadding() + 12.dp,
                        bottom = 6.dp
                    ),
                onSearch = { },
                expanded = false,
                onExpandedChange = { }
            )

            val contentPadding = PaddingValues(
                top = 0.dp,
                start = paddingValues.calculateStartPadding(layoutDirection),
                end = paddingValues.calculateEndPadding(layoutDirection),
                bottom = paddingValues.calculateBottomPadding()
            )

            var isRefreshing by rememberSaveable { mutableStateOf(false) }
            val pullToRefreshState = rememberPullToRefreshState()
            val listState = rememberLazyListState()

            LaunchedEffect(isRefreshing) {
                if (isRefreshing) {
                    delay(350)
                    design.requests.trySend(AccessControlDesign.Request.ReloadApps)
                    isRefreshing = false
                }
            }

            // 当排序或过滤设置改变时，滚动到顶部
            LaunchedEffect(showSystemApps, reverseOrder, sortBy) {
                listState.scrollToItem(0)
            }

            when {
                !design.hasPermission -> {
                    EmptyState(
                        message = MLang.no_permission,
                        hint = MLang.grant_permission,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }

                design.isLoading -> {
                    EmptyState(
                        message = MLang.loading,
                        hint = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }

                sortedApps.isEmpty() -> {
                    EmptyState(
                        message = if (searchText.isNotBlank()) MLang.no_match else MLang.no_apps,
                        hint = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }

                else -> {
                    PullToRefresh(
                        isRefreshing = isRefreshing,
                        onRefresh = { isRefreshing = true },
                        pullToRefreshState = pullToRefreshState,
                        refreshTexts = listOf(
                            MLang.pull_to_refresh,
                            MLang.release_to_refresh,
                            MLang.refreshing,
                            MLang.refresh_complete
                        ),
                        contentPadding = contentPadding
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = contentPadding,
                            overscrollEffect = null
                        ) {
                            items(sortedApps, key = { info -> info.packageName }) { app ->
                                AppRow(
                                    app = app,
                                    context = context,
                                    checked = design.selected[app.packageName] ?: false,
                                    onCheckedChange = { checked ->
                                        if (checked) design.selected[app.packageName] = true
                                        else design.selected.remove(app.packageName)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMoreBottomSheet) {
        SuperBottomSheet(
            title = MLang.more_title,
            show = remember { mutableStateOf(true) },
            onDismissRequest = { showMoreBottomSheet = false }
        ) {
            val sortItems = listOf(MLang.sort_name, MLang.sort_package, MLang.sort_install, MLang.sort_update)
            val sortValues = AppInfoSort.entries.toTypedArray()


            // 显示和排序设置
            Card(
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                BasicComponent(
                    title = show_system,
                    summary = show_system_summary,
                    onClick = {
                        val newValue = !showSystemApps
                        showSystemApps = newValue
                        design.uiStore.accessControlSystemApp = newValue
                    },
                    rightActions = {
                        Switch(
                            checked = showSystemApps,
                            onCheckedChange = { checked ->
                                showSystemApps = checked
                                design.uiStore.accessControlSystemApp = checked
                            }
                        )
                    }
                )
                BasicComponent(
                    title = reverse_order,
                    summary = reverse_order_summary,
                    onClick = {
                        val newValue = !reverseOrder
                        reverseOrder = newValue
                        design.uiStore.accessControlReverse = newValue
                    },
                    rightActions = {
                        Switch(
                            checked = reverseOrder,
                            onCheckedChange = { checked ->
                                reverseOrder = checked
                                design.uiStore.accessControlReverse = checked
                            }
                        )
                    }
                )
            }

            // 排序方式
            Card {
                SuperDropdown(
                    title = sort_method,
                    summary = sort_method_summary,
                    items = sortItems,
                    selectedIndex = sortValues.indexOf(sortBy),
                    onSelectedIndexChange = { index ->
                        val newSort = sortValues[index]
                        sortBy = newSort
                        design.uiStore.accessControlSort = newSort
                    }
                )
                SuperDropdown(
                    title = more_title,
                    items = listOf(MLang.select_all, MLang.select_none, MLang.select_invert),
                    selectedIndex = 0,
                    onSelectedIndexChange = { index ->
                        when (index) {
                            0 -> design.selectAll()
                            1 -> design.selectNone()
                            2 -> design.invertSelection()
                        }
                        showMoreBottomSheet = false
                    }
                )
            }
        }
    }
}

// 辅助函数：将 Drawable 转换为 Bitmap
private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap
    }
    val bitmap = createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1))
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

@Composable
private fun AppRow(
    app: AppInfo,
    context: Context,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // 获取应用图标并转换为 ImageBitmap
    val appIconBitmap = remember(app.packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(app.packageName)
            drawable.toBitmap().asImageBitmap()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = { onCheckedChange(!checked) }
    ) {
        BasicComponent(
            title = app.label,
            summary = app.packageName,
            leftAction = {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(end = 16.dp)
                    )
                }
            },
            rightActions = {
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            }
        )
    }
}
