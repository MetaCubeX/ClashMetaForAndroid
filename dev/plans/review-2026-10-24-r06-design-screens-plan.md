---
title: "审查方案 r06 design-screens"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 4、drop 1、weaken 0"
---

# 审查方案 r06 design-screens

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 `design/src/main/java/universe`。发现稿 5 条的引用文件都重新打开过。

hold 4、drop 1、weaken 0。

drop：r06-3。

## r06-1

- 判定：hold。严重度仍为高。
- 现在行号：`AccessControlScreen` 里 `LaunchedEffect(openSetId, appSets)` 在 `AccessControlScreen.kt` 第 128–139 行，与发现稿一致。`AppSetEditModal` 在第 389–404 行调用，没有 `key`。`name`、`description`、`query`、`selectedPackages`、`confirmDelete` 在第 503–507 行用无键 `remember` 初始化；`deleteId` 在第 508 行每次组合都读当前的 `initial.id`。确定在第 739–741 行把 `initial.id` 和记住的成员一起交给 `onSave`；名称留空时用 `initial.name`。删除确认在第 751–759 行把 `deleteId` 交给 `onDelete`。
- 问题类：弹层还在同一次组合里时，编辑目标的 id 换了，草稿仍是上一份。
- 不变量：同一次装载的 id、名称、说明和成员一起生效。`editingSet` 换成另一个集合时，丢掉上一份草稿，输入框、说明和勾选改为新集合的数据，删除确认也关掉。保存和删除使用这次装载的 id。名称留空时，回退名是这次装载的名称。不得用集合 A 的成员调用集合 B 的 id，也不得在确认框仍显示 A 的文案时删掉 B。
- 要动的符号或文件：`AccessControlScreen.kt` 的 `editingSet`、第 128 行的 `LaunchedEffect`、`AppSetEditModal` 及其第 503–508 行的状态。可以在第 389 行按装载 id 给弹层加 `key`，也可以在 `initial.id` 变化时把上述状态连同 `confirmDelete` 一起重置。第 128 行仍负责在 `openSetId` 有值时写入 `editingSet`。列表在 `Dialog` 后面，点另一行的编辑够不着；会换目标的是这条 effect。
- 明确不改什么：`onSaveAppSet` 和 `onDeleteAppSet` 的签名；集合行的勾选、拖选、导入导出；`IosSheetFrame` 的遮罩关闭；新建集合（`id == null`）的入口。内置集合本来就没有删除按钮，这一点保持不动。
- 怎么本地验证：打开集合 A 的编辑弹层，改名称并改勾选，不关闭。再让 `openSetId` 指向集合 B。输入框、说明和勾选应变成 B，A 的草稿消失。点确定，`onSaveAppSet` 的 id 和成员都属于 B。名称清空后再确定，回退名是 B 的名称。另一次：打开 A，点删除直到确认框出现，此时把目标换成 B。确认之后 `onDeleteAppSet` 的 id 必须是这次装载对应的那一个，不能是被换上来的另一个 id。`design` 的 `testImplementation` 只有 junit，现有 `:design:test` 覆盖不到这个弹层。

## r06-2

- 判定：hold。严重度仍为中。
- 现在行号：`ProfileList` 在 `ProfilesScreen.kt` 第 216 行。`rows` 在第 231 行，`dragId` 在第 232 行，`saved` 在第 237 行。`LaunchedEffect(profiles)` 在第 238–240 行，与发现稿一致。`finish` 在第 266–273 行；发现稿写到第 272 行，闭合括号在第 273 行，行为相同。列表画的是 `rows`，第 306 行。`ProfileRow` 用该对象的 `profile.active`（第 502 行）和 `profile.name`（第 533 行）。
- 问题类：拖动期间把行冻住之后，松手没有用最新配置合并顺序，也没有刷新行上的名称和激活状态。
- 不变量：松手时以当时的 `profiles` 为准。结果里的 UUID 集合等于这份列表的 UUID 集合。两边都还有的项，相对顺序与松手时 `rows` 的相对顺序一致。已经不在 `profiles` 里的 UUID 不进入 `onReorder`。界面上的名称和 `active` 来自松手时的 `profiles`，不停留在拖动开始时的对象。结果序列与当时的 `profiles` 序列相同，则不调用 `onReorder`，但行数据仍然刷新。拖动过程中 `dragId != null` 时可以继续不改 `rows`，避免和手指里的换位打架。
- 要动的符号或文件：`ProfilesScreen.kt` 的 `ProfileList`、第 238 行的 `LaunchedEffect`、第 266 行的 `finish`。`finish` 在把 `dragId` 置空之后，用 `saved.value` 和当前 `rows` 做出上面的序列，再把 `rows` 换成这份 `profiles` 里的对象。新增项必须出现；它夹在哪两项之间没有单独的产品规定，只要集合、共有项的相对顺序和对象来源满足不变量。
- 明确不改什么：第 241–265 行 `moveBy` 的换位，第 274–293 行拖动贴边滚动，`ProfileRow` 的点击、更新和菜单。少于 2 条时不挂长按拖动，保持第 333–335 行。
- 怎么本地验证：至少两条配置。长按拖动手柄，拖动中改其中一条的名称或 `active`，松手时 UUID 顺序与最新 `profiles` 相同。`onReorder` 不被调用，行上显示新名称和新的激活状态。再测一次：拖动中新增一条并删掉另一条，同时把仍在的项拖出新的相对顺序。松手后 `onReorder` 的 UUID 包含新增项、不含已删项，共有项的相对顺序与松手时 `rows` 一致，随后行上的对象是最新的 `profiles`。现有 `:design:test` 覆盖不到 `ProfileList`。

