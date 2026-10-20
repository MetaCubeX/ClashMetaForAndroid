---
title: "屏幕与请求通道"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-16
summary: "Design 用无界 Channel 把请求交给 Activity。每个 *Design 的根视图是 ComposeView，由对应 Activity 构造并 setContentDesign"
---

# 屏幕与请求通道

界面分工见 [界面](../../docs/systems/ui/INDEX.md)。设置页里的控件见 [设置项控件](preferences.md)。

## Design 基类

`design/.../Design.kt` 的 `Design<R>` 持有：

- `root`：子类提供的根视图。
- `requests`：`Channel<R>(Channel.UNLIMITED)`。
- `surface`：可观察的 `Surface`，字段是系统栏 `insets`。
- 协程作用域：`Dispatchers.Unconfined`。

`context` 是 `AppCompatActivity` 时，基类给 `window.decorView` 装系统栏 insets 监听，有变化就写入 `surface.insets`。每个 Compose 屏幕在自己的 `init` 里再次调用 `setOnInsertsChangedListener`。该函数设置的是单个 `OnApplyWindowInsetsListener`，所以以子类这次为准。子类这次仍会更新 `surface.insets`，并另存一份给 Compose。Compose 不再套一层 `WindowInsets.safeDrawing`。

界面侧把请求放进通道：Kotlin 里是 `requests.trySend`。`showToast` 在主线程用根视图上的 `Snackbar`；`ToastDuration` 的 `Short`、`Long`、`Indefinite` 对应 Snackbar 的同名时长。

Activity 不靠回调注册。`BaseActivity.setContentDesign` 在 `decorView` 投递之后把 `design` 设为该实例，setter 调用 `setContentView(design.root)`。`main` 再从通道取出请求：多数屏幕在 `select` 里用 `design.requests.onReceive`，和 `events` 一起等；`ApkBrokenActivity`，以及 `LogcatActivity` 打开本地文件的那条路径，用 `design.requests.receive()`。`onDestroy` 调用 `design?.cancel()`，取消这个 `Design` 的作用域。

通道也可以由 Activity 写入。已看到的有：`AccessControlActivity` 发送 `ReloadApps`，`ProxyActivity` 在组下标有效时发送一次 `Reload`，`FilesActivity.onBackPressed` 发送 `PopStack`。`ProxyDesign.Request.ReloadAll` 和 `PatchMode` 仍留在类型里，界面不再发送它们。

`HelpDesign` 和 `AppCrashedDesign` 是 `Design<Unit>`。对应 Activity 只循环接收 `events`，不读 `design.requests`。`HelpDesign` 的链接点击直接调用构造时传入的 `openLink`。

设置项控件写的是绑定的属性，不是这条 `requests` 通道。需要 Activity 接着做事时，控件的 `OnChangedListener` 再 `trySend`。

## 屏幕与 Activity

下表每一行都能在 `app` 里看到构造该 `Design` 并调用 `setContentDesign`。请求成员是 `*Design` 里声明的类型，不是 Activity 的处理分支。

