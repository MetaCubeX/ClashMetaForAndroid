---
title: "审查方案 r03 common"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 1、drop 0、weaken 4"
---

# 审查方案 r03 common

裁定：r03-1 hold；r03-2 weaken；r03-3 weaken；r03-4 weaken；r03-5 weaken。行号与发现稿一致。drop 0。

## r03-1 hold

- 问题类：API 33 起，`permission == null` 时动态接收器被注册成 `RECEIVER_EXPORTED`，其他应用可以投递匹配广播。
- 不变量：API 33 及以上，默认不导出。同一应用 UID 仍能把广播送给未导出的接收器；系统 UID 的系统广播也仍能送达。`permission` 只限制发送方是否持有该权限，不能代替导出标志。只有调用方明确要求导出时才使用 `RECEIVER_EXPORTED`。API 33 以下继续走现在的四参数 `registerReceiver`。
- 要动的符号或文件：`Context.registerReceiverCompat`（`common/src/main/java/com/github/kr328/clash/common/compat/Context.kt`，现为 25–36 行）。增加 `exported: Boolean = false`。API 33 及以上按这个参数选择 `RECEIVER_NOT_EXPORTED` 或 `RECEIVER_EXPORTED`，不再用 `permission == null` 推断。
- 明确不改什么：不改 API 33 以下的调用方式，旧系统没有这两个 flag。不把「传入 permission」改成导出。不改 `Permissions.RECEIVE_SELF_BROADCASTS` 的保护级别，不改 `ProfileReceiver`，也不改现有调用点的参数；默认改为不导出后，本应用自己的广播和系统广播仍然能到。
- 怎么本地验证：`common` 没有测试源码。应新增的断言意图：API 33 及以上，默认以及 `exported = false` 时 flag 为 `RECEIVER_NOT_EXPORTED`，即使 `permission` 非空也一样；`exported = true` 时为 `RECEIVER_EXPORTED`，`permission` 仍原样传入；API 33 以下不传第五个 flag。

## r03-2 weaken

严重度：中 → 低。

反证：`pendingIntentFlags` 在 6–15 行仍是发现稿写的分支。API 30 的 `PendingIntentRecord` 在带 `FLAG_IMMUTABLE` 时跳过 `fillIn`，所以 API 24–30 上 `mutable = true` 确实会得到不可变 PendingIntent。但 `mutable` 默认是 `false`。仓库里的调用都是 `pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)`，没有传入 `mutable = true`：`TunService`、`ProfileReceiver`、`ProfileWorker`、`DynamicNotificationModule`、`StaticNotificationModule`、`LogcatService`。这些调用要的就是不可变，API 24–30 或上 `FLAG_IMMUTABLE` 与它们一致。发现稿说的通知回复、动作按钮和 `send` 填 extras 不生效，当前调用不会发生。

- 问题类：显式 `mutable = true` 时，API 24–30 仍或上 `FLAG_IMMUTABLE`。
- 不变量：`mutable` 默认仍是 `false`。API 31 及以上，`mutable = true` 得到 `FLAG_MUTABLE`，否则得到 `FLAG_IMMUTABLE`，两个标志不同时出现。API 24–30 只在 `mutable = false` 时或上 `FLAG_IMMUTABLE`；`mutable = true` 时保持调用方原始 flags。API 24 以下仍原样返回。
- 要动的符号或文件：`pendingIntentFlags`（`common/src/main/java/com/github/kr328/clash/common/compat/Intents.kt`）。
- 明确不改什么：不改默认参数，不改上述六个调用点，不把 API 23 也纳入这次分支。
- 怎么本地验证：`common` 没有测试源码。应新增的断言意图：API 24–30 且 `mutable = true` 时结果不含 `FLAG_IMMUTABLE`；同一范围 `mutable = false` 时含 `FLAG_IMMUTABLE`；API 31 及以上 `mutable = true` 时含 `FLAG_MUTABLE` 且不含 `FLAG_IMMUTABLE`，`mutable = false` 时相反；API 24 以下等于入参。

