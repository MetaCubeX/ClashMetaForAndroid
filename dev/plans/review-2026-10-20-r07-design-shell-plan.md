---
title: "审查方案 r07 design-shell"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 4、drop 0、weaken 0"
---

# 审查方案 r07 design-shell

四条都 hold。引用行与活码一致，没有 drop 或 weaken。

## r07-1

- 问题类：覆盖项在「不修改」时点确定，或端口文本无法解析时，会写成禁用或默认。
- 不变量：`null` 表示不修改。端口空串或小于等于 0 表示已禁用，字符串空串表示该项的空值文案，密钥空串表示默认。打开时是 `null` 且用户没有改文本就点确定，写回 `null`。用户把已有文本删空后再确定，端口写 0，字符串和密钥写空串。重置仍写 `null`。取消仍保持打开时的值。非空且不能解析成整数的端口不要写成 0。
- 要动的符号或文件：`OverrideSettingsDesign.editNullable`（494–514 行）、`portRow`（404–409 行）。`dialog/Input.kt` 的 `requestModelTextInput` 确定分支（39–45 行）只增加覆盖项这条路需要的「用户是否改过文本」。`NullableTextAdapter.Port.to`（15–19 行）不再把解析失败收成 0。空串仍是 0，`null` 仍是 `null`。
- 明确不改什么：`portSummary`（461–467 行）、`stringSummary`（470–477 行）、`secretSummary`（480–484 行）和 `Port.from`。`NullableTextAdapter.String.to` 仍原样返回文本。取消仍由 dismiss 恢复 `initial`（48–51 行），重置仍恢复 `null`（53–57 行）。`FilesDesign.requestFileName`、`PropertiesDesign`、`MetaFeatureSettingsDesign.requestTextList`、`preference/EditableTextList.requestEditTextList` 这些非空 `initial` 的确定结果不变。不要改 WebDAV `edit` 的字段集合；未传新参数的调用方仍按现在的确定结果写回。
- 怎么本地验证：仓库没有覆盖这条路径的测试，`AppSetMembershipTest` 无关。应新增断言：`Port.to(null)` 为 `null`，`Port.to("")` 为 0，`Port.to("80")` 为 80，`Port.to("abc")` 不是 0。`editNullable` 在打开值为 `null`、文本未改就确定时，`write` 收到 `null`；把已有端口删空后确定，`write` 收到 0；字符串和密钥同样删空后收到空串；重置收到 `null`；取消不调用改变原值的 `write`。

## r07-2

- 问题类：hosts 或 nameserver policy 的添加框点取消或空确定后，添加协程不结束，应用和重置写不回。
- 不变量：添加框每次关闭都要结束 `requestMapEntry`。取消、返回和点外面关闭得到 `null`，不插入行。键或值 trim 后为空时确定不关闭对话框，也不结束协程。两者都非空才恢复这对键值并关闭。`requestEditableListOverlay` 仍等添加子协程结束后才把应用、重置或取消交回 `requestTextMap`，随后 `editMap` 才能 `write`。
- 要动的符号或文件：`OverrideSettingsDesign.requestMapEntry`（596–618 行）。取消按钮要在未完成时 `resume(null)`。空白确定不能依赖 `setPositiveButton` 的自动 `dismiss`；在 `show` 之后换掉确定按钮的监听，校验失败就留在框里。增加 dismiss 监听，仅在尚未完成时 `resume(null)`。与现有 `setOnCancelListener`（612–616 行）共用 `isCompleted`，避免返回键先 cancel 再 dismiss 时第二次 `resume`。
- 明确不改什么：`requestEditableListOverlay` 的 `Apply` / `Reset` / `Cancel`，以及 `newView` 用 `coroutineScope` 子协程启动 `addNewItem`（`preference/Overlay.kt` 35–38 行、51–63 行）。`requestTextMap` 的三种结果（589–592 行）和 `editMap` 的 `write` 时机（530–541 行）。`requestTextList` 与 `MetaFeatureSettingsDesign` 的纯文本新增。返回键取消这次添加、得到 `null` 的语义。
- 怎么本地验证：仓库没有这条测试。应新增断言：取消、返回和 dismiss 都使 `requestMapEntry` 返回 `null` 且协程结束；空键或空值确定后对话框仍在、协程仍挂起；合法确定返回 trim 后的键值。先改列表再打开添加框并取消，然后应用，`requestTextMap` 返回包含这些增删的映射，`editMap` 会 `write`；同一路径点重置，`write` 收到 `null`。空白确定之后外层应用不得永远等不到结果。

## r07-3

- 问题类：hosts 或 nameserver policy 同键两行在应用时被收成一行，先填的值丢掉。
- 不变量：列表里同一个键最多一行，应用后的 `Map` 与仍显示的行一一对应，条数不减少。再次加入已有键时当场换成新值并刷新那一行。不同键仍追加。
- 要动的符号或文件：`EditableTextMapAdapter.addElement`（18–24 行）。用 `keyAdapter.to` 后的键查找已有下标；找到则替换该 `Pair` 并 `notifyItemChanged`，找不到再按现在的尾部插入。
- 明确不改什么：`requestTextMap` 里 `toMap()`（591 行）仍是列表到 `Map` 的转换。删除按钮（38–44 行）。`configuration.hosts` 与 `configuration.dns.nameserverPolicy` 仍是 `Map<String, String>`。`TextAdapter.String`。
- 怎么本地验证：仓库没有这条测试。应新增断言：对同一键调用两次 `addElement` 后 `values.size` 为 1，值是第二次的，且没有新增一行；两个不同键为两行。`requestTextMap` 在应用时结果的 `size` 等于列表行数，先填的另一个键仍在。

## r07-4

- 问题类：自动同步打开后立刻关闭时，界面是关，存储仍可能被写成开，并且仍会发出 `Sync`。
- 不变量：界面上的 `autoSync` 与最后一次写入的 `store.autoSync` 相同。写入按开关代际串行，只有最新一次写进存储。最新一次是关闭，且 `Request.Sync` 还没发出，就不要发。已经发出的 `Sync` 不撤回。`trySend` 成功后 `busy` 仍为 true，直到现有的 `onActionFinished`。被更新代际废掉的打开不要留下 `busy`，也不要发 `Sync`。
- 要动的符号或文件：`WebdavSettingsDesign.onAutoSync`（144–161 行）。去掉只在 `enabled && busy` 时直接返回的不对称判断。用单调代际加互斥锁：锁内若代际已过期则什么都不写；否则写 `store.autoSync`。写完再看一次代际，仍是最新且为打开才 `trySend(Request.Sync)`。`requests` 是 `Channel.UNLIMITED`（`Design.kt` 23 行），不要靠发送失败来取消这次同步。
- 明确不改什么：`onActionFinished`（84–86 行）只清 `busy`。`confirmThenSend`、`send`、密码和其余 `edit`。不改 `WebdavSettingsStore`。不把 `busy` 放进 `WebdavSettingsUiState`（55–63 行）。不改 `ios/` 下的界面。
- 怎么本地验证：仓库没有这条测试。应新增断言：先开后关，且关闭发生在发送前，最终 `store.autoSync` 为 false，`Request.Sync` 发送次数为 0，`busy` 为 false。只打开一次，存储为 true 且只发送一次。开、关、开以最后一次打开为准，只发送一次。先打开并已发送后再关闭，存储为 false，已发送的那一次仍算已发送，不再多发一次。