| Design | Activity | 请求 |
|:-------|:---------|:-----|
| `AccessControlDesign` | `AccessControlActivity` | `ReloadApps`、`SelectAll`、`SelectNone`、`SelectInvert`、`Import`、`Export` |
| `ApkBrokenDesign` | `ApkBrokenActivity` | `Request(url)` |
| `AppCrashedDesign` | `AppCrashedActivity` | 无。类型参数是 `Unit` |
| `AppSettingsDesign` | `AppSettingsActivity` | `ReCreateAllActivities` |
| `AppsStrategyDesign` | `AppsStrategyActivity` | `Create`、`Edit`、`Active`、`Choose` |
| `FilesDesign` | `FilesActivity` | `OpenFile`、`OpenDirectory`、`RenameFile`、`DeleteFile`、`ImportFile`、`ExportFile`、`PopStack` |
| `HelpDesign` | `HelpActivity` | 无。类型参数是 `Unit` |
| `LogcatDesign` | `LogcatActivity` | `Close`、`Delete`、`Export`。同一 Activity 构造两次：本地文件传 `streaming = false`，实时日志传 `true` |
| `LogsDesign` | `LogsActivity` | `StartLogcat`、`DeleteAll`、`OpenFile` |
| `MainDesign` | `MainActivity` | `ToggleStatus`、`OpenProxy`、`OpenProfiles`、`OpenProviders`、`OpenAppsStrategy`、`OpenLogs`、`OpenSettings`、`OpenHelp`、`OpenAbout`、`SetMode` |
| `MetaFeatureSettingsDesign` | `MetaFeatureSettingsActivity` | `ResetOverride`、`ImportGeoIp`、`ImportGeoSite`、`ImportCountry`、`ImportASN` |
| `NetworkSettingsDesign` | `NetworkSettingsActivity` | `StartAccessControlList`、`AccessControlChanged` |
| `NewProfileDesign` | `NewProfileActivity` | `Create`、`OpenDetail`、`LaunchScanner` |
| `OverrideSettingsDesign` | `OverrideSettingsActivity` | `ResetOverride` |
| `ProfilesDesign` | `ProfilesActivity` | `UpdateAll`、`Create`、`Active`、`Update`、`Edit`、`Duplicate`、`Delete` |
| `PropertiesDesign` | `PropertiesActivity` | `Commit`、`BrowseFiles` |
| `ProvidersDesign` | `ProvidersActivity` | `Update(index, provider)` |
| `ProxyDesign` | `ProxyActivity` | `ReloadAll`、`ReLaunch`、`PatchMode`、`Reload`、`Select`、`UrlTest` |
| `SettingsDesign` | `SettingsActivity` | `StartApp`、`StartNetwork`、`StartOverride`、`StartMetaFeature`、`StartWebdav` |
| `WebdavSettingsDesign` | `WebdavSettingsActivity` | `Test`、`Sync`、`Pull`、`Push` |

上表每个 `*Design` 的根视图都是 `ComposeView`，组合策略是 `DisposeOnViewTreeLifecycleDestroyed`，内容包在 `ClashMetaXTheme` 里。屏幕文件在 `design/.../ios/`。关于对话框仍 inflate `design_about.xml`。单行文本、列表、映射、age 密钥、订阅二维码、拉取进度和策略编辑对话框仍用原来的布局。设置子页不再调用 `preferenceScreen`；覆写和 Meta 的列表编辑器仍用 `preference` 包。

## model

`design/.../model/` 里除应用集合以外的类型：

| 类型 | 源码里的内容 |
|:-----|:-------------|
| `AppInfo` | `packageName`、`label`、`icon`、`installTime`、`updateDate` |
| `AppInfoSort` | `Label`、`PackageName`、`InstallTime`、`UpdateTime`，各自按对应字段比较。访问控制页把它写入 `UiStore.accessControlSort` |
| `Behavior` | `autoRestart`。`AppSettingsActivity` 实现该接口并传给 `AppSettingsDesign` |
| `DarkMode` | `Auto`、`ForceLight`、`ForceDark`。存在 `UiStore.darkMode`。`BaseActivity` 用它选择日间或夜间主题 |
| `File` | `id`、`name`、`size`、`lastModified`、`isDirectory`。`FilesDesign` 的列表项 |
| `LogFile` | `fileName` 与 `date`。`parseFromFileName` 用正则 `clash-(\d+).log` 做整段匹配，时间取第一个捕获组。`generate` 用格式 `clash-%d.log` 和当前时间 |
| `ProfileProvider` | `File`、`Url`、`QR`，以及带 `name`、`summary`、`icon`、`intent` 的 `External`。`NewProfileDesign` 的列表 |
| `ProxyState` | `now`。`ProxyActivity` 为每个代理组建一个，再交给 `ProxyDesign` |

`AppSet.kt` 里的集合类型和 `applyAppSetToggle` 不在这里写。点选规则见 [访问控制](../../docs/systems/access-control/INDEX.md)。
