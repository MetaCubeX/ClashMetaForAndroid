---
title: "service 进程与远程接口"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-15
summary: ":background 中的服务与 Provider，以及 RemoteService 暴露的 Binder"
---

# 进程与远程接口

`service/src/main/AndroidManifest.xml` 把下面的组件都放在 `:background`。界面进程不持有 VPN，也不直接调用 `Clash`。跨进程入口是 `RemoteService` 和三个 Provider。隧道在运行时系统里的角色见 [运行时](../../docs/systems/runtime/INDEX.md)。

`BaseService` 是 `Service`，同时是 `Dispatchers.Default` 上的 `CoroutineScope`。`onDestroy` 里 `cancelAndJoinBlocking()`。`ClashService`、`RemoteService`、`ProfileWorker` 继承它。`TunService` 继承 `VpnService`，自己实现同一套作用域，不继承 `BaseService`。

## 组件

| 组件 | 导出 | 作用 |
|:-----|:-----|:-----|
| `ClashService` | 否 | 前台服务，`foregroundServiceType="specialUse"`。不建 VPN，只跑一套 `ClashRuntime` |
| `TunService` | 否 | `VpnService`。权限 `BIND_VPN_SERVICE`，intent-filter 动作 `android.net.VpnService`。同样是 `specialUse` 前台服务 |
| `RemoteService` | 否 | 绑定后交出 `IClashManager` 和 `IProfileManager`。不是前台服务 |
| `ProfileWorker` | 否 | 按闹钟或立即请求更新已导入配置的前台服务，`specialUse` |
| `FilesProvider` | 是 | `DocumentsProvider`。authority `${applicationId}.files`，权限 `MANAGE_DOCUMENTS`，`grantUriPermissions` |
| `StatusProvider` | 否 | authority `${applicationId}.status`。用 `call` 查询当前配置名 |
| `PreferenceProvider` | 否 | authority `${applicationId}.settings`。多进程读写名为 `service` 的偏好 |
| `ProfileReceiver` | 是 | 权限 `${applicationId}.permission.RECEIVE_BROADCASTS`。开机、换包、对时和配置更新闹钟 |

