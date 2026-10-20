---
title: "Compose 屏幕"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-17
summary: "屏幕用 ComposeView 把点击送进 Design.requests。颜色读 UiStore.darkMode，没有 Cupertino 库"
---

# Compose 屏幕

首页画什么、设置怎样分组，见 [界面](../../docs/systems/ui/INDEX.md)。取舍见 [ADR-002](../../dev/decisions/002-compose-ios-home.md)。请求成员和 Activity 的对应见 [屏幕与请求通道](screens.md)。

## 依赖

Compose 只加在 `design`。`design/build.gradle.kts` 打开 `buildFeatures.compose`，并应用 `libs.plugins.compose.compiler`（`org.jetbrains.kotlin.plugin.compose`，版本与 Kotlin `2.1.0` 相同）。BOM 是 `platform(libs.androidx.compose.bom)`。版本不在该 Gradle 文件里，而在 `gradle/libs.versions.toml` 的 `composeBom`，当前为 `2025.02.00`。

直接依赖是 `androidx.compose.ui:ui`、`androidx.compose.foundation:foundation`、`androidx.compose.material:material-icons-core`、`androidx.compose.material:material`。没有 Material3，也没有第三方 Cupertino 库。源码里没有 `AndroidView`。

## 放进请求通道

每个 `*Design` 仍是 `Design` 的子类。`root` 是 `ComposeView`，Activity 继续 `setContentView(design.root)`。组合策略是 `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`。`setContent` 外包一层 `ClashMetaXTheme`。

点击不直接调 Activity。回调进入各自的 `request`，再 `requests.trySend`。Activity 仍从这条无界通道接收。

`MainDesign` 用 `mutableStateOf` 保存 `HomeUiState` 和 `Insets`。`setProfileName`、`setAppsStrategyName`、`setClashRunning`、`setTraffic`、`setCurrentNode`、`setMode`、`setHasProviders` 都在 `Dispatchers.Main` 上替换 `state`。停止时清掉 `currentNode`，并把 `traffic` 换成空的 `TrafficRates`。`setTraffic` 追加一个 `TrafficSample`，只留最后 36 个。系统栏监听同时写 `surface.insets` 和 Compose 的 `insets`。关于对话框仍是 `AlertDialog` 加 `DesignAboutBinding`，不是 Compose。

`HomeScreen` 的回调对应这些请求：`onToggle` 发 `ToggleStatus`，`onMode` 发 `SetMode`，`onProfiles` 发 `OpenProfiles`，`onApps` 发 `OpenAppsStrategy`，`onProxy` 发 `OpenProxy(group)`，`onProviders` 发 `OpenProviders`，`onLogs` 发 `OpenLogs`，`onSettings` 发 `OpenSettings`，`onHelp` 发 `OpenHelp`，`onAbout` 发 `OpenAbout`。`group` 是首页当前节点选中的组名，没有节点时为空。

`SettingsDesign` 同样用 `mutableStateOf` 保存 `Insets`。标题优先用 Activity 的 `title`，空白则用 `R.string.settings`。返回走 `onBackPressedDispatcher`，不发 `Request`。`onApp` 发 `StartApp`，`onNetwork` 发 `StartNetwork`，`onOverride` 发 `StartOverride`，`onMeta` 发 `StartMetaFeature`，`onWebdav` 发 `StartWebdav`。

## `ios` 包

路径是 `design/src/main/java/com/github/kr328/clash/design/ios/`。首页和设置顶层是 `HomeScreen.kt`、`SettingsScreen.kt`。其余屏幕各有一个 `*Screen.kt`。共享控件在 `IosComponents.kt`、`IosControls.kt` 和 `IosTheme.kt`。列表行、分组偏好行、开关和搜索框的实现复制在 `nota/ui` 和 `universe/agentic/ui/search`，屏幕只调用 `Ios*`。

`ClashMetaXTheme` 读 `UiStore(context).darkMode`。`ForceDark` 提供 `DarkColors`，`ForceLight` 提供 `LightColors`，`Auto` 看 `UI_MODE_NIGHT_YES`。颜色经 `LocalIosColors` 交给 `IosTheme.colors`，并同时提供 `LocalShellIosDark` 和 `LocalShellPressTint`。`IosType` 的字号是固定 sp，不随暗色改变：`largeTitle` 34，`title2` 22，`headline` 与 `body` 17，`subhead` 15，`footnote` 与 `segment` 13。大标题只用于首页。

两屏都用 `LocalDensity` 把 `Insets` 换成 dp，再垫在根布局上。

控件在 `IosComponents.kt` 和 `IosControls.kt`，建在 Compose foundation 上。`IosInsetGroup` 接收 `List<IosDetail>`，逐行调用 `IosPreferenceRow`。行在 `enabled` 且有 `onClick` 时才可点。`disclosure` 默认 true，这时画 13dp 箭头；选择行设 false，只留 `IosSelection` 的勾或短横。`IosSheet` 是底部卡片，底边距是 8dp 加上对话框窗口自己的导航栏。`IosActionSheet` 的动作之间有 0.5dp 分隔线，`cancelLabel` 非空时下面再放一张取消卡。`IosNavIcon` 的点击区域是 44dp，图标 24dp。`IosFractionBar` 高 4dp。`iosConfirm` 的破坏性红色按 `UiStore.darkMode` 取色，判断与 `ClashMetaXTheme` 相同。`HomeScreen` 使用 `IosButton`、`IosModePicker`、`TrafficSparkline` 和 `IosInsetGroup`。连接卡和流量卡圆角 10dp，模式按钮保持 12dp。`SettingsScreen` 使用 `IosNavBar` 和 `IosInsetGroup`。分组行和 `IosModePicker` 的最小高度是 44dp。导航栏高 44dp，背景与页面相同，返回键的点击区域也是 44dp，内容描述是 `back`。`IosScreenScaffold` 把内容裁在导航栏下方，导航栏后画，滚动时不盖住标题。设置顶层也走这个脚手架。`IosButton` 的最小高度是 50dp。`IosSegmentedControl` 高 32dp，上述两个屏幕都没有调用它。
