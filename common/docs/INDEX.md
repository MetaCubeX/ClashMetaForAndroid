---
title: "common 模块设计"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: "common 的包、依赖，以及 Store、常量以外的 Global、compat 和 util"
---

# common

`app`、`core`、`service`、`design` 以 `implementation` 依赖本模块。本模块自己只 `compileOnly` [`hideapi`](../../hideapi/docs/INDEX.md)，另外 `implementation` 了协程和 `androidx.core`。namespace 来自根 `build.gradle.kts`：模块名不是 `app` 时为 `com.github.kr328.clash.<模块名>`，因此是 `com.github.kr328.clash.common`。依赖图见 [架构概览](../../docs/architecture/overview.md)。

源码在 `common/src/main/java/com/github/kr328/clash/common/`。

| 包 | 作用 |
|:---|:-----|
| `store` | 带类型的偏好读写。见 [Store](store.md) |
| `constants` | 按运行时包名拼出的动作、组件、权限、authority。见 [常量](constants.md) |
| `compat` | 按 `SDK_INT` 分支的 Android API 包装 |
| `util` | 包名、Intent、分片 Parcel、定时 Channel |
| `Global.kt` | 进程内的 `Application` 和 IO 协程作用域 |
| `log` | 固定 tag 的 `android.util.Log` 包装 |
| `id` | 进程内递增整数。仓库里由 `ProfileWorker` 用作通知 id |

本模块的 Manifest 只声明 `RECEIVE_BROADCASTS` 权限，见 [常量](constants.md)。外部控制动作怎样进 Manifest，见 [运行时](../../docs/systems/runtime/INDEX.md)。

## Global

`Global` 是 `object`，用 `CoroutineScope(Dispatchers.IO)` 委托实现 `CoroutineScope`。`init` 保存 `Application`；`application` 在 `init` 之前访问会因为 `lateinit` 失败。`destroy` 取消这个作用域。

仓库里 `Global.init` 的调用在 `app` 的 `MainApplication.attachBaseContext`。`Global.destroy` 只出现在该类的 `finalize()` 方法里，没有 `override`，源码里也没有别的调用点。

`util` 的顶层 `packageName` 在第一次读取时取 `Global.application.packageName`，之后不再变。`constants` 里那些 `val` 在对应 `object` 第一次初始化时读取它。

## compat

都在 `com.github.kr328.clash.common.compat`。版本判断以源码里的 `Build.VERSION.SDK_INT` 为准。

| 符号 | 行为 |
|:-----|:-----|
| `Application.currentProcessName` | API 28 起用 `Application.getProcessName()`。更低版本调用 `ActivityThread.currentProcessName()`；任何 `Throwable` 记 `Log.w` 后返回 `packageName`。存根见 [hideapi](../../hideapi/docs/INDEX.md) |
| `Drawable.foreground()` | API 26 起，且是 `AdaptiveIconDrawable`、`background == null` 时返回 `foreground`，否则返回自身 |
| `pendingIntentFlags` | `SDK_INT < 24` 时原样返回。`SDK_INT > 30` 且 `mutable` 为真时或上 `FLAG_MUTABLE`，否则在 `SDK_INT >= 24` 时或上 `FLAG_IMMUTABLE`。`mutable` 默认是 `false` |
| `PackageInfo.versionCodeCompat` | API 28 起读 `longVersionCode`，否则把 `versionCode` 转成 `Long` |
| `Configuration.preferredLocale` | API 24 起取 `locales[0]`，否则取 `locale` |
| `fromHtmlCompat` | API 24 起用 `FROM_HTML_MODE_COMPACT`，否则调用单参数 `Html.fromHtml` |
| `Context.getColorCompat` / `getDrawableCompat` | 转到 `ContextCompat` |
| `Context.registerReceiverCompat` | API 33 起：`permission == null` 时 flag 为 `RECEIVER_EXPORTED`，否则为 `RECEIVER_NOT_EXPORTED`。更低版本不传这个 flag |
| `Context.startForegroundServiceCompat` | API 26 起 `startForegroundService`，否则 `startService` |
| `Service.startForegroundCompat` | API 34 起带 `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`，否则两参数的 `startForeground` |
| `TextView.textAppearance` | 只写。API 23 起 `setTextAppearance(value)`，否则 `setTextAppearance(context, value)`。读会抛 `UnsupportedOperationException` |
| `Window.isSystemBarsTranslucentCompat` | 只写。API 30 起 `setDecorFitsSystemWindows`；更低版本改 `systemUiVisibility` 的 layout 标志。API 28 起同时改 `layoutInDisplayCutoutMode` |
| `Window.isLightStatusBarsCompat` / `isLightNavigationBarCompat` | 只写。API 30 起走 `WindowInsetsController`，否则改对应的 `systemUiVisibility` 亮色标志 |
| `Window.isAllowForceDarkCompat` | API 29 起读写 `decorView.isForceDarkAllowed`。更低版本读出 `false`，写被忽略 |

## util、log、id

| 符号 | 行为 |
|:-----|:-----|
| `KClass.componentName` | `ComponentName(Global.application.packageName, java.name)` |
| `KClass.intent` | `Intent(Global.application, java)` |
| `Intent.grantPermissions` | 默认同时加上读、写 URI 权限 flag，返回 `this` |
| `Intent.fileName` | `data` 的 scheme 为 `file` 时取其 scheme-specific part。写入用 `Uri.fromParts("file", …)` |
| `Intent.uuid` | scheme 为 `uuid` 时把 scheme-specific part 解析成 `UUID`。另有 `setUUID` / `setFileName` |
| `List.writeToParcelSlice` | 先写列表长度，再写一个 `Binder`。对方用事务码 `10` 按 offset 和 chunk 取一段，逐个 `writeToParcel` |
| `Creator.createListFromParcelSlice` | 按块 `transact`。返回 `false`、读到长度为 0，或已取满时停止 |
| `PatternFileName` | `Regex("[^*&%\\n\\r/]+")` |
| `CoroutineScope.ticker` | `Channel.RENDEZVOUS`。循环里先 `send` 当前毫秒时间，再 `delay(period)`。捕获 `Exception` 后循环结束，没有 `close` |
| `Log` | tag 常量是 `ClashMetaForAndroid`，`i` / `w` / `e` / `d` / `v` 都带上它。`f` 调用的是 `android.util.Log.wtf(message, throwable)`，按这个两参数重载，第一个参数是 tag，不是上面的常量 |
| `UndefinedIds.next` | `@Synchronized`。`current = ((current and 0x00FFFFFF) + 1) or 0x14000000`。初始 `current` 为 0 |

导航：[模块 README](../README.md)、[模块导航](../../docs/modules/common/INDEX.md)。
