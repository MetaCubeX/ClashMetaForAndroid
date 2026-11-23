---
title: "审查方案 r07 design-shell"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 3、drop 1、weaken 0"
---

# 审查方案 r07 design-shell

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 design/src/main/java/nota。

hold 3、drop 1、weaken 0。

drop：r07-1。

## r07-2

- 判定：hold。严重度仍为中。
- 现在行号：`SwitchHaptic.kt` 第 6–25 行与发现稿一致。`switchHapticHandler` 在第 6 行，`performSwitchHaptic` 在第 10–12 行，`BindSwitchHaptic` 在第 15–25 行，`onDispose` 在第 22–24 行把槽写成 `null`。`ShellSwitch` 在第 52 行调用 `BindSwitchHaptic`。点击在第 148 行调用 `performSwitchHaptic`。拖动松手在第 135 行调用，且只在第 134 行 `target != currentChecked` 时进入。
- 问题类：多个开关共用一个触觉槽，任一开关离开组合都会把槽清空。
- 不变量：组合里还有 `ShellSwitch` 时，`performSwitchHaptic` 能调用到仍在组合内的那个开关注册的 `LocalHapticFeedback`。某个开关离开组合只卸掉自己的注册；后进入的开关不能把先进入的开关的卸除变成全局清空。屏上一个开关都没有时，槽里没有可调用的回调。
- 要动的符号或文件：`design/src/main/java/nota/ui/switch/SwitchHaptic.kt` 的 `switchHapticHandler`、`performSwitchHaptic`、`BindSwitchHaptic`。注册按调用 `BindSwitchHaptic` 的那一次组合分开保存，`onDispose` 只删除这一次。`ShellSwitch.kt` 第 52、135、148 行可以继续调用现有函数。
- 明确不改什么：触觉类型仍是 `HapticFeedbackType.LongPress`。拖动松手仍只在状态变化时触发（第 134–136 行）。点击在 `toggleOnClick` 为真时仍每次触发（第 147–149 行）。不改轨道、拇指和 `checkedAfterRelease`。
- 怎么本地验证：`design` 模块没有这条组合的测试，现有 junit 不覆盖。同一组合放两个 `ShellSwitch`，去掉其中一个后，再点留下的那个，或把它拖到另一状态后松手，触觉仍发生。两个都去掉之后不再发生。拖动松手但目标与当前相同，仍然不发生。

## r07-3

- 判定：hold。严重度仍为中。
- 现在行号：`ShellListRow.kt` 第 192–201 行、第 206–209 行、第 327–331 行与发现稿一致。`shellPressBackground` 使用第 162 行记住的 `interactionSource`。只传 `onLongClick` 时走 `pointerInput(enabled, onLongClick)`，里面是 `detectTapGestures(onLongPress = { onLongClick() })`。语义块只在 `isInteractive` 时写 `Role.Button`，没有长按动作。
- 问题类：只长按的列表行没有按下底色；长按计时绑在回调实例上；语义没有长按动作。
- 不变量：`onClick == null` 且 `onLongClick != null` 时，按下写进传给 `shellPressBackground` 的那个 `interactionSource`，按压进度离开 0。`pointerInput` 不因 `onLongClick` 换了实例而取消正在计时的长按。计时结束调用当时最新的回调；未到时长松手不调用。这一支的语义有长按动作。`enabled == false` 时没有按下，也没有回调。
- 要动的符号或文件：`ShellListRow.kt` 的 `listRowClickable`（第 305–333 行，只长按分支在第 327–331 行）和 `ShellListRowImpl` 的语义块（第 206–209 行）。按下使用第 162 行已有的 `interactionSource`。`pointerInput` 的 key 去掉 `onLongClick` 实例，协程里读取最新回调。
- 明确不改什么：`onClick` 与 `onLongClick` 同时存在时仍用 `combinedClickable`（第 312–319 行）。只有 `onClick` 时仍用 `clickable`（第 320–326 行）。不改 `shellPressBackground` 的时长、缓动和 `LocalShellPressTint`。不改行高、分割线和箭头。
- 怎么本地验证：仓库没有这条测试。只传 `onLongClick` 的 `ShellListRow`，按下后底色出现；按住期间用新的 lambda 重组该行，长按到期仍调用最新回调；提前松手不调用。同时传点击和长按时，短按与长按都还在。`enabled = false` 时按下没有底色，长按不回调。

## r07-4

- 判定：hold。严重度仍为低。
- 现在行号：`ListRowChevron` 的路径在 `ShellListRow.kt` 第 343–348 行，与发现稿一致，从 `w * 0.32f` 画到 `w * 0.68f`，不读布局方向。`PreferenceChevron` 在 `PreferenceChevron.kt` 第 27–44 行，与发现稿一致，`LayoutDirection.Rtl` 时尖角在左侧。
- 问题类：列表行箭头在从右到左时仍指向右。
- 不变量：`LayoutDirection.Ltr` 时 `ListRowChevron` 尖角在右侧。`LayoutDirection.Rtl` 时尖角在左侧，与 `PreferenceChevron` 同一侧。描边仍是圆头。`SingleLineArrow` 和双行 `showArrow` 都走这个箭头。
- 要动的符号或文件：`ShellListRow.kt` 的 `ListRowChevron`（第 336–361 行）。按 `LocalLayoutDirection` 翻转第 343–348 行的路径。
- 明确不改什么：`PreferenceChevron` 的路径、比例和描边。`ListRowChevron` 的尺寸、颜色，以及第 341 行的描边宽度。`Row` 在从右到左时把末尾控件放到左侧。列表行的点击和长按。
- 怎么本地验证：仓库没有这条测试。`Ltr` 下尖角在右侧，`Rtl` 下尖角在左侧。`PreferenceChevron` 在两种方向下与现在相同。`ListRowVariant.SingleLineArrow` 和双行 `showArrow` 都随方向翻转。
