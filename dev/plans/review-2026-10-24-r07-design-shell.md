---
title: "审查发现 r07 design-shell"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "4 条，最严重为中：可点击设置行里的开关只吃轨道那一块，轨道外的点击会变成整行点击。"
---

# 审查发现 r07 design-shell

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖 design/src/main/java/nota。下面 4 条都来自这次读到的源码。

## r07-1
- 严重度：中
- 症状：开关放在带点击的设置行末尾时，手指点在轨道四周、但仍在这一行里，开关不切换，整行的 `onClick` 会先发生。
- 代码：
```160:170:design/src/main/java/nota/ui/switch/ShellSwitch.kt
    Box(modifier.size(width = spec.trackWidth.dp, height = spec.trackHeight.dp)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .requiredSize(width = touchTargetWidth.dp, height = touchTargetHeight.dp)
                .semantics(mergeDescendants = false) {
                    role = Role.Switch
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                    if (!enabled) disabled()
                }
                .then(gestureModifier),
```
```102:107:design/src/main/java/nota/ui/settings/ShellPreferenceItem.kt
                    if (onClick != null && enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
```
```154:161:design/src/main/java/nota/ui/settings/ShellPreferenceItem.kt
            when {
                trailing != null -> {
                    Spacer(modifier = Modifier.width(widgetGap.dp))
                    Box(
                        modifier = Modifier.defaultMinSize(minWidth = spec.widgetMinWidth.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        trailing()
```
- 为什么是问题：外层 `Box` 的测量尺寸被固定成轨道（默认和 iOS 都是 40×22）。更大的热区只加在子 `Box` 的 `requiredSize` 上，上层看到的仍是轨道。`switchTouchTargetWidth` 写明热区可以大于轨道，而且不能把宿主行撑高；`shellIosSwitchSpec` 把 `minTouchTarget` 设成 44。`ShellPreferenceItem` 又把 `trailing()` 放进同一行的 `clickable`。点在轨道外、行内时，这一行已经是界内命中，子节点溢出的那一圈不会先交给开关。轨道上下各约 11dp 的点击变成整行点击，开关的 `onCheckedChange` 不跑。
- 方向：开关的可点范围要落在它自己报给上层的命中边界上，盖住轨道外仍属于开关的那一圈。

## r07-2
- 严重度：中
- 症状：同一屏上有多个开关时，任意一个开关离开组合之后，还留在屏上的开关再拨动就没有触觉。
- 代码：
```6:25:design/src/main/java/nota/ui/switch/SwitchHaptic.kt
private var switchHapticHandler: (() -> Unit)? = null

internal fun performSwitchHaptic() {
    switchHapticHandler?.invoke()
}

@Composable
internal fun BindSwitchHaptic() {
    val hapticFeedback = LocalHapticFeedback.current
    DisposableEffect(hapticFeedback) {
        switchHapticHandler = {
            // Compose 1.7 only has LongPress and TextHandleMove.
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        onDispose {
            switchHapticHandler = null
        }
    }
}
```
- 为什么是问题：每个 `ShellSwitch` 都会调用 `BindSwitchHaptic`，点击和拖动松手都走 `performSwitchHaptic`（`ShellSwitch.kt` 第 135、148 行）。全局只有一个槽，后组合的开关覆盖前者。某个开关离开组合时，`onDispose` 无条件把槽写成 `null`，不会恢复其它开关已经注册的回调。列表里滑走一个开关之后，还可见的开关再切换就没有触觉，直到它们自己因为别的原因重组并重新绑定。
- 方向：触觉回调跟具体那一个开关走，离开组合时只卸掉自己的。

## r07-3
- 严重度：中
- 症状：列表行只传长按、不传点击时，按下没有按压底色；按住期间这行一旦重组，长按计时被取消，松手没有回调。
- 代码：
```192:201:design/src/main/java/nota/ui/list/ShellListRow.kt
                    .shellPressBackground(
                        enabled = isInteractive && enabled,
                        interactionSource = interactionSource,
                    )
                    .listRowClickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
```
```327:331:design/src/main/java/nota/ui/list/ShellListRow.kt
        onLongClick != null ->
            pointerInput(enabled, onLongClick) {
                if (!enabled) return@pointerInput
                detectTapGestures(onLongPress = { onLongClick() })
            }
```
- 为什么是问题：`shellPressBackground` 只看 `interactionSource` 里的按下。有 `onClick` 时，`clickable` / `combinedClickable` 会把按下写进同一个 source。只剩 `onLongClick` 时改走 `pointerInput`，`detectTapGestures` 不碰这个 source，按压进度停在 0，底色不出现。`pointerInput` 的 key 含有 `onLongClick` 实例，实例一变手势协程就重启，正在计时的长按被取消。这一支的 `semantics` 只在有交互时标了 `Role.Button`（第 206–209 行），没有长按动作。
- 方向：只长按的行仍把按下写进同一个 `interactionSource`，并且不要把回调实例放进 `pointerInput` 的 key。

## r07-4
- 严重度：低
- 症状：布局方向从右到左时，列表行末尾的箭头仍指向右；设置行的箭头会转向左。
- 代码：
```343:348:design/src/main/java/nota/ui/list/ShellListRow.kt
            Path().apply {
                val w = this@Canvas.size.width
                val h = this@Canvas.size.height
                moveTo(w * 0.32f, h * 0.22f)
                lineTo(w * 0.68f, h * 0.5f)
                lineTo(w * 0.32f, h * 0.78f)
```
```27:44:design/src/main/java/nota/ui/settings/PreferenceChevron.kt
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.size(chevronSize)) {
        val width = size.width
        val height = size.height
        val strokeWidth = (width * 0.1f).coerceAtLeast(1.25f)
        val path =
            Path().apply {
                val insetX = width * 0.3f
                val insetY = height * 0.22f
                if (rtl) {
                    moveTo(width - insetX, insetY)
                    lineTo(insetX, height / 2f)
                    lineTo(width - insetX, height - insetY)
                } else {
                    moveTo(insetX, insetY)
                    lineTo(width - insetX, height / 2f)
                    lineTo(insetX, height - insetY)
                }
```
- 为什么是问题：`Row` 在从右到左时会把末尾控件放到左侧，前进方向是向左。`ListRowChevron` 的路径固定从左侧 0.32 画到右侧 0.68，箭头仍指向右，和阅读方向相反。同模块的 `PreferenceChevron` 会按 `LayoutDirection.Rtl` 把尖角画到左侧。
- 方向：列表行箭头按当前布局方向翻转。