声明里的权限还有 `ACCESS_NETWORK_STATE`、`CHANGE_NETWORK_STATE`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`、`RECEIVE_BOOT_COMPLETED`、`QUERY_ALL_PACKAGES`。`RECEIVE_BROADCASTS` 的权限定义在 `common` 的清单里，保护级别是 `privileged|signature`。`common` 的 `Authorities` 用 `packageName` 拼出 `.status`、`.settings`、`.files`，与上面的 authority 一致。

`ClashService` 与 `TunService` 的 `onCreate` 都先看 `StatusProvider.serviceRunning`。已经是真就 `stopSelf()` 并返回，不再启动运行时。否则把它设为真，创建通知渠道，发出「正在加载」通知，然后 `runtime.launch()`。两者的 `onTrimMemory` 都调用 `runtime.requestGc()`。

## 谁启动、谁绑定

`app` 的 `startClashService()` 读 `UiStore.enableVpn`。为真时先 `VpnService.prepare`；系统返回同意意图就把它交回调用方，否则 `startForegroundService` 启动 `TunService`。为假时启动 `ClashService`。`stopClashService()` 只向本包发送 `Intents.ACTION_CLASH_REQUEST_STOP`。`requestClashReload()` 发送 `ACTION_CLASH_REQUEST_RELOAD`。

`ClashService.onStartCommand` 发送 `ACTION_CLASH_STARTED` 并返回 `START_STICKY`。`TunService.onStartCommand` 同样先发 `ACTION_CLASH_STARTED`，然后返回 `super.onStartCommand`。`onBind`：`ClashService` 和 `ProfileWorker` 都返回一个空的 `Binder()`，不提供管理接口。`TunService` 没有重写 `onBind`，由 `VpnService` 接系统的 VPN 绑定。

`app` 的 `remote.Service.bind()` 用 `BIND_AUTO_CREATE` 绑定 `RemoteService`，在 `onServiceConnected` 里 `unwrap(IRemoteService::class)`。`LogcatService` 同样绑定，再取 `clash()`。`RemoteService.onCreate` 创建 `ClashManager` 和 `ProfileManager`，各自 `wrap()` 成 Binder。`onBind` 返回 `RemoteService` 自己的 `wrap()`。`onDestroy` 先走 `BaseService` 的取消，再取消这两个对象自己的协程。

`ProfileManager` 初始化时调用 `WebdavAutoSync.syncIfDue`，并在自己的 IO 协程里打开 Room、调用 `ProfileReceiver.rescheduleAll`。绑定 `RemoteService` 会创建它，从而开始这两件事。`syncIfDue` 内部仍会检查是否开启、是否已配置、是否到期。同步规则见 [WebDAV](../../docs/systems/webdav/INDEX.md)。

`ProfileReceiver` 收到 `BOOT_COMPLETED`、`MY_PACKAGE_REPLACED`、`TIMEZONE_CHANGED` 或 `TIME_SET` 时，清掉进程内的 `initialized` 标记，再以前台服务启动 `ProfileWorker`，动作是 `ACTION_PROFILE_SCHEDULE_UPDATES`。收到 `ACTION_PROFILE_REQUEST_UPDATE` 时，把这个 Intent 的组件改成 `ProfileWorker` 再启动它。

闹钟不靠清单里的隐式过滤器。`pendingIntentOf` 使用显式组件 `ProfileReceiver`，动作是 `Intents.ACTION_PROFILE_REQUEST_UPDATE`（`packageName.intent.action.REQUEST_UPDATE`），并用 `setUUID` 带上配置 UUID。清单里另一个过滤器写的是字面量 `{applicationId}.intent.action.PROFILE_REQUEST_UPDATE`，scheme 为 `uuid`，和 `Intents` 里的动作字符串不一样。

`ProfileWorker.onStartCommand` 返回 `START_NOT_STICKY`。`ACTION_PROFILE_REQUEST_UPDATE` 为该 UUID 启动一个任务，调用 `ProfileProcessor.update`。`ACTION_PROFILE_SCHEDULE_UPDATES` 调用 `ProfileReceiver.rescheduleAll`，再延迟 30 秒。服务自己的循环先延迟 10 秒，再按顺序 `join` 已入队任务，队列空了就 `stopSelf()`。更新成功或失败会发结果通知，并分别广播 `ACTION_PROFILE_UPDATE_COMPLETED` 或 `ACTION_PROFILE_UPDATE_FAILED`。成功后 `scheduleNext`。

`app` 的 `RestartReceiver` 在开机和换包时读取 `StatusProvider.shouldStartClashOnBoot`，为真才调用 `startClashService()`。这个值看的是文件，不是界面进程里的内存字段。

## 状态与偏好 Provider

`StatusProvider.call` 只处理 `currentProfile`。`serviceRunning` 为真时返回带 `name` 的 Bundle，否则返回 null。`query`、`insert`、`update`、`delete`、`getType` 都抛 `IllegalArgumentException("Stub!")`。

`serviceRunning` 的内存值只活在 `:background` 里的这个 Provider 进程。setter 同时写 `shouldStartClashOnBoot`：在 `filesDir/service_running.lock` 上 `createNewFile()` 或 `delete()`。`currentProfile` 只是内存字符串，由 `ConfigurationModule` 在配置加载成功后设成已导入配置的名字。界面通过 `StatusClient` 对 `.status` authority 做 `contentResolver.call`，因此读到的是后台进程里的值。

`PreferenceProvider` 的偏好文件名是 `service`。`createSharedPreferencesFromContext`：上下文是 `BaseService` 或 `TunService` 时直接 `getSharedPreferences`；其他上下文用 `MultiProcessPreference` 指向 `Authorities.SETTINGS_PROVIDER`。`RemoteService` 继承 `BaseService`，所以它进程内新建的 `ServiceStore` 走本地偏好。界面进程里的 `ServiceStore` 走 Provider。字段见 [配置库与 ServiceStore](profiles-store.md)。

`FilesProvider` 把已导入配置暴露成文档树。根只列出 `ImportedDao` 的 UUID，不列出仅存在于 `pending` 的配置。路径由 `Paths.resolve` 解析：`/`、`/<uuid>`、`/<uuid>/config.yaml`、`/<uuid>/providers` 以及 providers 下的相对路径。可写打开会先把该 UUID 从 `imported` 克隆进 `pending`。类型是 `Url` 的 `config.yaml` 拒绝写入。文档标志与目录的对应关系见 [配置库与 ServiceStore](profiles-store.md)。

## 远程接口

`remote/` 下的接口标了 `@BinderInterface`。`service/build.gradle.kts` 用 `ksp(libs.kaidl.compiler)` 生成 `wrap()` 和 `IBinder.unwrap`。`IRemoteService` 只有两个方法：

| 方法 | 返回 |
|:-----|:-----|
| `clash()` | `IClashManager` |
| `profile()` | `IProfileManager` |

`IClashManager` 由 `ClashManager` 实现，调用同一进程里的 `core` 的 `Clash`：

| 方法 | 行为 |
|:-----|:-----|
| `queryTunnelState` / `queryTrafficNow` / `queryTrafficTotal` / `queryProxyGroupNames` / `queryProxyGroup` / `queryConfiguration` / `queryProviders` | 读隧道、当前速率、累计流量、策略组和 Provider |
| `patchSelector` | `Clash.patchSelector`；成功则把当前 `activeProfile` 的选择写入 `selections`，失败则删掉该组的已存选择 |
| `healthCheck` / `updateProvider` | 等待 `Clash` 对应的延迟结果 |
| `queryOverride` / `patchOverride` / `clearOverride` | 读写 `Clash.OverrideSlot`。只有 `patchOverride` 会 `sendOverrideChanged()` |
| `setLogObserver` | `Clash.subscribeLogcat()`，把 `LogMessage` 交给 `ILogObserver.newItem`。观察者置空时取消订阅并 `Clash.forceGc()` |

`IProfileManager` 由 `ProfileManager` 实现。`create`、`clone`、`patch`、`commit`、`release`、`delete`、`update`、`queryByUUID`、`queryAll`、`queryActive`、`setActive` 的落盘方式见 [配置库与 ServiceStore](profiles-store.md)。`webdavPerform` 把端点和动作交给 `WebdavSync.perform`，进度经 `IWebdavObserver.onProgress` 回传。默认地址和同步规则不在这里写，常量在 `WebdavProtocol.kt`，行为见 [WebDAV](../../docs/systems/webdav/INDEX.md)。

另外两个回调接口：`IFetchObserver.updateStatus(FetchStatus)`，`ILogObserver.newItem(LogMessage)`。
