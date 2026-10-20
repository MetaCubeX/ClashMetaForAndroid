---
title: "配置生命周期"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-15
summary: "从导入和待提交，到提交成已导入、按间隔更新，再把当前配置与本地覆盖送进内核。"
---

# 配置生命周期

一份配置先以 `Pending` 存在。`ProfileProcessor.apply` 在快照仍匹配时把它写成 `Imported`。当前选中的已导入配置，加上本地覆盖，才进入内核。

表、列和目录不在这里抄。见 [配置库与 ServiceStore](../../../service/docs/profiles-store.md)。界面逐屏见 [app 的 Activity](../../../app/docs/activities.md)。界面经 `withProfile` 调 `IProfileManager`，见 [app 怎样调用 :background](../../../app/docs/remote.md)。

## 导入入口

`ProfilesActivity` 进入前台或收到配置变更时 `queryAll()`，把 `Pending` 和 `Imported` 合成列表。新建请求打开 `NewProfileActivity`。未导入的配置不能直接设为当前：`ProfilesDesign.requestSave` 提示先去编辑。

`NewProfileActivity` 调用 `ProfileManager.create`。`create` 用 `generateProfileUUID()` 插入一行 `Pending`，并建好 `pending/<uuid>/config.yaml` 和 `providers/`。此时还没有 `Imported`。

| 入口 | 类型 | 接着做什么 |
|:-----|:-----|:-----------|
| 文件 | `Profile.Type.File` | 打开 `PropertiesActivity`。正文由下面的文件导入写入 |
| URL | `Profile.Type.Url` | 打开 `PropertiesActivity`，来源留到属性页再填 |
| 二维码 | `Profile.Type.Url` | 把扫到的字符串当作 `source`，再打开属性页 |
| 外部提供者 | `Profile.Type.External` | 对方返回的 URI 当作 `source`，再打开属性页 |

文件导入发生在 `FilesActivity`。它用 `GetContent` 选取文件，再经 `FilesClient.importDocument` 或 `copyDocument` 写入 `FilesProvider`。可写打开时，若还没有 `Pending` 且已有 `Imported`，`Picker.cloneToPending` 先复制目录并插入 `Pending`。`create` 已经插入过 `Pending` 时，这一步直接返回。根上的 `config.yaml` 只在类型不是 `Url` 时带可写标志。URL 配置的正文靠提交时下载，不靠这次导入。

`clash://install-config` 和 `clashmeta://install-config` 进 `ExternalControlActivity` 的 `ACTION_VIEW`。链接必须带 `url`。`type=file` 创建 `Profile.Type.File`，`type=url` 或其他值创建 `Profile.Type.Url`。名称缺省为新配置。`update-interval` 按分钟解析：大于 0 时至少 15，否则为 0，再换成毫秒。`create` 之后立刻 `patch` 名称、来源和间隔，然后打开 `PropertiesActivity`。启动、停止和切换不在这里写，见 [app 的 Activity](../../../app/docs/activities.md) 和 [app 怎样调用 :background](../../../app/docs/remote.md)。

复制走 `ProfileManager.clone`。它只接受已导入 UUID，新行类型固定为 `File`，文件从 `imported/` 拷到新的 `pending/`。

## 从 Pending 到 Imported

`patch` 改的是 `Pending`。还没有待提交行时，先把已导入目录复制到同 UUID 的 `pending/` 再插入。同一 UUID 可以同时有两行。`resolveProfile` 的名称、类型、来源、间隔和流量优先用 `Pending`。`Profile.imported` 表示已导入行在不在。`Profile.pending` 表示待提交行在不在。

`PropertiesActivity` 在尚未取消、且字段相对进入时有变化的 `ActivityStop` 上调用 `patch`。用户点提交时先再 `patch` 一次，再 `commit`。`finish()` 先跑 `defer`：把取消标记设上，并 `release`。因此成功提交后的停止事件不会再 `patch`。`release` 只删 `Pending` 和 `pending/` 目录，不动已导入内容。

`ProfileManager.commit` 调用 `ProfileProcessor.apply`。`apply` 没抛异常就 `scheduleUpdate(uuid, false)`。没有 `Imported` 行时，这次排程直接返回。`apply` 与更新共用 `processLock`，读写行时再用 `profileLock`：

1. 读出 `Pending`。没有这行就抛 `IllegalArgumentException`。
2. `Pending.enforceFieldValid` 检查名称、来源和间隔。名字空白、非文件型来源为空、来源非空且 scheme 不是 `https` / `http` / `content`、或间隔非 0 且小于 15 分钟，都拒绝。
3. 把 `pending/<uuid>/` 复制到 `processing/`。
4. `Clash.setAgeSecretKey` 使用快照里的密钥；空白当成不设。
5. 类型不是 `File` 时 `force` 为真。`Clash.fetchAndValid` 拉取并校验 `processing/`。`config.yaml` 已存在且 `force` 为假时不重新下载。
6. `FetchStatus.Action.SubscriptionInfo` 不交给界面。其他状态调用 `IFetchObserver.updateStatus`。回调抛异常后不再继续回调。`ProfileWorker` 更新时传入的观察者是 null。
7. 回到锁里。这行 `Pending` 已经不是刚才的快照时，放弃提交，不改 `imported/`，也不删 `Pending`。
8. 否则用 `processing/` 换掉 `imported/<uuid>/`，插入或更新 `Imported`，删掉 `Pending` 和 `pending/` 目录，再广播 `ACTION_PROFILE_CHANGED`。

