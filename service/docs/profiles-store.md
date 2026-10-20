---
title: "配置库与 ServiceStore"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-15
summary: "Room 的 imported、pending、selections，以及 ServiceStore 的偏好键和策略文件"
---

# 配置库与 ServiceStore

配置的界面流程和「当前选中哪一份」见 [配置](../../docs/systems/profiles/INDEX.md)。分流策略的点选和离开页面时的重载见 [访问控制](../../docs/systems/access-control/INDEX.md)。这里只写 `service` 里实际落盘的实体、目录和偏好。

## Room

`Database` 是 Room 数据库，文件名 `profiles`，`version = 2`，`exportSchema = false`。实体是 `Imported`、`Pending`、`Selection`。打开时加上 `MIGRATIONS`。实例用 `SoftReference` 缓存，锁在 `database` 的 getter 上。类初始化时在 `Dispatchers.IO` 上跑 `LEGACY_MIGRATION`。

`MIGRATION_1_2` 为 `imported` 和 `pending` 各增加可空列 `ageSecretKey TEXT`。

`Converters` 把 `UUID` 存成 `toString()`，把 `Profile.Type` 存成枚举名。

### imported

表名 `imported`，主键 `uuid`。`ImportedDao.insert` 冲突策略是 `ABORT`。

| 列 | 类型 |
|:---|:-----|
| `uuid` | `UUID` |
| `name` | `String` |
| `type` | `Profile.Type` |
| `source` | `String` |
| `interval` | `Long`，毫秒 |
| `upload`、`download`、`total`、`expire` | `Long` |
| `createdAt` | `Long` |
| `ageSecretKey` | `String?` |

查询按 `createdAt` 列出 UUID。`remove` 按 UUID 删除。

### pending

表名 `pending`，主键 `uuid`。列与 `imported` 相同。`createdAt` 默认 `System.currentTimeMillis()`。`insert` 和 `update` 的冲突策略是 `REPLACE`。同一 UUID 可以同时有已导入行和待提交行；待提交行在 `commit` 成功后删除。

### selections

表名 `selections`，主键 `(uuid, proxy)`。列是 `uuid`、`proxy`、`selected`。外键指向 `imported.uuid`，删除和更新都是 `CASCADE`。

`SelectionDao.setSelected` 使用 `REPLACE`。`ClashManager.patchSelector` 先调用 `Clash.patchSelector`。没有 `activeProfile` 时不改这张表。有当前配置时，成功则 `setSelected`，失败则 `removeSelected`。`ConfigurationModule` 加载后，把核心拒绝的组从这张表删掉。

### 旧库

`context.getDatabasePath("clash-config")` 不存在就返回。存在则按 SQLite `version` 读取。`try` 之后无论是否迁入，都会 `deleteDatabase("clash-config")`。版本不是 1、2、3、4 时不插入行：

- 版本 1：表 `profiles` 的 `name`、`token`、`id`、`file`。`token` 以 `file|` 开头成为 `Profile.Type.File`，以 `url|` 开头成为 `Url`，其余跳过。文件型把 `file` 列指向的文件复制为 `pending/<新 UUID>/config.yaml`，然后删除该旧文件。
- 版本 2、3、4：`type` 的 1、2、3 分别是 `File`、`Url`、`External`。版本 2 的间隔列名是 `update_interval`，写入前乘 1000；3 和 4 用 `interval`。文件型复制 `filesDir/profiles/<id>.yaml`。迁完后删除 `filesDir/profiles`，以及 `filesDir/clash` 下名字全是数字的子项。

迁入的都是新的 `Pending` 行，并发送 `ACTION_PROFILE_CHANGED`。

## 目录

`util/Files.kt`：

| 路径 | 谁写入 |
|:-----|:-------|
| `filesDir/imported/<uuid>/` | `ProfileProcessor.apply` 和 `update`。内有 `config.yaml` 和 `providers/` |
| `filesDir/pending/<uuid>/` | `create`、`clone`、`patch`，以及 `FilesProvider` 的可写打开 |
| `filesDir/processing/` | `apply` 和 `update` 的临时整目录副本，没有 UUID 子目录 |

`Profile.updatedAt` 不是表列。`resolveUpdatedAt` 先取 `pending/<uuid>` 目录树的最大 `lastModified`，没有再取 `imported/<uuid>`，都没有则是 -1。

`create` 用 `generateProfileUUID()`（避开已存在的 imported 和 pending UUID），插入 `Pending`，并建空的 `config.yaml` 与 `providers/`。`interval`、`upload`、`download`、`total`、`expire` 初始为 0。

`clone` 只接受已导入 UUID，新 UUID 的类型固定为 `File`，把 `imported` 目录复制到 `pending`。

`patch` 若还没有 `Pending`，先把已导入目录复制到同 UUID 的 `pending` 再插入；`upload`、`download`、`total`、`expire` 写成 0。已有 `Pending` 则只更新名字、来源、间隔和 `ageSecretKey`，这四列同样清 0。

`release` 删 `Pending` 行和 `pending/<uuid>`，不动已导入目录。

`delete` 在仍有已导入行时，用该目录下文件的最大 `lastModified` 调用 `WebdavAutoSync.noteDeleted`，并 `ProfileReceiver.cancelNext`。然后 `ProfileProcessor.delete`：两张表都删、两个目录都删、若 `activeProfile` 就是它则置空，再广播 `ACTION_PROFILE_CHANGED`。最后 `WebdavAutoSync.enqueue`。

`setActive` 只在 `ImportedDao.exists` 时写 `activeProfile` 并广播变更。