## r03-3 weaken

严重度：中 → 低。

反证：`isLightStatusBarsCompat` 与 `isLightNavigationBarCompat` 在 API 30 及以上仍是 `decorView.windowInsetsController?.apply { setSystemBarsAppearance(...) }`（`UI.kt` 56–61、68–77、89–94、101–110）。`View.getWindowInsetsController()` 在 `mAttachInfo == null` 且 parent 不是 `View` 或 `ViewRootImpl` 时返回 null；`Activity.onCreate` 里窗口还没 `addView`，这次写入会被跳过。但读 `decorView` 会安装 Decor。API 30 `PhoneWindow.generateLayout` 会按当前主题把 `SYSTEM_UI_FLAG_LIGHT_STATUS_BAR` / `SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR` 写到 DecorView 上。`ViewRootImpl.adjustLayoutParamsForCompatibility` 在还没置 `PRIVATE_FLAG_APPEARANCE_CONTROLLED` 时，会把这两个旧标志转成对应的 `APPEARANCE_LIGHT_*`。控制器调用被跳过，外观就不会被标记成已由控制器接管，主题写下的标志会在挂上窗口时生效。唯一调用点 `BaseActivity.applyDayNight` 把这两个属性设成 `windowLightStatusBar` / `windowLightNavigationBar`，与主题是同一个布尔值，而且 `applyStyle` 发生在访问 `decorView` 之前。因此「浅色背景上图标颜色丢了」对这条调用不成立。丢失的是：未 attach，且要设的值与主题属性不一致。

- 问题类：API 30 及以上，窗口未 attach 时，与主题不一致的浅色系统栏设置不会留下来。
- 不变量：API 30 以下继续改 `systemUiVisibility`。API 30 及以上，attach 之前的最后一次 true/false 在 attach 之后仍然生效，包括与 `windowLightStatusBar` / `windowLightNavigationBar` 不一致的值。已经 attach 时，结果与现在直接 `setSystemBarsAppearance` 相同。
- 要动的符号或文件：`Window.isLightStatusBarsCompat`、`Window.isLightNavigationBarCompat`（`common/src/main/java/com/github/kr328/clash/common/compat/UI.kt`）。用会保存未 attach 状态的 `WindowInsetsControllerCompat`（`common` 已依赖 `androidx.core`）。
- 明确不改什么：不改 `isSystemBarsTranslucentCompat`，不改主题 XML，不改 `BaseActivity.applyDayNight` 的调用顺序。
- 怎么本地验证：`common` 没有测试源码。应新增的断言意图：API 30 及以上，Decor 尚未 attach 时先设 true 再设 false，attach 之后外观等于最后一次写入；该值与主题 `windowLightStatusBar` / `windowLightNavigationBar` 相反时也等于最后一次写入。API 30 以下仍只改对应的 `systemUiVisibility` 位。

## r03-4 weaken

严重度：中 → 低。

反证：`isSystemBarsTranslucentCompat` 的 setter 在 37–44 行仍只改 `attributes.layoutInDisplayCutoutMode`，没有 `setAttributes`。窗口已经 `addView` 之后，`ViewRootImpl` 用的是 `copyFrom` 得到的副本，再改这个字段不会进 WindowManager。但现有写入都在加入窗口之前：`BaseActivity.onCreate` 里的 `applyDayNight`，以及 `AppBottomSheetDialog.onCreate`、`FullScreenDialog.onCreate`（`Dialog.show` 在 `onCreate` 之后才 `addView`）。加入时的 `copyFrom` 会带走当时的 `SHORT_EDGES`。主题里没有 `windowLayoutInDisplayCutoutMode`，`generateLayout` 不会覆盖这个字段。API 30 `PhoneWindow.setDecorFitsSystemWindows` 只在已有 `ViewRootImpl` 时换 insets 监听，不提交 `LayoutParams`；`onViewRootImplSet` 会在 attach 时再套用已经记下的 `mDecorFitsSystemWindows`。因此「这次调用先把修改前的刘海模式交出去」不成立。显示之后再切换才会停在旧模式，当前没有这种调用。

