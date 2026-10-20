---
title: "ClashRuntime 与模块"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-17
summary: "ClashRuntime 如何装上 clash/module，以及各模块在服务循环里的职责"
---

# ClashRuntime 与模块

`ClashService` 和 `TunService` 各自持有一个 `clashRuntime { ... }`。它跑在服务的协程里，真正碰 `Clash` 核心的是这些模块，不是界面。进程和 Binder 见 [进程与远程接口](process.md)。访问控制名单怎样被选进隧道，用户可见规则见 [访问控制](../../docs/systems/access-control/INDEX.md)；下面只写 `TunService` 建接口时实际调用的分支。

## 运行时怎么接上模块

`clashRuntime` 返回的 `ClashRuntime` 有两个方法。`launch()` 在 `Dispatchers.IO` 上抢进程内唯一的 `globalLock`，然后：

1. `Clash.reset()`，并 `Clash.clearOverride(Clash.OverrideSlot.Session)`。
2. 构造 `ClashRuntimeScope`。`install` 立刻返回模块实例，并在这次 `launch(Dispatchers.IO)` 的协程上启动子任务，子任务调用 `Module.execute()`。
3. 执行调用方传入的 `block`。`block` 返回后，对这个 IO 协程 `cancel()`，模块子任务一起取消。
4. `finally` 里在 `NonCancellable` 下再次 `Clash.reset()` 和清除 Session 覆盖。

`requestGc()` 调用 `Clash.forceGc()`。这把锁把两次运行时的初始化串起来：上一次的 `launch` 协程（含最后的 reset）结束之前，下一次进不了初始化。源码没有 `SupervisorJob`。模块子任务失败会取消这次 IO 协程；服务本身的 `CoroutineScope(Dispatchers.Default)` 也是普通 `Job`，取消会继续向上传。

`Module.execute()` 调用子类的 `run()`。结束时在 `NonCancellable` 里对已登记的 `BroadcastReceiver` 调一次 `onReceive(null, null)` 再 `unregisterReceiver`。`enqueueEvent` 把事件送进无界 `Channel`。`onEvent` 是这个 Channel 的 `onReceive`，供服务循环 `select`。

`receiveBroadcast` 默认 `requireSelf = true`，用 `Permissions.RECEIVE_SELF_BROADCASTS` 登记，因此只能收到持有该权限的发送方。显式传 `false` 的模块接收系统广播。服务发往本包的广播走 `sendBroadcastSelf`，带的是同一权限。

`ClashService` 和 `TunService` 的循环都 `catch (Exception)`，把 `message` 记到 `reason`，`finally` 里 `stopSelf()`。`TunService` 在这之前还会 `tun.close()`。`onDestroy` 把 `serviceRunning` 设为假，广播 `ACTION_CLASH_STOPPED`（额外字段 `stop_reason`），再取消服务协程。`reason` 仍为空时，销毁日志写成 `successfully`。

## 两条服务各装什么

`ClashService` 的安装顺序是 `CloseModule`、`ConfigurationModule`、`NetworkObserveModule`，然后按 `ServiceStore.dynamicNotification` 安装 `DynamicNotificationModule` 或 `StaticNotificationModule`，最后是 `AppListCacheModule`、`TimeZoneModule`、`SuspendModule`。该布尔值默认是真，所以默认走动态通知。`select` 只看三件事：`CloseModule` 退出；`ConfigurationModule` 的 `LoadException` 记下 `message` 后退出；`NetworkObserveModule` 的事件不退出。它不安装 `TunModule` 和 `ReloadModule`。

`TunService` 在 `CloseModule` 之后、`ConfigurationModule` 之前多安装 `ReloadModule` 和 `TunModule`，其余与上面相同。进入循环前调用 `tun.open()`。循环里关闭和配置失败同样退出。重载事件在 `NonCancellable` 里调用 `reloadTun`；返回假时把 `reason` 设为 `Reload tun failed` 并退出。网络事件在 API 22 到 28 上调用 `setUnderlyingNetworks`，然后继续循环。`onDestroy` 先 `TunModule.requestStop()`。

## 各模块

### CloseModule

