---
title: "界面视觉收口"
type: plan
status: implemented
phase: N/A
updated: 2026-10-16
summary: "分组行、弹层、空态和对话框主题已收成一套。编辑器保持现有校验。没有装到手机上"
---

# 界面视觉收口

> 制定日期：2026-10-16
> 状态：已落地
> 看板：[并行任务](../parallel/archive/ui-visual-consistency.md)

## 背景

用户可见屏幕的根视图已经是 Compose。规格在 [其余屏幕的分组交互](compose-remaining-screens.md)，行为在 [界面](../../docs/systems/ui/INDEX.md)。

现在有三套行：首页和设置顶层的 `IosInsetGroup` 自绘 12dp 圆角和 20dp Material 箭头；设置子页用复制的分组行，圆角 10dp、箭头 13dp；代理、配置、日志、文件、访问控制的长列表是贴边白行。底部动作表只有 8dp 底边距，动作之间没有分隔线。确认框有的走 `iosConfirm`，有的仍是 `MaterialAlertDialogBuilder`，破坏性红色读的是系统夜间模式。空列表有的把页面标题或「置空」当成说明。选中态用零宽空格压掉箭头。加载和用量条是 Material 进度条。

这些是同一套界面里的接缝。连接列表、规则全文、链式代理、配置拖拽排序仍不做。

## 目标

- 首页、设置顶层和设置子页使用同一套分组行。
- 长列表保持通栏。一行一个节点、一份配置或一个应用，左右不再套一层分组卡片。
- 底部弹层避开导航栏，动作之间有分隔线。含破坏性动作的表下面有单独的取消。
- 简单是否确认都走 `iosConfirm`。红色跟随 `UiStore.darkMode`，与 `ClashMetaXTheme` 同一套判断。
- 空列表和搜索无结果显示专门的句子。`empty` 维持「置空」，不拿来当空态。
- 选中、半选、反序用标记，不再插入零宽空格或在文案前加「✓」。
- 导航栏末端用已有的 24dp 矢量图标，点击区域 44dp，带内容描述。
- 关于、文本、列表、映射、二维码、拉取进度、策略名称继续 inflate 现有布局。对话框主题改成分组背景和 14dp 圆角。校验函数和「取消写回、重置得到 null」保持不动。

## 非目标

- 不新增 `IClashManager` 方法，不做连接列表、规则全文、链式代理、配置拖拽排序。
- 不引入 Cupertino、SF Symbols 或 Material 3。
- 不把编辑器改成 Compose。不改 `ValidatorNotBlank`、`ValidatorHttpUrl`、`ValidatorAutoUpdateInterval`、`ValidatorAgeSecretKey`、`ValidatorFileName`、`NullableTextAdapter.Port`。
- 不改访问控制的点选语义、配置的导入更新，或 WebDAV 的合并与默认常量。
- 不改 `applicationId`、`clash://`、`clashmeta://` 和外部控制 action。
- 不把长列表改成分组卡片，也不把分组设置改成通栏。
- 本方案不要求装到手机上，也不打 release 包。

## 共享约定

T1 落地之后，其余任务只调用，不再改这些类型的默认外观。

| 约定 | 落点 |
|:-----|:-----|
| 分组行 | `IosInsetGroup` 逐行调用已有的 `IosPreferenceRow`。去掉它自己的 12dp 裁剪和私有 `IosDetailRow` |
| 通栏行 | 继续 `IosListRow`。父列表不加第二层 16dp 水平内边距 |
| 选中 | `IosDetail.selection`：`None`、`Checked`、`Mixed`。`selected = true` 仍表示 `Checked`。半选画一条短横，不再叠 `ic_app_set_indeterminate` |
| 箭头 | `disclosure` 默认 `true`。选择行设 `false`，只留标记 |
| 标记 | 蓝色小勾或短横，画在 `IosDetailTrailing`。动作表的 `IosAction.marked` 用同一个标记 |
| 导航图标 | 新的 `IosNavIcon(icon, description, onClick, tint)`。44dp，按压是背景，`indication = null` |
| 返回 | `IosNavBar` 的内容描述用 `R.string.back`。图标保持现在的自动镜像返回箭头 |
| 弹层 | 公开 `IosSheet`。卡片底色 `IosColors.grouped`，圆角 14dp。底边距是 8dp 加上对话框窗口自己的 `WindowInsets.navigationBars`。屏幕内容继续只用 Design 的 insets，不叠 `safeDrawing` |
| 动作表 | 行间 0.5dp 分隔线。`cancelLabel` 默认 null；非空时在下方再放一张取消卡 |
| 确认色 | `iosConfirm` 用 `UiStore.darkMode`：`ForceDark`、`ForceLight`，`Auto` 才看 `UI_MODE_NIGHT_YES`。函数签名不变 |
| 进度 | `IosProgressRow` 用画布弧线。新增 `IosFractionBar(fraction)`，高 4dp，前景 `blue`，轨道 `track` |
| 搜索清除 | `shellIosSearchDimens().touchTargetMin` 改为 44。条高 `barHeightDefault` 仍是 36，清除按钮只加宽点击区域 |
| 首页卡片 | 连接卡和流量卡圆角改为 10dp，与分组行一致。模式按钮保持 12dp |