- 问题类：窗口已经显示后再改 `isSystemBarsTranslucentCompat`，刘海模式不会交给 WindowManager。
- 不变量：API 28 及以上，`true` 仍是 `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`，`false` 仍是 `LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT`。API 30 及以上 `setDecorFitsSystemWindows(!value)` 的含义和相对顺序保持不变。窗口还没加入时，加入时的副本包含这次写入；已经加入后再改，WindowManager 收到新的 cutout 模式。
- 要动的符号或文件：`Window.isSystemBarsTranslucentCompat` 的 setter（`common/src/main/java/com/github/kr328/clash/common/compat/UI.kt`）。改完 `layoutInDisplayCutoutMode` 后 `setAttributes(attributes)`。
- 明确不改什么：不改 `SYSTEM_UI_FLAG_LAYOUT_*` 分支，不改两个 cutout 常量的选择，不改浅色系统栏两个属性，不改 Activity 和 Dialog 的调用点。
- 怎么本地验证：`common` 没有测试源码。应新增的断言意图：API 28 及以上，窗口未加入时写入后 `getAttributes().layoutInDisplayCutoutMode` 已是目标值；窗口已加入后再切换，会调用 `setAttributes`，并且传出的 `LayoutParams.layoutInDisplayCutoutMode` 是切换后的值。API 30 及以上同一次赋值仍调用 `setDecorFitsSystemWindows(!value)`。

## r03-5 weaken

严重度：中 → 低。

反证：`CoroutineScope.ticker`（`Ticker.kt` 9–24 行）仍用空的 `catch (ignored: Exception)`，两条退出路径都没有 `channel.close()`。`CancellationException` 是 `Exception` 的子类，作用域取消时 `delay` 或 `send` 抛出的取消会被吃掉，Channel 保持打开。Rendezvous Channel 上，若另一个作用域还停在 `receive()`，它不会结束。现有调用不是这种用法：`MainActivity`、`FilesActivity`、`LogcatActivity`、`ProfilesActivity`、`ProvidersActivity`、`DynamicNotificationModule` 都在同一个作用域里 `ticker(...)`，再在该作用域的 `select` 里 `ticker.onReceive`。作用域取消时收集方一起取消，界面不会停在一次活着的 `receive` 上。所用间隔是 500 毫秒、1 秒或 1 分钟，`delay` 除取消外不会抛错。发现稿说的「周期刷新停在最后一次」要等生产者单独死亡、收集方还在别的作用域里接收，仓库里没有这条路径。

- 问题类：`ticker` 退出时不关闭 Channel，并吞掉 `CancellationException`。
- 不变量：仍是 `Channel.RENDEZVOUS`，循环里仍先 `send` 当前毫秒再 `delay(period)`。作用域取消、循环结束或循环里抛错时，Channel 都会关闭。`CancellationException` 继续向外抛，这个子协程以取消结束，而不是正常完成。
- 要动的符号或文件：`CoroutineScope.ticker`（`common/src/main/java/com/github/kr328/clash/common/util/Ticker.kt`）。去掉吞掉全部 `Exception` 的空 catch；在 `finally` 里 `channel.close()`。
- 明确不改什么：不改六个调用点的 `select`，不改 `period` 的单位，不改成带缓冲的 Channel。
- 怎么本地验证：`common` 没有测试源码。应新增的断言意图：取消 `ticker` 所在作用域后 `isClosedForReceive` 为真，且该子任务是取消而不是正常完成；另一个作用域里正在 `receive` 的收集方因 Channel 关闭而结束；`delay` 或 `send` 抛出的非取消异常不会被吃掉，Channel 仍然关闭。
