---
title: "审查方案 r08 design-classic"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 3、drop 0、weaken 2"
---

# 审查方案 r08 design-classic

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 `design/src/main/java/com`、`design/src/main/res`、`design/src/test` 和 `design/build.gradle.kts`。发现稿 5 条的引用都在这些路径里重新打开过。`design/build.gradle.kts` 只提供 JUnit，没有被发现稿引用。`design/src/test` 里与本轮有关的只有 `NullableTextAdapterPortTest`。

hold 3、drop 0、weaken 2。

drop：无。

## r08-1

- 判定：hold。严重度仍为高。
- 现在行号：`NullableTextAdapter.Port.to` 仍在 `design/src/main/java/com/github/kr328/clash/design/preference/Value.kt` 第 15–20 行。`OverrideSettingsDesign.editNullable` 在第 497–517 行；发现稿写的 503–516 行与现在一致，写回在第 513–515 行。五个端口仍在第 176–189 行把 `configuration.httpPort`、`socksPort`、`redirectPort`、`tproxyPort`、`mixedPort` 交给 `portRow`。第 463 行的注释仍把 null 显示成不修改。`requestModelTextInput` 的这次调用没有传入 `validator`（第 506–512 行）。
- 问题类：无法按整数解析的确认被写成不修改，原端口覆盖丢失。
- 不变量：HTTP、SOCKS、Redirect、TProxy、Mixed 的编辑里，只有重置写出 null。只有空字符串写出 0。非空且 `toIntOrNull` 为 null 的文本不调用 `write`，打开对话框前的端口保持不变。`String.toIntOrNull` 对 `"abc"`、`"80 "`、`" 80"`、`"80\n"`、`"2147483648"` 得到 null，这些都算解析失败。
- 要动的符号或文件：`OverrideSettingsDesign.editNullable`。端口这一支在 `entered` 非 null、非空、且不能按 Int 解析时不 `write`。空字符串仍 `write(0)`，重置交回的 null 仍 `write(null)`。可以同时给这次 `requestModelTextInput` 传入只接受空串或整数的 `validator`，让确定键拒收坏文本。写回前的判断仍然要在，避免坏文本变成 null 后被写进去。
- 明确不改什么：`NullableTextAdapter.Port.to` 对 null、空串、合法整数的结果。`Port.from`。`NullableTextAdapter.String`。`stringRow` 和 `secretRow`。带空白或换行的文本不另作修剪后的成功解析。`"080"` 仍按整数 80 解析。负整数仍按现在的 `to` / `from` 走，小于等于 0 仍显示为禁用。
- 怎么本地验证：已有 `NullableTextAdapterPortTest.portToKeepsNullEmptyAndParsedAndDoesNotZeroGarbage` 保持通过：`null` 得到 null，`""` 得到 0，`"80"` 得到 80，`"abc"` 不是 0。应新增断言：`"abc"`、`"80 "`、`"80\n"`、`"2147483648"` 不作为一次端口写回；重置的 null 仍然写回；空字符串仍然写成 0。不得把垃圾收成 0，否则现有测试失败。`editNullable` 目前没有测试，断言放在写回决策上。

## r08-2

- 判定：hold。严重度仍为高。
- 现在行号：`OverrideSettingsDesign.editNullable` 第 503–517 行与发现稿一致。`editList` 第 525–530 行与发现稿一致，函数到第 531 行。`MetaFeatureSettingsDesign.editList` 第 351–356 行与发现稿一致，函数从第 346 行到第 357 行。`WebdavSettingsDesign.edit` 第 201–214 行与发现稿一致。`Input.kt` 第 51 行的取消按钮为空，第 52–54 行在未完成时把初值交回，闭合括号在第 55 行。`Design` 的作用域在 `Design.kt` 第 18–19 行是 `Dispatchers.Unconfined`，这三处 `launch` 显式使用 `Dispatchers.Main`，`withContext(Dispatchers.IO)` 会让出主线程。名单取消在 `OverrideSettingsDesign` 第 567–570 行和 `MetaFeatureSettingsDesign` 第 379–382 行把 `EditableListOverlayResult.Cancel` 收成打开时的列表，调用方随后仍 `write`。
- 问题类：同一字段的编辑在对话框出现前可以再进一层，后结束的那层把打开时的快照写回去。
- 不变量：`OverrideSettingsDesign`、`MetaFeatureSettingsDesign`、`WebdavSettingsDesign` 上，一次编辑从进入到写回结束前不再打开第二层。取消不调用 `write`。文本层交回的值等于本次打开时的初值时不调用 `write`。重置仍写 null。名单层只有 `Apply` 和 `Reset` 写回。
- 要动的符号或文件：`OverrideSettingsDesign.editNullable`、`editList`、`editMap`（第 534–545 行，发现稿没有点名；`requestTextMap` 在第 593–596 行对取消同样交回初值，hosts 等走 `editMap`）。`MetaFeatureSettingsDesign.editList`。`WebdavSettingsDesign.edit`。占住编辑的标记要在第一次 `withContext(Dispatchers.IO)` 之前置上，因为 `launch` 会立刻返回。`Input.kt` 可以继续在取消时交回初值，由这些调用方跳过 `write`。
- 明确不改什么：`Input.kt` 里重置交回 null、取消交回初值。`requestTextList` 和 `requestTextMap` 里 `Apply` 与 `Reset` 的取值。`EditableText.kt`、`EditableTextList.kt`、`EditableTextMap.kt` 的偏好构建器，`com` 包里没有调用方。`Design` 的协程作用域。`choice` 那类直接写枚举的点击。
- 怎么本地验证：没有现成测试。应新增断言：第一次 `editNullable`、`editList` 或 `edit` 尚未结束时，第二次入口直接返回，不再读初值。名单结果为 `Cancel` 时 `write` 不被调用。文本层交回的字符串等于打开时初值时 `write` 不被调用。交回 null 且初值不是 null 时仍然 `write(null)`。先保存的新值不会被后结束的那层用旧初值盖掉。

