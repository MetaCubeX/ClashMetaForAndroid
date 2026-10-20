---
title: "审查方案 r06 design-ui"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 4、drop 0、weaken 1"
---

# 审查方案 r06 design-ui

核对范围是 `design/.../ios` 活码。r06-1、r06-2、r06-3、r06-5 维持原严重度。r06-4 降为低。没有 drop。

## r06-1（hold，中）

- 问题类：配置行在已有进度条时，用量文案被 `download < 2` 单独清掉。
- 不变量：`usageFraction(profile) != null` 时 `trafficLine` 必须返回含已用和总量的文案。`download` 为 0 或 1 不能单独代表没有用量。已用为 0 但进度条仍出现时，文案写 0 与总量。
- 要动的符号或文件：`ProfilesScreen.kt` 的 `trafficLine`（245–250 行）。进度条仍由 173–181 行的 `fraction != null` 决定，门槛仍是 `usageFraction`（272–278 行）。
- 明确不改什么：不改 `usageFraction` 的 1000 刻度算法，不改到期行和「更新于」行，不改 `IosFractionBar`。
- 怎么本地验证：设计模块没有对应用例。新增断言意图：`download` 为 0、`upload` 大于 0、`total` 不小于 1000 时，分数非空且文案同时含已用与总量；`download`、`upload`、`total` 都小于 2 时，分数与文案都为空。

## r06-2（hold，中）

- 问题类：非 Inline 供应商在 `updatedAt <= 0` 时仍把 `now - updatedAt` 格式化成右侧时间。
- 不变量：`updatedAt <= 0` 的供应商行右侧时间为空，与文件行 `lastModified <= 0`（`FilesScreen.kt` 81–85 行）和配置行 `updatedAt <= 0`（`ProfilesScreen.kt` 227–230 行）相同。
- 要动的符号或文件：`ProvidersScreen.kt` 的 `ProviderLine`。实际时间表达式在 131 行：`if (inline) null else (now - row.updatedAt).elapsedIntervalString(context)`。
- 明确不改什么：不改 Inline 隐藏更新按钮的条件，不改 `updating` 时的 `IosProgressRow`，不改 `elapsedIntervalString` 本身。
- 怎么本地验证：设计模块没有对应用例。新增断言意图：非 Inline 且 `updatedAt <= 0` 时 value 为 null，且不拿 `now - updatedAt` 去格式化；`updatedAt > 0` 时仍格式化间隔；Inline 的 value 仍为 null。

## r06-3（hold，中）

- 问题类：`TunnelState.Mode.Script` 时三枚模式按钮的选中下标为 -1，运行中的代理行也没有脚本短标签。
- 不变量：当前模式为 Script 时，首页模式区能看出是脚本模式；隧道在跑时，代理行的值是脚本短标签。`selected == -1` 只表示当前不是 Rule、Global、Direct。
- 要动的符号或文件：`HomeScreen.kt` 的 `modeSummary`（73–78 行，Script 现为 null）、代理行 `value`（86–88 行，找不到短标签就用 `R.string.mode`）、以及脚本模式的短标签和摘要字符串。`IosModePicker` 在 `IosComponents.kt` 403–404 行用 `index == selected` 上色，这段比较保持不变。
- 明确不改什么：不把 Script 放进三枚可点按钮，不让这三枚按钮调用 `onMode(Script)`，不改 Rule、Global、Direct 的短标签、摘要和 `onSelect`（161–167 行）。
- 怎么本地验证：设计模块没有对应用例。新增断言意图：`state.mode == Script` 且 `running` 时，模式摘要非空并标明脚本，代理行值是脚本短标签而不是 `R.string.mode`；`selected` 仍不是 0、1、2。Rule、Global、Direct 的选中下标和摘要保持原样。

## r06-4（weaken，低）

- 问题类：分组选择器打开时，导航栏测速仍会调用 `onUrlTest`，延迟测试进度只出现在选择器关闭后的列表里。
- 不变量：`picking == true` 时不调用 `onUrlTest`。测速目标保持进入选择器前已经选中的那一组。选择器关闭后，进度行仍在原列表分支。
- 要动的符号或文件：`ProxyScreen.kt` 98–104 行的测速 `IosNavIcon` 条件。加上 `!picking`，或在 `onClick` 里于 `picking` 时直接返回。
- 明确不改什么：不改 `GroupPicker` 的 `selected = groupIndex == current`（245 行），不把测速改派到尚未点中的行，不把 `IosProgressRow`（153–155 行）搬进 `picking` 分支。
- 怎么本地验证：设计模块没有对应用例。新增断言意图：`picking` 为真时测速控件不触发 `onUrlTest`；`picking` 为假且未在测速时，点击仍调用 `onUrlTest` 一次。
- 反证：选择器列表把 `groupIndex == current` 标成选中，`onUrlTest` 不接收另一个下标，`onPickGroup` 只在点中某一行之后才发生。测速仍打在进入选择器前的已选组上，不是打在另一条未选中的组上。进度被藏起来，是因为 115–127 行的 `picking` 分支没有进度行。这是选择器与测速叠在一起的边界，严重度改为低。

## r06-5（hold，中）

- 问题类：底部弹层进入关闭后，再次点击不会替换已记下的动作，删除会在约 250ms 动画结束后仍执行。
- 不变量：`SheetMotionMs`（250）走完之后，只执行最后一次 `requestClose` 传入的 `after`。取消的空 lambda 必须盖过尚未执行的删除。动画结束前不调用 `followUp`。
- 要动的符号或文件：`IosControls.kt` 的 `IosSheetHost.requestClose`（511–517 行）。现在 `closingState` 已为真时直接丢掉新的 `after`。应改为始终写入 `followUp`，并且只在首次把 `closingState` 设为真。动画结束处仍是 527–529 行的 `followUp` 然后 `onDismiss`。取消按钮（356–360 行）继续传空 lambda。
- 明确不改什么：不改 `SheetMotionMs`，不在点击当帧执行 `IosAction.onClick`，不改 `ProfilesScreen.kt` 206 行的删除动作本身，不改 `IosSheetButton` 的按下样式，不改 `iosConfirm`。
- 怎么本地验证：设计模块没有对应用例。新增断言意图：先 `requestClose(删除)` 再 `requestClose({})`，动画结束后删除 lambda 的调用次数为 0，`onDismiss` 调用次数为 1；只点一次删除时，动画结束后删除恰好调用一次。