登记 `ACTION_CLASH_REQUEST_STOP`。收到一次就记录日志、发出 `RequestClose`，然后 `run()` 返回。服务循环把它当成退出。

### ReloadModule

只由 `TunService` 安装。循环接收 `ACTION_CLASH_REQUEST_RELOAD`，每次 `enqueueEvent(Unit)`。`ClashService` 没有这个模块，那条停止以外的重载广播没有对应的接收循环。

### ConfigurationModule

启动时向内部的 `CONFLATED` 通道投一次，强制加载。之后 `select` 这个通道和两条广播：`ACTION_PROFILE_CHANGED`（读 `EXTRA_UUID`）、`ACTION_OVERRIDE_CHANGED`（当成没有 UUID）。

当前配置取 `ServiceStore.activeProfile`。已经加载过、且广播里的 UUID 不是这份当前配置时，跳过。否则：

- 没有 `activeProfile`，或 `ImportedDao` 查不到，直接送出 `LoadException`，消息是 `R.string.no_profile_selected`。
- `Clash.setAgeSecretKey`：空白密钥当成 null。
- `Clash.load` 读取 `imported/<uuid>` 目录并等待完成。
- 读出该 UUID 的 `selections`。`Clash.patchSelector` 返回假的组被 `SelectionDao.removeSelections` 删掉。
- `StatusProvider.currentProfile` 设为这条已导入记录的 `name`，并广播 `ACTION_PROFILE_LOADED`。

加载过程中的其他异常同样变成 `LoadException`，消息用异常的 `message`，没有则是 `Unknown`。

### NetworkObserveModule

向 `ConnectivityManager` 注册 `NetworkCallback`。请求能力是 `NOT_VPN`、`INTERNET`、`NOT_RESTRICTED`；API 28 及以上再加 `FOREGROUND`。`onAvailable` 只建记录。`onLosing`、`onLost`、`onLinkPropertiesChanged` 更新记录、尝试通知 DNS，并把 `Network` 送进事件通道。注册或反注册失败只打日志。

DNS 取优先级最低分的网络。分数按传输类型：Wi-Fi 0、以太网 1、API 31 及以上的 USB 2、蓝牙 3、蜂窝 4、API 35 及以上的卫星 5、其他 20、VPN 90、拿不到能力 100。正在 losing（`losingMs` 仍在未来）再加 10。分数更低的优先。新的 DNS 列表非空且与上次不同时，把地址格式化成端口 53 的文本，调用 `Clash.notifyDnsChanged`。列表变空不会在运行中通知；`run()` 结束时才通知空列表并反注册。

服务循环把这些 `Network` 事件取走。除了 `TunService` 在 API 22 到 28 上设置底层网络，事件本身不改变配置。

### TunModule 与 TunService.open

`TunModule.run()` 阻塞在关闭通道上。`close()` 往这个通道发一次；结束时 `requestStop()`，即 `Clash.stopHttp()` 和 `Clash.stopTun()`。

`TunService` 里的扩展 `open()` 用 `VpnService.Builder` 建接口，再 `attach`。常量是：

| 常量 | 值 |
|:-----|:---|
| `TUN_MTU` | 9000 |
| `TUN_GATEWAY` / 前缀 | `172.19.0.1` / 30 |
| `TUN_PORTAL`、`TUN_DNS` | `172.19.0.2` |
| `TUN_GATEWAY6` / 前缀 | `fdfe:dcba:9876::1` / 126 |
| `TUN_PORTAL6`、`TUN_DNS6` | `fdfe:dcba:9876::2` |
| `NET_ANY` / `NET_ANY6` | `0.0.0.0` / `::` |

`allowIpv6` 为真才加入 v6 地址、v6 DNS 和 v6 路由。`bypassPrivateNetwork` 为真时，路由来自 `R.array.bypass_private_route`（以及 v6 的 `bypass_private_route6`），并额外路由虚拟 DNS `/32` 和 `/128`。为假时路由 `NET_ANY/0` 和可选的 `NET_ANY6/0`。