`IosDetail` 的新字段都有默认值。不改字段的调用在 T1 之后仍能编译，外观与现在的导航行一致。

### 文案

只增加这三个键，八个 `design/src/main/res/values*/strings.xml` 都写上。不改已有的 `empty`。

| 键 | 默认 | zh / zh-rHK / zh-rTW | ja | ko | ru | vi |
|:---|:-----|:---------------------|:---|:---|:---|:---|
| `back` | Back | 返回 | 戻る | 뒤로 | Назад | Quay lại |
| `search_empty` | No matches | 没有匹配结果 / 沒有符合的結果 / 沒有符合的結果 | 該当なし | 검색 결과 없음 | Ничего не найдено | Không có kết quả |
| `list_empty` | Nothing here | 这里是空的 / 這裡是空的 / 這裡是空的 | 何もありません | 비어 있음 | Здесь пусто | Không có mục nào |

zh-rHK 与 zh-rTW 用繁体那一列。简体只写 `values-zh`。

空态用法：

- 搜索框非空且过滤结果为空：`search_empty`。代理节点、组选择、日志详情、访问控制都按这条。
- 列表本身没有条目：`list_empty`。配置、文件、日志文件、日志详情在没有关键字时、代理组里没有节点时，都按这条。
- 访问控制没有关键字且应用表为空：继续 `app_set_summary_empty`。

## 任务与文件

同时改一份文件的任务会互相覆盖。下表是所有权。看板见 [并行任务](../parallel/archive/ui-visual-consistency.md)。

| ID | 做什么 | 可以改的文件 | 依赖 |
|:---|:-------|:-------------|:-----|
| T1 | 共享行、弹层、确认色、进度、导航图标、三条文案、首页和设置顶层圆角 | `IosComponents.kt`、`IosControls.kt`、`IosTheme.kt`、`HomeScreen.kt`、`SettingsScreen.kt`、`nota/ui/ios/ShellIosSpecs.kt`、`design/src/main/res/values*/strings.xml` | — |
| T2 | 代理：空态、菜单改 `IosSheet`、测速和更多改成图标 | `ProxyScreen.kt` | T1 |
| T3 | 配置和文件的空态；用量条改 `IosFractionBar`；删除动作表带取消 | `ProfilesScreen.kt`、`FilesScreen.kt` | T1 |
| T4 | 日志空态；清空、导出、删除改成图标 | `LogsScreen.kt`、`LogcatScreen.kt` | T1 |
| T5 | 去掉零宽空格和半选叠图；反序用 `marked`；菜单带取消 | `AccessControlScreen.kt`、`AppsStrategyScreen.kt` | T1 |
| T6 | 对话框主题；简单确认改走 `iosConfirm` | 见下方 | — |
| T7 | 编译，并更新系统文档和快照 | `docs/systems/ui/INDEX.md`、`design/docs/compose.md`、`dev/progress/status.md`、本方案、看板 | T1–T6 |

T1 与 T6 没有共同文件，可以同时做。T2 到 T5 等 T1 合并进工作区后再改。T2 到 T6 之间没有共同文件。只有 T7 运行 Gradle。

### T2 代理

导航栏末端是 `ic_baseline_more_vert` 和 `ic_baseline_flash_on`，描述用 `more` 和 `delay_test`。测速中只把闪电图标换成 `IosProgressRow`，更多仍在。节点行和组选择行设 `disclosure = false`。排序和「排除不可选」放进 `IosSheet`，删掉 `ProxyScreen` 里那份自绘 `Dialog`。排序仍是 `ProxySort.Default`、`Title`、`Delay`。空关键字表示不过滤。

### T3 配置和文件

