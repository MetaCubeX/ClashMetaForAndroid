---
title: "Compose 屏幕"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-30
summary: "屏幕用 ComposeView 把点击送进 Design.requests。切 tab 保留页面状态，滚动位置返回时直接定位"
---

# Compose 屏幕

首页画什么、设置怎样分组，见 [界面](../../docs/systems/ui/INDEX.md)。取舍见 [ADR-002](../../docs/decisions/compose-ios-home.md)。请求成员和 Activity 的对应见 [屏幕与请求通道](screens.md)。

## 依赖

Compose 只加在 `design`。`design/build.gradle.kts` 打开 `buildFeatures.compose`，并应用 `libs.plugins.compose.compiler`（`org.jetbrains.kotlin.plugin.compose`，版本与 Kotlin `2.1.0` 相同）。BOM 是 `platform(libs.androidx.compose.bom)`。版本不在该 Gradle 文件里，而在 `gradle/libs.versions.toml` 的 `composeBom`，当前为 `2025.02.00`。

直接依赖是 `androidx.compose.ui:ui`、`androidx.compose.foundation:foundation`、`androidx.compose.material:material-icons-core`、`androidx.compose.material:material`。没有 Material3，也没有第三方 Cupertino 库。源码里没有 `AndroidView`。

## 放进请求通道

每个 `*Design` 仍是 `Design` 的子类。`root` 是 `ComposeView`，Activity 继续 `setContentView(design.root)`。组合策略是 `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`。`setContent` 外包一层 `AgenticClashTheme`。

点击不直接调 Activity。回调进入各自的 `request`，再 `requests.trySend`。Activity 仍从这条无界通道接收。

`MainDesign` 用 `mutableStateOf` 保存 `HomeUiState` 和 `Insets`。`setProfileName`、`setClashRunning`、`setTraffic`、`setOutbound`、`setMode`、`setUptime`、`setAside` 都在 `Dispatchers.Main` 上替换 `state`。停止时清掉节点、快切列表、流量和运行时长，并把外部资源个数设为 -1。`setTraffic` 追加一个 `TrafficSample`，只留最后 36 个。系统栏监听同时写 `surface.insets` 和 Compose 的 `insets`。关于由 `StartAbout` 打开 `AboutActivity`，页面是 `AboutScreen`，引用开源二级页是 `NoticesScreen`。`AboutActivity` 把安装包的启动图标转成 `ImageBitmap` 传给 `AboutDesign`，身份头不引用设计模块里的固定图标。`AboutScreen` 自己保存二维码弹窗的当前条目，关闭后把焦点还给对应二维码行；`AboutDesign` 保存引用开源二级页的焦点请求，返回后交给 `AboutScreen` 消费。

`HomeScreen` 的回调对应这些请求：`onToggle` 发 `ToggleStatus`，`onMode` 发 `SetMode`，`onProfiles` 发 `OpenProfiles`，`onApps` 发 `OpenAppsStrategy`，`onProxy` 发 `OpenProxy(group)`，`onSelectNode` 发 `SelectNode`，`onProviders` 发 `OpenProviders`，`onLogs` 发 `OpenLogs`，`onReload` 发 `ReloadKernel`，`onWebdav` 发 `OpenWebdav`，`onFiles` 发 `OpenFiles`。首页标题没有设置按钮。`group` 是首页当前节点选中的组名，没有节点时为空。

`SettingsDesign` 同样用 `mutableStateOf` 保存 `Insets`。标题优先用 Activity 的 `title`，空白则用 `R.string.settings`。返回走 `onBackPressedDispatcher`，不发 `Request`。`onApp` 发 `StartApp`，`onNetwork` 发 `StartNetwork`，`onOverride` 发 `StartOverride`，`onMeta` 发 `StartMetaFeature`，`onWebdav` 发 `StartWebdav`，`onAbout` 发 `StartAbout`。

## 屏幕包

路径是 `design/src/main/java/universe/agentic/clash/`，包名 `universe.agentic.clash`。首页和设置顶层是 `HomeScreen.kt`、`SettingsScreen.kt`。其余屏幕各有一个 `*Screen.kt`。共享控件在 `IosComponents.kt`、`IosControls.kt` 和 `IosTheme.kt`。列表行、分组偏好行、开关和搜索框的实现复制在 `nota/ui` 和 `universe/agentic/ui/search`，屏幕只调用 `Ios*`。