## r08-3

- 判定：hold。严重度仍为高。
- 现在行号：`WebdavSettingsDesign.edit` 第 201–214 行与发现稿一致，调用在第 203–208 行，没有传 `preserveUnedited`。`Input.kt` 第 30 行默认 `preserveUnedited = false`，确定键第 40–49 行在该标志为 false 时 `resume` 输入框里的字符串。`NullableTextAdapter.String.to` 在 `Value.kt` 第 28–30 行原样返回。地址、局域网地址、用户名、远程目录分别在第 118–131 行走 `edit`。
- 问题类：未设置（null）时不改内容就确定，会把 null 存成空字符串。
- 不变量：这四个字段打开时为 null，最终文本仍是空串时，确定之后存储仍是 null。打开时的非空字符串在未改动时保持原字符串。用户把非空文本改成空串时可以写成 `""`。重置写成 null。取消保持打开时的值。
- 要动的符号或文件：`WebdavSettingsDesign.edit` 这次 `requestModelTextInput` 传入 `preserveUnedited = true`。写回仍用 `NullableTextAdapter.String.to`。`initial == null` 时，`Input.kt` 第 42 行把未改动定义成最终文本等于空串，确定会交回 null。若同时做 r08-2，交回值等于打开时初值就跳过 `write`，存储同样仍是 null。两条都不得把这次确定写成 `""`。
- 明确不改什么：`onPassword`（第 134–147 行）。`NullableTextAdapter.String.to` 对 null 和 `""` 的原样返回。覆盖设置里已经传入 `preserveUnedited = true` 的 `editNullable`。`Input.kt` 里「最终文本对 `initial ?: ""`」的未改动比较。不增加把 null 和空串分成两种手势的新控件；null 经过 `setText` 后，确定键看到的就是空串。
- 怎么本地验证：没有现成测试。应新增断言：`initial == null` 且最终文本仍是空串时，确定交回 null，`String.to` 之后仍是 null。`initial == "https://example"` 且未改动时交回该字符串。把该字符串改成空串时交回 `""`。重置交回 null。

## r08-4

- 判定：weaken。原严重度低，降为提示。
- 现在行号：`requestEditableListOverlay` 里重置在 `design/src/main/java/com/github/kr328/clash/design/preference/Overlay.kt` 第 41–45 行，取消在第 47–49 行，确定在第 51–55 行，与发现稿一致。dismiss 的完成判断在第 59–63 行；发现稿写到第 62 行，闭合括号在第 63 行。`dialog_preference_list.xml` 第 54–88 行的三个按钮没有 `android:onClick`。`FullScreenDialog` 在 `Dialogs.kt` 第 76–78 行，是 `Dialog` 子类。覆盖设置的名单和映射、Meta 的嗅探名单仍调用这个全屏层。
- 仍成立：确定和重置不看 `ctx.isCompleted` 就 `resume`。取消只关对话框。dismiss 在未完成时才交回 `Cancel`。监听若再执行一次，后一次 `resume` 会落在已经完成的续体上。
- 反证：同一次点击在主线程先 `resume`，紧接着 `dialog.dismiss()`。dismiss 监听看到已经完成，不会再 `resume`。布局没有第二套点击回调。这次点击返回之前，代码里没有另一条 `resume`。发现稿说的「关掉前连点必然再 resume 并崩溃」，在这段调用顺序里没有第二条必然执行的 `resume`。
- 问题类：全屏名单的确定和重置缺少与 dismiss 相同的完成判断。
- 不变量：确定最多 resume 一次 `Apply`。重置最多 resume 一次 `Reset`。已完成后再进入这两个监听，不调用 `resume`。取消仍只关对话框；对话框在协程尚未完成时关掉，仍交回 `Cancel`。
- 要动的符号或文件：`Overlay.kt` 的 `binding.resetView` 与 `binding.okView`。完成判断与第 59–63 行同一条件。
- 明确不改什么：取消按钮。dismiss 交回 `Cancel` 的条件。`EditableListOverlayResult`。`OverrideSettingsDesign.requestMapEntry` 第 607–632 行已有的 `isCompleted` 判断。`Input.kt`。
- 怎么本地验证：没有现成测试。应新增断言：第一次确定只得到 `Apply`，紧接着的 dismiss 不再得到 `Cancel`。第一次重置只得到 `Reset`。续体已完成后再进入这两个监听，不调用 `resume`。