配置为空、目录为空时用 `list_empty`。用量条只在现在会画条的时候出现，比例算法不变。配置动作表里的删除保持破坏性颜色，并传入 `cancel`。文件动作表同样传入 `cancel`。

### T4 日志

日志文件列表为空用 `list_empty`。详情在有关键字且没有行时用 `search_empty`，否则用 `list_empty`。列表页的清空用 `ic_baseline_delete`，描述 `delete_all_logs`，颜色 `red`。文件日志的导出用 `ic_baseline_publish`，删除用 `ic_baseline_delete`。实时日志的关闭保持文字按钮，它是这一页的主退出。

### T5 访问控制

应用行和集合行设 `disclosure = false`。全选用 `Checked`，半选用 `Mixed`，未选用 `None`。`trailing` 不再放 `\u200B` 或 `\u3000`。半选图标叠层删掉。反序动作设 `marked = reversed`，标题只用 `reverse`。动作表传入 `cancel`。集合的点选规则不变，仍以 [访问控制](../../docs/systems/access-control/INDEX.md) 为准。

分流策略当前行同样去掉零宽空格，`disclosure = false`，当前项 `Checked`。行尾的应用列表和编辑图标留在原地。

### T6 对话框

可以改这些文件：

- `design/src/main/res/values/themes.xml`
- `design/src/main/java/com/github/kr328/clash/design/Design.kt`
- `MainDesign.kt`、`AppsStrategyDesign.kt`、`OverrideSettingsDesign.kt`、`MetaFeatureSettingsDesign.kt`、`PropertiesDesign.kt`、`LogsDesign.kt`
- `dialog/Input.kt`、`dialog/Progress.kt`
- `preference/EditableText.kt`、`EditableTextList.kt`、`EditableTextMap.kt`
- `util/SubscriptionQr.kt`、`util/Toast.kt`
- `app/src/main/java/com/github/kr328/clash/MetaFeatureSettingsActivity.kt` 里未知 Geo 格式那一个对话框

在 `AppThemeLightBase` 和 `AppThemeDarkBase` 上设置 `materialAlertDialogTheme`。亮色卡片白、按钮 `#007AFF`；暗色卡片 `#1C1C1E`、按钮 `#0A84FF`。圆角 14dp。正文字和按钮仍由原来的布局 inflate。

这些简单确认改成调用 `iosConfirm`，签名不变：覆写重置、Meta 重置、属性未保存退出、清空日志。未知 Geo 格式只有一个确定按钮，确定用 `R.string.ok`，标题和说明保持现有字符串。

`Design.showToast` 仍是 `Snackbar`。背景和文字按当前 `UiStore.darkMode` 取分组色和标签色，动作文字用蓝色。

策略名称对话框继续用现有 binding，确定按钮的启用逻辑留在 `TextWatcher` 里。输入框的校验失败仍禁用确定。

## 验收

- [x] 首页和设置顶层的行与设置子页同一圆角、同一箭头
- [x] 代理、配置、日志、文件、访问控制仍是通栏
- [x] 底部弹层在导航栏上方；破坏性动作表有取消；`iosConfirm` 在强制深色和强制浅色下使用对应红色
- [x] 配置、文件、日志不再把标题或「置空」当成空态；搜索无结果显示「没有匹配结果」
- [x] 访问控制和分流策略的选择行没有零宽空格，半选是短横
- [x] 代理导航栏末端是两个图标；测速中闪电图标变为进度
- [x] 编辑器的取消、重置和校验与现在一致
- [x] `:design:compileMetaDebugKotlin` 与 `:app:compileMetaDebugKotlin` 通过

## 风险

| 风险 | 缓解 |
|:-----|:-----|
| 两个任务改同一文件 | 只改上表列出的文件。共享类型的默认值由 T1 一次加完 |
| 对话框主题影响无关的 Material 对话框 | 只改 `materialAlertDialogTheme`。全屏对话框和底部表的现有 style 不动 |
| `IosInsetGroup` 再套一层卡片背景 | 分组背景只来自 `IosPreferenceRow` |
| 弹层把活动的 insets 又加一遍 | 弹层只读自己窗口的 `navigationBars` |
| 并行 Gradle 占满内存 | 只有 T7 编译 |
| 改空态时误改 `empty` | `empty` 继续表示置空。新句子用新键 |

## 参考

- 决定：[ADR-002](../decisions/002-compose-ios-home.md)
- 已落地的屏幕规格：[其余屏幕的分组交互](compose-remaining-screens.md)
- 行为说明：[界面](../../docs/systems/ui/INDEX.md)