访问控制：`activeAppsStrategyConfigUuid` 能在 `appsStrategyConfigs` 里找到，就用那份 `mode` 和 `packages`，否则用 `accessControlMode` 和 `accessControlPackages`。`AcceptAll` 不改应用列表。`AcceptSelected` 对名单再加上本包调用 `addAllowedApplication`。`DenySelected` 对名单去掉本包后调用 `addDisallowedApplication`。单个包失败被 `runCatching` 吃掉。

其余设置：`setBlocking(false)`，会话名 `Clash`，`setConfigureIntent` 指向 `Components.MAIN_ACTIVITY`。API 29 及以上 `setMetered(false)`。同一条件下若 `systemProxy` 为真，`listenHttp()` 用 `127.x.y.z:0` 调用 `Clash.startHttp`（`x`、`y`、`z` 各是 1 到 199 的随机整数），再 `setHttpProxy`。代理绕过列表是 `HTTP_PROXY_BLACK_LIST`；绕过私网时再追加 `HTTP_PROXY_LOCAL_LIST`。`allowBypass` 为真则 `allowBypass()`。

`establish()` 为空就抛 `VpnEstablishException`。成功则 `detachFd()`，交给 `Clash.startTun`。参数来自 `TunDevice`：文件描述符、`tunStackMode`、网关、portal、DNS，以及 `vpn::protect` 和 `queryUid`。`dnsHijacking` 为真时 DNS 字符串是 `NET_ANY`（只有 `0.0.0.0`）；为假时是 portal，v6 打开则再加 portal6。`queryUid` 在 `SDK_INT < 29` 时恒为 -1；否则调用 `ConnectivityManager.getConnectionOwnerUid`，失败也是 -1。

`reloadTun` 再调一次 `open()`。`VpnEstablishException` 时记日志并返回真，保留当前接口。其他异常再试一次 `open()`；仍失败返回假，服务循环退出。

### 通知

`onCreate` 无论开关都调用 `StaticNotificationModule.createNotificationChannel` 和 `notifyLoadingNotification`。渠道 `clash_status_channel`，重要性低。加载通知和之后的状态通知都用 id `R.id.nf_clash_status`，点击进入主界面。

`StaticNotificationModule` 等待 `ACTION_PROFILE_LOADED`，然后用 `StatusProvider.currentProfile`（空则 `Not selected`）再次 `startForeground`。正文是 `R.string.running`。

`DynamicNotificationModule` 用同一渠道。屏幕亮着时每秒读 `Clash.queryTrafficNow` 和 `queryTrafficTotal`，用 `NotificationManager.notify` 刷新当前速率和累计流量。`ACTION_SCREEN_ON` / `OFF` 只开关这个刷新，登记时 `requireSelf` 为假。`ACTION_PROFILE_LOADED` 更新标题，空名字用 `Not selected`。初始标题字面量是 `Not Selected`。它不在循环里再调 `startForeground`；前台身份来自 `onCreate` 的加载通知。

### AppListCacheModule

`getInstalledPackages(0)`，丢掉 `applicationInfo` 为空的项。分组键是非空的 `sharedUserId`，否则是包名。一组只有一个包时，交给核心的名字强制用包名；多于一个时用分组键。然后 `Clash.notifyInstalledAppsChanged`，参数是 UID 到这个名字。

广播是 `PACKAGE_ADDED` 和 `PACKAGE_REMOVED`，data scheme `package`，`requireSelf` 为假。每次广播后延迟 10 秒再进入下一轮 `reload()`。`run()` 一开始就会先加载一次。

### TimeZoneModule

进入循环先把 `TimeZone.getDefault()` 的 `id` 和 `rawOffset / 1000` 交给 `Clash.notifyTimeZoneChanged`，再等待下一次 `ACTION_TIMEZONE_CHANGED`。这里用默认的自身广播权限登记。

### SuspendModule

用 `PowerManager.isInteractive` 初始化，缺省当成亮屏。调用 `Clash.suspendCore(!interactive)`。之后以 `requireSelf = false` 接收 `ACTION_SCREEN_ON`（`suspendCore(false)`）和 `ACTION_SCREEN_OFF`（`suspendCore(true)`）。`run()` 结束时在 `NonCancellable` 里 `suspendCore(false)`。其他动作的分支会调用 `Clash.healthCheckAll()`，源码注释写明不可达。