## r08-5

- 判定：weaken。原严重度中，降为低。
- 现在行号：`onAutoSync` 在 `WebdavSettingsDesign.kt` 第 149–168 行。发现稿的引用范围是第 149–186 行；`confirmThenSend` 的函数结束括号现在在第 187 行，函数体与发现稿一致。`busy` 初始为 false，在第 49 行，不是界面状态。`trySend(Request.Sync)` 和 `busy = true` 在第 164–166 行，位于偏好写入之后。`confirmThenSend` 在第 171–172 行看到 `busy` 为 false 就占住并进入确认。第 183–184 行在 `sent` 仍为 false 时把 `busy` 设回 false。`onActionFinished` 在第 89–91 行把 `busy` 设为 false。`send` 在第 189–193 行同样先看 `busy`。
- 仍成立：打开自动同步后，在 `store.autoSync` 写入完成前，`busy` 仍是 false。这段时间上传、下载或立即同步的确认可以进入。确认没有发出请求时，`finally` 无条件清除 `busy`，不看自动同步是否已经 `trySend`。
- 反证：`Request.Sync` 要等 `withContext(Dispatchers.IO)` 回到主线程才发出。确认框还在时同步是否已经发出，取决于 `iosConfirm` 是否把主线程让出去。`iosConfirm` 不在 `com` 包，这次没有打开它的函数体。若它占住主线程直到返回，取消会先把 `busy` 清掉，随后自动同步才 `trySend` 并把 `busy` 设回 true。那样，发现稿说的「进行中的同步不再挡住下一次拉取或上传」不会由这次取消造成。
- 问题类：自动同步占住页面的时机晚于偏好写入，未发出的确认又会清掉整个页面的忙碌标记。
- 不变量：这次打开自动同步会发出 `Request.Sync` 时，在偏好写入挂起之前 `busy` 已经为 true，`confirmThenSend` 和 `send` 直接返回。已经 `trySend` 成功的同步，不因另一个确认得到非确认结果而把 `busy` 设回 false；这次同步仍由 `onActionFinished` 清标记。同一次打开若在发出 `Sync` 前被更新的 `autoSyncGeneration` 取代，并且确认框也没有发出请求，`busy` 回到 false。
- 要动的符号或文件：`WebdavSettingsDesign.onAutoSync`、`confirmThenSend`，以及第 49 行的 `busy`。打开且即将同步时，在 `launch` 之后的第一次挂起之前置 `busy`。`confirmThenSend` 的 `finally` 只清除这次确认自己占上的、且自己没有发出的标记。
- 明确不改什么：第 151–154 行在已忙碌时把自动同步拨回 false 并返回。`autoSyncGeneration` 与 `autoSyncMutex` 对过期写入的丢弃。关闭自动同步不发送 `Sync`，也不为此把 `busy` 设为 true。`onActionFinished`。`withProgress`。`send` 在 `busy` 时直接返回的判断。
- 怎么本地验证：没有现成测试。应新增断言：`onAutoSync(true)` 在 `store.autoSync` 写完之前 `busy` 已是 true，此时 `confirmThenSend` 不进入确认。自动同步已经 `trySend(Request.Sync)` 成功后，一次未确认的 `confirmThenSend` 结束时 `busy` 仍为 true。`onAutoSync(false)` 不发送 `Sync`。已有 `busy` 时再打开自动同步不发送第二次 `Sync`，页面上的开关保持为关。写入完成前 generation 被取代且没有请求发出时，`busy` 为 false。