`queryAll` 合并两张表的 UUID。`resolveProfile` 的名字、类型、来源、间隔和流量优先用 `Pending`，没有再用 `Imported`。两边都没有则返回 null。`active` 仅当已导入行的 UUID 等于 `activeProfile`。`imported` / `pending` 两个布尔表示行是否存在。`ageSecretKey` 在有 `Pending` 时取待提交行，否则取已导入行。

`commit` 调用 `ProfileProcessor.apply`，再 `scheduleUpdate(uuid, false)` 和 `WebdavAutoSync.enqueue`。`update` 走 `scheduleUpdate(uuid, true)`。

`ProfileProcessor.apply` 与 `ProfileProcessor.update` 共用 `processLock`，快照和提交再用 `profileLock`。拉取发生在两段 `profileLock` 之间，调用 `Clash.fetchAndValid`；`FetchStatus.Action.SubscriptionInfo` 不回调界面，其他状态交给 `IFetchObserver.updateStatus`，回调抛异常后不再继续回调。`IProfileManager.update` 不直接调用 `ProfileProcessor.update`，而是 `scheduleUpdate(uuid, true)`，由 `ProfileWorker` 再进来。

`apply` 要求 `Pending` 仍在。非 `File` 才把 `force` 设为真。提交时若这行 `Pending` 已经变了就放弃。否则用 `processing` 换掉 `imported/<uuid>`，插入或更新 `Imported`，删掉 `Pending` 和 `pending` 目录。订阅里的更新间隔只在「原先没有 imported 行，且待提交间隔是 0」时采用。已存在的行保留原来的 `createdAt`。

`ProfileProcessor.update` 从已导入目录复制到 `processing`，`force` 恒为真。提交时若 imported 行还在，替换目录；只有 `subscriptionInfo.subUpload` 非空才回写 `upload`、`download`、`total`、`expire`。它不改名字、类型、来源和间隔。锁释放之后才 `WebdavAutoSync.enqueue`。

`Pending.enforceFieldValid` 在 `apply` 的快照阶段执行：名字空白、非文件型来源为空、来源 scheme 不是 `https` / `http` / `content`、或间隔非 0 且小于 15 分钟，都抛 `IllegalArgumentException`。

`ProfileReceiver.schedule` 立刻发出该配置的显式闹钟。`scheduleNext` 先取消闹钟；`interval` 小于 15 分钟，或 `imported/<uuid>/config.yaml` 的 `lastModified` 小于 0，就不再设定。否则用 `AlarmManager.RTC`，触发点是现在加上「间隔减去文件年龄」，并且不小于 0。`rescheduleAll` 在进程内的 `initialized` 已为真时直接返回；开机、换包和对时会先清掉这个标记。它只处理类型不是 `File` 的已导入行。

`FilesProvider` 的根列出已导入 UUID。`config.yaml` 在类型为 `Url` 时没有可写标志，`File` 和 `External` 带 `Writable`。可写打开时，类型不是 `File` 会抛 `IllegalArgumentException("invalid open mode")`。`providers` 目录只有 `Virtual`；其下文件同时有 `Writable` 和 `Deletable`。可写的 `pick` 会先克隆到 `pending`。`openDocument` 要求 `FileDocument`，虚拟节点不能当文件打开。

## ServiceStore

`ServiceStore` 分两处，都不是 Room。

偏好来自 `PreferenceProvider` 的 `service` 文件，经 `common` 的 `Store`。跨进程规则见 [进程与远程接口](process.md)。键和默认值：

| 属性 | 键 | 默认 |
|:-----|:---|:-----|
| `activeProfile` | `active_profile` | 空字符串读成 null，否则 `UUID.fromString` |
| `bypassPrivateNetwork` | `bypass_private_network` | `true` |
| `accessControlMode` | `access_control_mode` | `AccessControlMode.AcceptAll` |
| `accessControlPackages` | `access_control_packages` | 空字符串集合 |
| `dnsHijacking` | `dns_hijacking` | `true` |
| `systemProxy` | `system_proxy` | `true` |
| `allowBypass` | `allow_bypass` | `true` |
| `allowIpv6` | `allow_ipv6` | `false` |
| `tunStackMode` | `tun_stack_mode` | `"mips"` |
| `dynamicNotification` | `dynamic_notification` | `true` |
| `activeAppsStrategyConfigUuid` | `active_apps_strategy_config_uuid` | 与 `activeProfile` 相同的空串规则 |

`AccessControlMode` 的枚举值是 `AcceptAll`、`AcceptSelected`、`DenySelected`。`store.enum` 使用 `AccessControlMode.values()`。

`appsStrategyConfigs` 不在偏好里。文件是 `filesDir/settings/app_lists_configs.json`，构造时会 `mkdirs()`。JSON 使用 `ignoreUnknownKeys` 和 `prettyPrint`。内容是 `AppsStrategyConfig` 列表：`uuid`、`name`、`mode`、`packages`。文件不存在或解码抛异常时，getter 返回空列表，不改文件。`AppsStrategyConfig.create` 用 `UUID.randomUUID()` 生成 UUID。

WebDAV 设置不在 `ServiceStore`。`WebdavSettingsStore` 读写 `filesDir/webdav/settings.json`。默认常量在 `WebdavProtocol.kt`，不在这里抄写。行为见 [WebDAV](../../docs/systems/webdav/INDEX.md)。

`Profile` 是给 `IProfileManager` 的模型，不是 Room 实体。除上面合并出来的字段外，还有 `Type`：`File`、`Url`、`External`。它实现 `Parcelable`，序列化走 `Parcelizer`。
