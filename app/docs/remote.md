---
title: "app 怎样调用 :background"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-15
summary: "界面进程绑定 RemoteService；启停、状态、文件和偏好各自另有一条路"
---

# app 怎样调用 :background

`:background` 里有哪些服务见 [运行时](../../docs/systems/runtime/INDEX.md) 和 [架构概览](../../docs/architecture/overview.md)。本文只写 `app` 源码里的调用方向。

`RemoteService`、`ClashService`、`TunService`、`FilesProvider`、`StatusProvider`、`PreferenceProvider` 的 `android:process` 都是 `:background`。`app` 清单没有给 Activity、`LogcatService`、`TileService` 指定进程，它们在默认应用进程。

## 进程入口

`MainApplication.attachBaseContext` 调用 `Global.init`。`onCreate` 在区分进程之前先把地理文件解到 `filesDir/clash`：`geoip.metadb`、`geosite.dat`、`ASN.mmdb`、`BundleMRS.7z`。文件不存在，或修改时间早于本包 `lastUpdateTime` 时，从 assets 重新拷贝。

随后：

- `currentProcessName == packageName`（界面进程）时调用 `Remote.launch()`。
- 其他进程（包括 `:background`）调用 `sendServiceRecreated()`，不绑定远程服务。`Broadcasts` 收到该广播后把 `clashRunning` 置为 false，并通知观察者。接收器还没登记时，这次广播不会被 `Broadcasts` 处理。

## 可见时才绑定 RemoteService

`Remote.launch()` 只做两件事：挂上 `ApplicationObserver`，并在 IO 线程跑 `verifyApp`。

`ApplicationObserver` 在任一 Activity `onStart` 后视为可见，全部 Activity 停止或销毁后视为不可见。可见时 `service.bind()` 且 `broadcasts.register()`；不可见时 `service.unbind()` 且 `broadcasts.unregister()`。因此界面进程持有的 `IRemoteService` 连接跟「有没有 Activity 处于 started」走，不跟隧道是否在跑走。`LogcatService` 自己另绑一次，不受这次 unbind 影响，见 [日志捕获](logcat.md)。

`remote/Service.kt` 的 `bind()` 对 `RemoteService` 使用 `BIND_AUTO_CREATE`。`onServiceConnected` 把 `IBinder` `unwrap` 成 `IRemoteService`，放进 `Resource`。`onServiceDisconnected` 把 `Resource` 置空。若距离上次断开不到 10 秒，则 unbind，并执行 `Remote` 传入的崩溃回调：结束已创建的 Activity，再启动 `AppCrashedActivity`。`bindService` 抛异常时同样走这个回调。

`Resource.get()` 在值为空时挂起，`set` 非空后唤醒等待者。`reset` 只在当前值与传入实例是同一引用时清空。

`verifyApp` 比较 `AppStore.updatedAt` 和包的 `lastUpdateTime`。不一致时用 `verifyApk()` 在 apk 与 split 里找 `lib/<abi>/libclash.so`，ABI 必须和 `Build.SUPPORTED_ABIS` 有交集。失败则结束已创建 Activity 并打开 `ApkBrokenActivity`。成功则把 `updatedAt` 写成这次的 `lastUpdateTime`。

## Binder：IRemoteService

方向是界面进程调用 `:background`，不是反过来。

```
界面进程
  bindService(RemoteService)
    → IRemoteService.clash()    → IClashManager（ClashManager）
    → IRemoteService.profile()  → IProfileManager（ProfileManager）
```

`util/Remote.kt` 是这条 Binder 的唯一包装：

- `withClash` 取 `Remote.service.remote`，再 `clash()`，在 `Dispatchers.IO` 上执行块。
- `withProfile` 同样取连接，再 `profile()`。
- 只捕获 `DeadObjectException`：打日志、`reset` 这次拿到的实例，然后无限重试。其他异常原样抛出。

`IClashManager` 上界面会用到的方法：`queryTunnelState`、`queryTrafficNow`、`queryTrafficTotal`、`queryProxyGroupNames`、`queryProxyGroup`、`queryProviders`、`patchSelector`、`healthCheck`、`updateProvider`、`queryOverride`、`patchOverride`、`clearOverride`、`setLogObserver`。`queryConfiguration` 在接口上，当前 `app` 源码没有调用。