## r06-4

- 判定：hold。严重度仍为中。
- 现在行号：`IosDialogChrome` 在 `IosControls.kt` 第 965 行。发现稿写的第 984–1015 行整段现在在第 983–1016 行：第 983 行是铺满屏幕的外层 `Box`，第 984–990 行是带 `onScrim` 的遮罩，第 1002–1014 行是卡片 `Column`。卡片有 `clip`、`background` 和 `padding`，没有自己的点击。`IosConfirmDialog` 在第 866 行把遮罩点击收成 `close(false)`。`IosConfirmOverlay` 在第 699–713 行进入同一个确认框。删除应用集合在 `AccessControlScreen.kt` 第 751–761 行使用 `IosConfirmOverlay`。
- 问题类：确认卡片没有消费点击，点在标题、说明或卡片空白上会落到遮罩并取消。
- 不变量：点在卡片范围内不调用 `onScrim`，确认框保持打开，`onFinished` 不触发。点卡片外的遮罩仍按现在的语义取消或关闭。取消按钮结果为 false，确认按钮结果为 true。按钮的点击不被卡片抢走。
- 要动的符号或文件：`IosControls.kt` 的 `IosDialogChrome`，给第 1002 行的卡片加上消费点击、且不触发 `onScrim` 的修饰。同模块里已经这样挡穿透的是 `AccessControlScreen.kt` 第 536–540 行和 `HomeScreen.kt` 第 683–687 行。`IosAboutDialog` 第 930 行也走这个 chrome，卡片会一起吃掉点击；它的遮罩仍然关闭。
- 明确不改什么：遮罩的 `onScrim` 语义；`DialogPillButton` 的点击；`IosSheetFrame` 和 `IosSheetHost`；`legacyConfirm` 的系统 `AlertDialog`。不为关于框单独改文案或按钮。
- 怎么本地验证：从删除应用集合打开确认框。点标题、说明、卡片内按钮以外的空白，框不关闭，`onFinished` 不调用。点卡片外的遮罩，以 false 结束。点取消为 false，点删除为 true。关于框点标题和图标不关闭，点遮罩仍关闭。现有 `:design:test` 覆盖不到 `IosDialogChrome`。

## r06-5

- 判定：hold。严重度仍为中。
- 现在行号：出口卡片的 `onClick` 在 `HomeScreen.kt` 第 331–337 行。发现稿写的第 337–343 行是这一段，打开条件仍是 `nodeSelectable && quickNodes.isNotEmpty()`，不看 `currentGroup`。分组副标题在第 329 行用 `isNotEmpty()` 过滤；`OutboundCard` 第 588 行用 `isNullOrEmpty()` 决定画不画。`QuickNodeSheet` 的 `onSelect` 在第 379–383 行。发现稿写的第 385–389 行是这一段：先把 `switching` 设为 false，只有 `currentGroup != null` 才调用 `onSelectNode`。
- 问题类：快速切换能不能打开，和点选能不能把节点交出去，对当前分组的要求不一致。
- 不变量：`currentGroup` 为 null 或空白时不把 `switching` 设为 true，走第 334–336 行已有的 `onProxy(state.currentGroup)`。有非空分组时才打开列表，并记下这个分组。之后点某个节点，用记下的分组和该节点名调用 `onSelectNode`，再关闭列表。打开期间分组变成 null 或空白，仍然用记下的分组提交，不能只关列表并丢掉节点名。
- 要动的符号或文件：`HomeScreen.kt` 第 331 行的 `onClick`，以及第 379 行传给 `QuickNodeSheet` 的 `onSelect`。打开时记下的分组放在 `HomeScreen` 里，`QuickNodeSheet` 的参数形状可以不动。
- 明确不改什么：`onSelectNode` 的签名；`onOpenAll` 和第 384–386 行的 `onProxy`；`nodeSelectable` 为 false 或 `quickNodes` 为空时的原分支；`OutboundCard` 的副标题规则；`QuickNodeSheet` 的搜索和节点行。
- 怎么本地验证：`running` 为真，`currentNode` 非空，`nodeSelectable` 为真，`quickNodes` 非空。`currentGroup` 为 null 时点出口卡片，列表不出现，调用 `onProxy(null)`，不调用 `onSelectNode`。`currentGroup` 为 `""` 时同样不出现列表，调用 `onProxy("")`。`currentGroup` 为非空分组时列表出现；点一个节点，`onSelectNode` 收到打开时的分组和该节点名，列表关闭。列表开着时把 `currentGroup` 改成 null 再点节点，仍用打开时记下的分组调用 `onSelectNode`。现有 `:design:test` 覆盖不到 `HomeScreen`。
