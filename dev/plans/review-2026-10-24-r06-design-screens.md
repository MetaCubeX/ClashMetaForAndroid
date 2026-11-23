---
title: "审查发现 r06 design-screens"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条，最严重的是应用集合编辑弹层不随目标重置，可能把上一份成员写进另一个集合或删错集合。"
---

# 审查发现 r06 design-screens

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖 design/src/main/java/universe。下面 5 条都来自这次读到的源码。

## r06-1
- 严重度：高
- 症状：应用集合编辑弹层已经打开时，如果 `openSetId` 又指向另一个集合，确定会把上一份名称和成员保存到新 id；删除确认也会删到新 id。
- 代码：
```128:138:design/src/main/java/universe/agentic/clash/AccessControlScreen.kt
    LaunchedEffect(openSetId, appSets) {
        val id = openSetId ?: return@LaunchedEffect
        val set = appSets.find { it.id == id } ?: return@LaunchedEffect
        editingSet = AppSetEditData(
            id = set.id,
            name = set.name,
            packages = set.packages,
            isBuiltin = set.isBuiltin,
            description = set.description,
        )
        onOpenSetConsumed()
    }
```
```503:508:design/src/main/java/universe/agentic/clash/AccessControlScreen.kt
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var query by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf(initial.packages) }
    var confirmDelete by remember { mutableStateOf(false) }
    val deleteId = initial.id
```
```739:759:design/src/main/java/universe/agentic/clash/AccessControlScreen.kt
                            onClick = {
                                val finalName = name.trim().ifEmpty { initial.name }
                                onSave(initial.id, finalName, description.trim(), selectedPackages.toList())
                            },
...
                        if (ok) onDelete(deleteId)
```
- 为什么是问题：`editingSet` 从集合 A 换成集合 B 时，弹层还在同一处组合，`remember` 继续拿着 A 的名称、说明和成员。保存和删除读的却是新的 `initial.id` / `deleteId`。名称留空时还会用 B 的名字配上 A 的成员。集合 B 的成员被覆盖，或确认删除删掉的是 B。
- 方向：编辑目标变化时丢掉弹层里的草稿，保存和删除都钉住打开时的那一个 id。

## r06-2
- 严重度：中
- 症状：长按拖动配置排序时，列表内容一旦更新，松手后界面仍显示拖动开始时的名称和激活状态；若这期间有配置加进来或被拿走，提交的顺序仍是旧的 UUID 列表。
- 代码：
```238:240:design/src/main/java/universe/agentic/clash/ProfilesScreen.kt
    LaunchedEffect(profiles) {
        if (dragId == null) rows = profiles
    }
```
```266:272:design/src/main/java/universe/agentic/clash/ProfilesScreen.kt
    val finish = rememberUpdatedState {
        val id = dragId
        dragId = null
        dragOffset = 0f
        if (id == null) return@rememberUpdatedState
        val uuids = rows.map { it.uuid }
        if (uuids != saved.value.map { it.uuid }) reorder.value(uuids)
    }
```
- 为什么是问题：拖动中 `profiles` 变化时，效果因为 `dragId != null` 直接返回，不会改 `rows`。松手只把 `dragId` 置空，这个效果的键没变，不会再跑。UUID 顺序如果没变，`onReorder` 也不调用，界面就停在旧的 `Profile` 上。顺序如果变了，回调拿到的是不含拖动期间新增项、可能仍含已删除项的那份 UUID。
- 方向：松手时用最新的配置列表合并这次拖出的顺序，再刷新行数据。

## r06-3
- 严重度：中
- 症状：在访问控制或集合成员列表右侧拖选，手指停在上下边缘再松开后，列表还会自己继续滚一段。
- 代码：
```78:93:design/src/main/java/universe/agentic/clash/DragSelect.kt
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                change.consume()
                val y = change.position.y
                val depth = when {
                    y < edgePx -> (edgePx - y) / edgePx
                    y > size.height - edgePx -> (y - (size.height - edgePx)) / edgePx
                    else -> 0f
                }
                if (depth > 0f) {
                    val direction = if (y < edgePx) -1f else 1f
                    val distance = direction * (8f + 28f * depth)
                    scope.launch { listState.scrollBy(distance) }
                }
```
- 为什么是问题：边缘上的每次指针事件都新开一个 `scrollBy`，这些协程没有在松手时取消。`scrollBy` 会排队执行，手指离开后积压的滚动仍会把列表推走。选中范围在循环结束时就定了，后面的滚动不再改选中，视口和刚圈定的行对不上。
- 方向：自动滚动只用一个可取消的任务，手指抬起就停。

## r06-4
- 严重度：中
- 症状：删除应用集合等确认框里，点标题、说明或卡片空白会直接取消，只有点到按钮上才算数。
- 代码：
```984:1015:design/src/main/java/universe/agentic/clash/IosControls.kt
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(colors.scrim)
                .clickable(interactionSource = scrim, indication = null, onClick = onScrim),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp + bars.calculateStartPadding(layoutDirection),
                    end = 16.dp + bars.calculateEndPadding(layoutDirection),
                    top = 16.dp,
                    bottom = bottom,
                ),
            contentAlignment = if (stickToBottom) Alignment.BottomCenter else Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * 16.dp.toPx()
                    }
                    .clip(ShellShapes.Container)
                    .background(colors.grouped)
                    .padding(12.dp),
                content = content,
            )
        }
    }
```
- 为什么是问题：确认框的遮罩铺满屏幕并可点击，`IosConfirmDialog` 把这次点击当成取消。卡片只有背景和裁剪，标题和说明没有自己的点击消费，点在卡片上会落到遮罩。同目录里的底部表单用空的 `clickable` 挡住了这种穿透，确认框没有。删除确认因此会在用户点到文案时关掉。
- 方向：确认卡片自身吃掉点击，只有卡片外面的遮罩才取消。

## r06-5
- 严重度：中
- 症状：首页当前节点可以打开快速切换，但当前分组为空时，点某个节点只会关掉列表，节点不会换。
- 代码：
```337:343:design/src/main/java/universe/agentic/clash/HomeScreen.kt
                    onClick = {
                        if (state.nodeSelectable && state.quickNodes.isNotEmpty()) {
                            switching = true
                        } else {
                            onProxy(state.currentGroup)
                        }
                    },
```
```385:389:design/src/main/java/universe/agentic/clash/HomeScreen.kt
            onSelect = { name ->
                val group = state.currentGroup
                switching = false
                if (group != null) onSelectNode(group, name)
            },
```
- 为什么是问题：弹出条件不看 `currentGroup`。卡片把空分组当成可以不展示的副标题，点选却在分组为空时丢掉节点名，并且仍然把 `switching` 设为 false。用户看到列表合上，出口节点保持原样。
- 方向：没有分组时不要打开这个点选列表，或者点选仍然把节点交出去。