`IProfileManager` 上的方法：`create`、`clone`、`commit`、`release`、`delete`、`patch`、`update`、`queryByUUID`、`queryAll`、`queryActive`、`setActive`、`webdavPerform`。配置和同步的语义见 [配置](../../docs/systems/profiles/INDEX.md) 与 [WebDAV](../../docs/systems/webdav/INDEX.md)。

调用方：主界面、代理、提供者、覆盖设置、Meta 功能设置走 `withClash`。主界面、外部导入、配置列表、配置属性、新建配置、网络设置、分流策略、WebDAV 设置、访问控制离开后的同步走 `withProfile`。`LogcatService` 不走 `withClash`，它自己 `unwrap` 之后调用 `clash().setLogObserver`。

## 不经过 IRemoteService 的三条路

这些仍然从默认进程进 `:background`，但不是 `RemoteService` 的 Binder。

**状态。** `StatusClient` 对 `Authorities.STATUS_PROVIDER`（`:background` 的 `StatusProvider`）做 `contentResolver.call`，方法是 `currentProfile`。返回 Bundle 里的 `name`；服务没在跑或调用失败时得到 null。`ExternalControlActivity`、`TileService` 和 `Broadcasts.register` 用「名字非空」表示隧道在跑。

**文件。** `FilesClient` 用 `DocumentsContract` 访问 `Authorities.FILES_PROVIDER`（`:background` 的 `FilesProvider`）：列子文档、重命名、删除、以及经 `copyContentTo` 导入或导出。只有 `FilesActivity` 使用它。

**偏好。** `ServiceStore` 在 `BaseService` / `TunService` 上直接打开名为 `service` 的 `SharedPreferences`；其他 Context（界面进程）走 `MultiProcessPreference`，authority 是 `Authorities.SETTINGS_PROVIDER`，也就是 `:background` 的 `PreferenceProvider`。策略 JSON 写在调用方 `filesDir` 下，细节见 [访问控制](../../docs/systems/access-control/INDEX.md)。

`AppStore` 和 `TipsStore` 不走这个 Provider。它们用本进程 `MODE_PRIVATE` 的 `SharedPreferences`，文件名分别是 `app` 和 `tips`。`AppStore` 只保存上面的 `updated_at`。`TipsStore` 有 `request_donate` 和 `primary_version`，当前仓库没有其他引用。

## 启停与重载

`util/Clash.kt`：

- `startClashService`：`UiStore.enableVpn` 为真时先 `VpnService.prepare`。需要授权则把系统 Intent 返回给调用方，不启动服务。已授权则前台启动 `TunService`。未开 VPN 则前台启动 `ClashService`。两者都在 `:background`。
- `stopClashService`：`sendBroadcastSelf(ACTION_CLASH_REQUEST_STOP)`。广播 `setPackage` 为本包，并要求 `RECEIVE_SELF_BROADCASTS`。
- `requestClashReload`：同样发送 `ACTION_CLASH_REQUEST_RELOAD`。`AccessControlActivity` 在离开页面之后使用它。

谁处理这两条请求在 `service`，不在 `app`。

## 广播：:background 通知界面进程

`sendBroadcastSelf` 把包名设成本包。`Broadcasts` 的接收器再核对 `intent.package` 是否为本包，然后按动作分发：

| 动作 | 界面侧 |
|:-----|:-------|
| `ACTION_SERVICE_RECREATED` | `clashRunning = false`，`onServiceRecreated` |
| `ACTION_CLASH_STARTED` | `clashRunning = true`，`onStarted` |
| `ACTION_CLASH_STOPPED` | `clashRunning = false`，`onStopped`，额外字段是停止原因 |
| `ACTION_PROFILE_CHANGED` | `onProfileChanged` |
| `ACTION_PROFILE_UPDATE_COMPLETED` / `FAILED` | 从额外字段解析 UUID；失败还有原因字符串 |
| `ACTION_PROFILE_LOADED` | `onProfileLoaded` |

`register()` 成功之后用 `StatusClient.currentProfile() != null` 填一次 `clashRunning`。

`Broadcasts.registered` 初始是 false，`register` 和 `unregister` 都没有把它写成 true。因此 `register()` 不会因为「已经注册」而返回；`unregister()` 一进来就返回，不会 `unregisterReceiver`，也不会把 `clashRunning` 置回 false。可见性回调仍然会调用这两个函数。`TileService` 自己在监听期间注册和注销，并带上 `RECEIVE_SELF_BROADCASTS`；`Broadcasts` 注册时没有传这个权限参数。
