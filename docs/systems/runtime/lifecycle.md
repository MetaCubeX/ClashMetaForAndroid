---
title: "启停与重载"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-17
summary: "按是否开启 VPN 启动 :background 里的 TunService 或 ClashService；停止和重载只发本包广播。"
---

# 启停与重载

调用方在默认进程。`ClashService` 和 `TunService` 在 `:background`。`TunService` 继承 `VpnService`。`ClashService` 不建 VPN。

组件表见 [进程与远程接口](../../../service/docs/process.md)。`ClashRuntime` 装哪些模块见 [ClashRuntime 与模块](../../../service/docs/runtime.md)。绑定和 `IRemoteService` 见 [app 怎样调用 :background](../../../app/docs/remote.md)。

## 界面进程

进程名等于包名时，`MainApplication.onCreate` 调用 `Remote.launch()`。界面可见时，它绑定 `RemoteService`，并登记 `Broadcasts`。`:background` 不走 `Remote.launch()`。

`BaseActivity.clashRunning` 读 `Remote.broadcasts.clashRunning`。收到 `Intents.ACTION_CLASH_STARTED` 时为真，收到 `ACTION_CLASH_STOPPED` 时为假。

`MainActivity` 处理 `MainDesign.Request.ToggleStatus`。已在跑则 `stopClashService()`。否则调用 `startClash()`。

`startClash()` 先 `withProfile { queryActive() }`。没有当前配置，或尚未导入，就不启动。否则调用 `startClashService()`。返回的授权 Intent 非空时，结果为 `RESULT_OK` 再调一次。`withProfile` 用的是已经绑定的远程接口。

`ExternalControlActivity` 启动前和主界面一样要求已导入的当前配置。没有则只提示未选择，不启动，也不拉起 VPN 授权。`TileService` 和 `RestartReceiver` 仍直接调用 `startClashService()` / `stopClashService()`。开机接收器还要看 `StatusProvider.shouldStartClashOnBoot`。入口之间的差别见 [app 的 Activity](../../../app/docs/activities.md)。

三个函数在 `app` 的 `util/Clash.kt`。动作常量在 `common` 的 `Intents`。字符串用 `Global.application.packageName` 拼接，见 `common` 的 `util/Global.kt` 里的 `packageName`。

- `startClashService` 读 `UiStore.enableVpn`。为真时先 `VpnService.prepare`。需要授权就把系统 Intent 交回调用方，服务先不启。已授权则 `startForegroundServiceCompat` 启动 `TunService`。为假时同样启动 `ClashService`。API 26 及以上是 `startForegroundService`，否则是 `startService`。
- `stopClashService` 经 `sendBroadcastSelf` 发送 `Intents.ACTION_CLASH_REQUEST_STOP`。
- `requestClashReload` 同样发送 `Intents.ACTION_CLASH_REQUEST_RELOAD`。

`sendBroadcastSelf` 把包名设成当前包，并带上 `Permissions.RECEIVE_SELF_BROADCASTS`。

## 服务起来

两条服务的 `onCreate` 都先看 `StatusProvider.serviceRunning`。已经是真就 `stopSelf()` 并返回，不再 `runtime.launch()`。否则把它设为真，调用 `StaticNotificationModule.createNotificationChannel` 和 `notifyLoadingNotification`，然后 `runtime.launch()`。

`onStartCommand` 调用 `sendClashStarted()`，发出 `ACTION_CLASH_STARTED`。`ClashService` 返回 `START_STICKY`。`TunService` 返回 `super.onStartCommand`。这条开始广播不等配置加载完成。界面的 `clashRunning` 可以先变成真。

`clashRuntime` 的 `launch()` 在 `Dispatchers.IO` 上抢 `globalLock`。然后 `Clash.reset()`，并 `Clash.clearOverride(Clash.OverrideSlot.Session)`。接着执行服务传入的块。块里 `install` 模块。`TunService` 在 `select` 之前调用 `tun.open()`。`ClashService` 没有这一步。块返回后取消这次运行，并在 `finally` 里再次 reset、清掉 Session 覆盖。这些 `Clash` 调用进入 `libclash.so`，见 [core 桥接边界](../../../core/docs/bridge.md)。

## 停止

`ACTION_CLASH_REQUEST_STOP` 由两条服务都安装的 `CloseModule` 接收。它收到一次就返回，并向 `select` 送出 `RequestClose`。循环因此退出。配置加载失败也会让循环退出。模块里的其他事件不在这里展开。

`finally` 里，`TunService` 先 `tun.close()`，再 `stopSelf()`。`ClashService` 只 `stopSelf()`。

`onDestroy` 时，`TunService` 先 `TunModule.requestStop()`。两边都把 `serviceRunning` 设为假，调用 `sendClashStopped(reason)`，再 `cancelAndJoinBlocking()`。`reason` 为空时，销毁日志写成 `successfully`。停止广播的额外字段是 `Intents.EXTRA_STOP_REASON`。

界面侧 `Broadcasts` 把 `clashRunning` 置为假。`BaseActivity.onStopped` 在原因非空、且页面仍在前台时弹出异常。

## 重载

`requestClashReload()` 只发 `ACTION_CLASH_REQUEST_RELOAD`。只有 `TunService` 安装的 `ReloadModule` 在循环里接收它。每次向 `select` 送一个事件。`ClashService` 没有这条接收循环。广播到了也不会重载那条服务。

`TunService` 在 `NonCancellable` 里调用 `reloadTun`。它再执行一次 `tun.open()`。`VpnEstablishException` 时保留当前接口并返回真。其他异常再试一次 `open()`。仍失败则返回假。返回假时把 `reason` 设为 `Reload tun failed`，`select` 退出，然后走上一节的 `finally` 和 `onDestroy`。返回真则循环继续，服务不销毁。

访问控制保存后，只有这份名单正在驱动已运行的隧道才调用 `requestClashReload()`，不先停服务再等待。见 [访问控制](../access-control/INDEX.md)。

## 外部动作

`ExternalControlActivity` 在清单里导出。写死的动作是 `com.github.metacubex.clash.meta.action.START_CLASH`、`STOP_CLASH`、`TOGGLE_CLASH`。

活动分支比较的是运行时常量 `Intents.ACTION_START_CLASH`、`ACTION_STOP_CLASH`、`ACTION_TOGGLE_CLASH`。它们是 `Global.application.packageName` 加上 `.action.` 和对应后缀。meta flavor 在未设置 `remove.suffix` 时加上 `applicationIdSuffix` `.meta`。默认 applicationId 因此是 `com.github.metacubex.clash.meta`，与清单字面量一致。

分支里怎样启动或停止，见 [app 的 Activity](../../../app/docs/activities.md)。