订阅响应头里的更新间隔只在「原先没有 `Imported`，且待提交间隔是 0」时写入新行。已有行保留原来的 `createdAt`。拉取或校验失败时 `apply` 抛异常，`commit` 不会排下一次更新。

## 更新调度

更新间隔是毫秒，存在 `Pending.interval`，提交后存在 `Imported.interval`。属性页的 `PropertiesDesign.inputInterval` 按分钟编辑。`ValidatorAutoUpdateInterval` 接受空串或大于等于 15 的整数。文件类型的这一项在界面上不可用。订阅头 `profile-update-interval` 按小时解析，见 [golang/native 包布局](../../../core/docs/native-layout.md)。

`ProfileReceiver.scheduleNext` 先取消该 UUID 的闹钟。间隔小于 15 分钟，或 `imported/<uuid>/config.yaml` 的 `lastModified` 小于 0，就不再设定。否则用 `AlarmManager.RTC`，触发点是现在加上「间隔减去文件年龄」，并且不小于 0。闹钟动作是 `Intents.ACTION_PROFILE_REQUEST_UPDATE`，数据是 `uuid:` 形式的该 UUID。

到点后 `ProfileReceiver` 把这个 Intent 交给 `ProfileWorker`。`IProfileManager.update` 不直接拉配置。它经 `scheduleUpdate(uuid, true)` 调用 `ProfileReceiver.schedule`，取消旧闹钟并立刻发出同一个广播。`ProfilesActivity` 的单个更新就是这条路径。全部更新只对已导入、且类型不是 `Profile.Type.File` 的配置调用 `update`。

`ProfileWorker` 处理两个动作：

- `ACTION_PROFILE_REQUEST_UPDATE`：为该 UUID 调用 `ProfileProcessor.update`。
- `ACTION_PROFILE_SCHEDULE_UPDATES`：调用 `ProfileReceiver.rescheduleAll`，再延迟 30 秒。开机、覆盖安装、时区变化和系统时间变化会清掉进程内的 `initialized` 标记，再以前台服务启动这个动作。

`ProfileManager` 初始化时也直接调用 `rescheduleAll`。`initialized` 已经为真则直接返回。它只给类型不是 `File` 的已导入行排下一次。

`onStartCommand` 返回 `START_NOT_STICKY`。服务自己的循环先延迟 10 秒，再按顺序 `join` 已入队任务，队列空了就 `stopSelf()`。

`ProfileProcessor.update` 从已导入目录复制到 `processing/`，`force` 恒为真。提交时若 `Imported` 还在，就替换目录。只有 `subscriptionInfo.subUpload` 非空才回写 `upload`、`download`、`total`、`expire`。它不改名字、类型、来源和间隔。成功后 `ProfileWorker` 发结果通知和 `ACTION_PROFILE_UPDATE_COMPLETED`，再 `scheduleNext`。失败发 `ACTION_PROFILE_UPDATE_FAILED`，不再排下一次。服务形态见 [进程与远程接口](../../../service/docs/process.md)。

## 当前配置与覆盖进内核

`ProfilesActivity` 只对 `Profile.imported` 为真的项调用 `setActive`。`ProfileProcessor.active` 再确认 `ImportedDao.exists`，然后把 UUID 写入 `ServiceStore.activeProfile`，并广播 `ACTION_PROFILE_CHANGED`。`queryActive` 在这个 UUID 没有已导入行时返回 null。服务没在跑时，这里只记下选择，不加载内核。

`ClashService` 和 `TunService` 安装 `ConfigurationModule`。它启动时加载一次，并监听 `ACTION_PROFILE_CHANGED` 与 `ACTION_OVERRIDE_CHANGED`。模块循环的其余分支见 [ClashRuntime 与模块](../../../service/docs/runtime.md)。加载时：

1. 读取 `ServiceStore.activeProfile`。没有，或 `ImportedDao` 查不到，就失败。
2. `Clash.setAgeSecretKey` 使用已导入行的密钥。空白当成不设。
3. `Clash.load` 读取 `imported/<uuid>/` 并等待完成。
4. 读出该 UUID 的 `Selection`。`Clash.patchSelector` 拒绝的组从 `SelectionDao` 删掉。

`Clash.load` 进到 Go 的 `Load`。`UnmarshalAndPatch` 读该目录的 `config.yaml`，`patchOverride` 先解码持久覆盖，再解码会话覆盖，然后内核应用这份配置。解码失败只打日志。覆盖字段不在这里列，见 [core 桥接边界](../../../core/docs/bridge.md)。槽的落盘和套用顺序见 [golang/native 包布局](../../../core/docs/native-layout.md)。

持久覆盖是 `Clash.OverrideSlot.Persist`。`OverrideSettingsActivity` 和 `MetaFeatureSettingsActivity` 读出它，离开时经 `ClashManager.patchOverride` 写回。`patchOverride` 调用 `Clash.patchOverride`，并 `sendOverrideChanged()`。会话覆盖是 `Clash.OverrideSlot.Session`，只在内存里。`MainActivity` 和 `ProxyActivity` 用它改模式。`clashRuntime` 在启动和结束时调用 `Clash.clearOverride`，清掉这个槽。持久文件在内核 home 下，服务重启后仍然参加下一次 `Clash.load`。会话覆盖不跨这次运行时。

## WebDAV

`ProfileManager.commit` 在 `apply` 返回后调用 `WebdavAutoSync.enqueue`，`ProfileProcessor.update` 在锁外也调用它；配置的拉取和推送见 [WebDAV](../webdav/INDEX.md)。