`AgenticClashTheme` 读 `UiStore(context).darkMode`。`ForceDark` 提供 `DarkColors`，`ForceLight` 提供 `LightColors`，`Auto` 看 `UI_MODE_NIGHT_YES`。颜色经 `LocalIosColors` 交给 `IosTheme.colors`，并同时提供 `LocalShellIosDark` 和 `LocalShellPressTint`。按压灰浅色是 `#1A767680`，深色是 `#24767680`，按下 100ms，松开 300ms。`IosType` 的字号是固定 sp，不随暗色改变：`largeTitle` 28，`title2` 20，`title3` 18，`headline` 16，`body` 15，`section` 与 `footnote` 13，`subhead` 与 `segment` 14。根页面标题用 `title2`。

`ShellPages` 切 tab 时只改可见性：`ShellTabBar` 选中项变化后，仪表盘留在组合里并把尺寸置 0，同时用空的 `drawWithContent` 跳过绘制。0dp 的盒子不裁剪，不跳过的话 `requiredSize` 的点击区会画到窗口左上角，切页淡入时露出来。另外四页的 `View` 都留在 `FrameLayout` 里，非当前页设 `INVISIBLE`，不销毁。页面状态因此跟着实例留下，包括滚动位置、分段、展开项、搜索词和多选。滚动位置记 `firstVisibleItemIndex` 和 `firstVisibleItemScrollOffset` 两个值，返回时直接定位，不播滚动动画；数据键没变不重新请求。以后若改成按需创建页面，用 `SaveableStateHolder` 的 `SaveableStateProvider(tab)` 保存同一组状态。

切 tab 的动画是淡入 200ms，`CubicBezierEasing(0f, 0f, 0.58f, 1f)`，旧页同帧移出，不做位移。仪表盘是 Compose 内容，用 `graphicsLayer` 改 `alpha`；另外四页在 `AndroidView` 里，图层变换不可靠，改对宿主 `FrameLayout` 调 `ViewPropertyAnimator`。两条路径都在 `tab` 变化时从 0 开始，首帧透明，不会先闪一帧。系统关闭动画时两者都直接到位。

两屏都用 `LocalDensity` 把 `Insets` 换成 dp，再垫在根布局上。

控件在 `IosComponents.kt` 和 `IosControls.kt`，建在 Compose foundation 上。`IosInsetGroup` 接收 `List<IosDetail>`，逐行调用 `IosPreferenceRow`。行在 `enabled` 且有 `onClick` 时才可点。`disclosure` 默认 true，这时画 16dp 箭头；单选行设 `exclusive`，画右侧圆环；多选行设 `checkable`，画方框，半选是短横。`IosSheet` 只圆顶部 16dp，底边距是对话框窗口自己的导航栏，卡片内再留 16dp，宽度不超过 480dp。几个短操作从锚点弹出 `IosPopupMenu`：宽至少 160dp、至多 280dp，圆角 12dp，行高 40dp，文字左对齐，同组不画分割线，破坏性项用红字并在前面留 8dp。点外面或返回就关闭，没有取消按钮。`IosActionSheet` 仍用于需要确认的底部表：动作之间有分隔线，普通动作用正文色，破坏性用红字，`cancelLabel` 非空时下面再放一张取消卡。`IosNavIcon` 的点击区域是 44dp，按下时在 32dp 的圆里铺按压灰。`IosFractionBar` 高 4dp。`iosConfirm` 画在全屏透明窗口里：竖屏贴底，横屏居中，宽不超过 480dp，圆角 16dp，四周 16dp。取消是灰色胶囊，普通确认是主题色胶囊并叠 10% 黑，破坏性确认是红字。颜色仍按 `UiStore.darkMode` 取，判断与 `AgenticClashTheme` 相同。首页状态卡使用圆形电源键和 `IosSegmentedControl`。`TrafficSparkline` 画在独立的流量卡上。「正在使用」是两列六格，图标在标题左侧。`SettingsScreen` 使用 `IosNavBar` 和 `IosInsetGroup`。分组行单行最低 52dp，双行最低 64dp。导航栏高 48dp，背景与页面相同，返回键的点击区域也是 48dp，内容描述是 `back`，图标用正文色。`IosScreenScaffold` 把内容裁在导航栏下方，导航栏后画，滚动时不盖住标题。设置顶层也走这个脚手架。`IosButton` 的最小高度是 44dp，形状是胶囊。`IosSegmentedControl` 高 40dp，首页状态卡把它的水平内边距设为 0。
