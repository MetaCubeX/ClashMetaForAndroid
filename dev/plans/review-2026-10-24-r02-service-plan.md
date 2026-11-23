---
title: "审查方案 r02 service"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 5、drop 0、weaken 0"
---

# 审查方案 r02 service

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 service/ 活码。结论：hold 5、drop 0、weaken 0。

drop：无。

## r02-1

**结论：** hold。严重度维持高。`WebdavSync.download` 的跳过循环仍在 148–175 行；`profileBodyFingerprint` 仍在 `WebdavDecision.kt` 71–79 行，不含上传、下载、总量、到期。`mergeWebdav` 仍只在远端配置对象非空时读取 `remoteUsage`（`WebdavMerge.kt` 65–73 行，其中 67–73 行是这道门）。`upload` 仍用这份指纹决定要不要上传配置文件（214–218 行），meta 不同就 `putMeta` 写回（253–258 行）。拉取本身不上传（103–105 行）；同一次 sync，或之后的 sync / push，会把合并结果里的旧计数写回。

**问题类：** etag 未变而跳过配置下载时，合并用不到 meta 里更新的流量计数，随后的 sync 或 push 会把本地旧计数写回。

**不变量：** 因 etag 未变而没有放进 `remote.profiles`、本地仍存在的配置，其 `meta.profileUsage` 里同 uuid 的记录仍进入现有的 `preferUsage`。写出的 meta 使用合并后的计数，不用更旧的本地计数覆盖更新的远端计数。配置正文指纹继续不含流量计数。

**要动的符号或文件：** `mergeWebdav`（`WebdavMerge.kt` 65–73 行）在远端配置对象缺失时，把 `remoteUsage` 中同 uuid 的记录放进 candidates。`WebdavSync.upload` 的 `metaFrom` / `putMeta`（253–258 行）随之写出合并后的用量。`download` 148–157 行可以继续不下载正文。

**明确不改什么：** `profileBodyFingerprint`、`shouldSkipProfileGet` 的 etag 匹配条件（`WebdavDecision.kt` 145–153 行）、`usageRank` / `preferUsage`（105–107 行）、冲突路径上已有的 `mergeUsageLists`（`WebdavMerge.kt` 194–201 行，由 `WebdavSync.putMeta` 427–429 行调用）。uuid 不同但订阅身份相同的 alias 分支保持原样，那条路径要求两边配置正文都在。

**怎么本地验证：** 已有测试保持通过：`WebdavSyncTest.usageCountersStayOutOfTheUploadFingerprint`、`unchangedEtagSkipsTheDownloadOnlyWhenTheLocalCopyIsKnown`、`newerUsageDoesNotReplaceTheConfigurationBody`、`explicitUsageBeatsStaleCountersInANewerProfileFile`。新增断言：本地有该配置、`remoteProfiles` 为空、`remoteUsage` 同 uuid 且 `updatedAt` 更新时，`mergeWebdav` 的 `profileUsage` 与配置上的计数取这份远端用量；远端用量更旧时仍取本地。

## r02-2

**结论：** hold。严重度维持高。`ProfileProcessor.update` 仍在 138–139 行先 `deleteRecursively` 已导入目录，再 `copyRecursively`，返回值未使用。提交路径仍是同一顺序（56–57 行）。下一次 `update` 仍在 122–123 行先清空 `processingDir`。`copyRecursively` 默认出错会抛出，抛出前已导入目录已经删掉。`WebdavProfiles.readFiles` 对空目录返回空文件列表，`exportLocal` 仍会把内容哈希变化记成新修订。

**问题类：** 更新和提交配置时先删掉已导入目录再复制回去，中断后已提交文件会丢，下一次更新还会把 processing 目录里剩下的副本清掉。

**不变量：** 新树完整落地之前，已导入目录保持替换前的文件。复制、替换或进程中断失败时，数据库行仍指向这份旧树。下一次 `update` 开头清空 `processingDir` 时，已提交内容仍在已导入目录里。

**要动的符号或文件：** `ProfileProcessor.apply`（56–57 行）和 `ProfileProcessor.update`（138–139 行）。新内容先写入临时目录，确认完整后再替换已导入目录。

**明确不改什么：** `WebdavProfiles.install` 已有的临时目录加 `renameTo`（264–290 行）。`fetchProfile` 以及锁外下载、锁内提交的划分。`update` 122–123 行对 `processingDir` 的清空。`exportLocal` 的修订计算。`ProfileProcessor.delete` 里故意删除已导入目录的路径。

**怎么本地验证：** service 测试里没有覆盖这次替换。新增断言：替换完成前已导入目录仍是旧树；写入临时目录失败，或替换尚未完成时，旧文件仍在；此时间点之后若执行 `update` 开头的 `processingDir.deleteRecursively`，已提交文件仍可从已导入目录读出。用临时目录即可，不必调用 `Clash.fetchAndValid`。

## r02-3

**结论：** hold。严重度维持中。`ServiceStore.migrateLegacyAccessControl` 184–195 行只写入策略列表和当前项，不改 `activeStrategySyncRev`。`WebdavProfiles.applyLegacyAccess` 351 行在 `legacyAccessControlMigrated` 已为真时直接返回，350 行构造 `ServiceStore` 时迁移已经把该标记写上，370 与 382 行的 `noteActiveAppsStrategy` 走不到。`exportActiveStrategy` 仍在 414–425 行把 0 时间戳写成当前时间。`exportLocal` 169 行仍把修订设为原来的 `local.activeStrategySyncRev`。`pickActive` 仍在 `WebdavMerge.kt` 280–284 行用 `syncRankCompare`：修订相同就比时间，时间和写入者再相同就比策略 id（23–28 行）。`applyActiveStrategy` 512–513 行在远端名次更高时接受这个当前项。

**问题类：** 旧访问控制迁移不推进当前策略修订，首次导出却把 0 时间戳写成现在，修订同为 0 时后同步的设备会换掉对方正在用的应用策略。

**不变量：** 用户没有改选当前策略时，不得把当前项换成另一台设备的策略。禁止靠补写“现在”制造时间差。修订和时间都仍是 0 时，也不得改用策略 id 的字典序分出胜负。只有用户真正改选才推进修订和时间，更高修订仍按现有顺序胜出。

**要动的符号或文件：** `WebdavProfiles.exportActiveStrategy`（414–425 行）在时间戳为 0 时不要写成现在。`WebdavMerge.pickActive`（268–285 行）在修订与时间都没有拉开差距时，不要用策略 id 让另一侧获胜；只停掉补时，id 比较仍会替换当前项。`ServiceStore.migrateLegacyAccessControl`（184–195 行）与 `applyLegacyAccess` 的提前返回保持“迁移不补记修订”。

**明确不改什么：** `WebdavAutoSync.noteActiveAppsStrategy`（76–86 行）对用户改选做修订加一。`syncRankCompare` 里“修订优先于时间”的顺序。`applyActiveStrategy` 在远端修订确实更高时写回当前项（512–513 行）。`exportStrategies` 里单个策略正文的时间戳。

**怎么本地验证：** 现有 `WebdavSyncTest.newerAppGroupReplacesPackagesWithoutRemovingTheOtherGroup` 与 `AccessControlSelectionTest` 不覆盖当前项的 0 修订。新增断言：两侧 `activeAppsStrategySyncRev` 都是 0、策略 uuid 不同、只有一侧 `updatedAt` 非 0 时，`mergeWebdav` 的 `activeAppsStrategy` 保持调用方本地的当前项；两侧时间都是 0、id 字典序不同时同样保持本地。另一侧修订更高时，当前项改为修订更高的一侧。

## r02-4

**结论：** hold。严重度维持中。`WebdavProfiles.applyStrategies` 仍在 492–493 行写入策略列表并调用 `applyActiveStrategy`，这里没有广播。`TunService` 的 `TunModule.open` 仍在 172–180 行读取当前策略并填入允许或拒绝名单；该函数在建立隧道（50 行）和 `reloadTun`（256–258 行）时执行。`ReloadModule` 仍只监听 `ACTION_CLASH_REQUEST_RELOAD`（9–14 行）。service 内发出该广播的只有 `McpBridge.reload`（137 行）。`ConfigurationModule` 监听的是配置与覆盖变更，重载的是 Clash 配置，不重建 VPN 名单。

**问题类：** WebDAV 写下新的应用策略或当前项之后不重建隧道，VPN 仍按旧的允许或拒绝名单转发。

**不变量：** 正在运行的 VPN 所允许或拒绝的应用集合，与刚写入的当前策略一致。生效名单或当前项没有变化时，不要求重建。

**要动的符号或文件：** `WebdavProfiles.applyStrategies`（492–493 行）及其调用的 `applyActiveStrategy`（497–521 行）。生效集合变化时发出 `ReloadModule` 已在监听的 `ACTION_CLASH_REQUEST_RELOAD`，让 `TunService.reloadTun` 再执行 `TunModule.open`。

**明确不改什么：** `ReloadModule` 的动作集合。`TunModule.open` 里 `resolveAccessControl` 与 `addAllowedApplication` / `addDisallowedApplication` 的填法（172–194 行）。`ConfigurationModule` 对 `ACTION_PROFILE_CHANGED` 的配置重载。`WebdavSync.runOnce` 里 `ACTION_PUSH` 不调用 `apply`（96–98 行）。

**怎么本地验证：** 没有现成测试。新增断言：当前策略的模式、包名集合或当前 uuid 变化时，apply 路径要求一次隧道重载；三者都不变时不要求。重载信号使用 `Intents.ACTION_CLASH_REQUEST_RELOAD`。`AccessControlSelectionTest.activeStrategyOverridesGlobalList` 继续通过，用来固定“只认当前策略”的生效集合。

## r02-5

**结论：** hold。严重度维持中。`McpBridge.start` 仍在 140–150 行用 `getSharedPreferences("ui", MODE_PRIVATE)` 读取 `enable_vpn`，缺省为 true，再启动 `TunService` 或 `ClashService`。发现稿里的异常文案 “ClashMetaX” 现为 “AgenticClash”（145 行），选择逻辑未变。`McpService` 仍在 `AndroidManifest.xml` 44 行运行于 `:background`。service 内没有 `enable_vpn` 的写入。`PreferenceProvider` 17–29 行的跨进程通道对应文件名 `service`（15 行），`start` 不走这条通道。本仓库 `targetSdk` 为 35，`MODE_PRIVATE` 的再次 `getSharedPreferences` 不会因为其他进程改了文件而重载内存缓存。

**问题类：** 常驻 MCP 用本进程的私有偏好缓存读 VPN 开关，界面改过之后 start 仍按旧值启动 `TunService` 或 `ClashService`。

**不变量：** 每次 `start` 读到的模式等于其他进程已经写入的当前值。后台进程第一次读到的值不能留到下一次 `start`。键尚不存在时的默认 true 只表示存储里确实没有这个键。

**要动的符号或文件：** `McpBridge.start`（140–150 行）对 `ui` / `enable_vpn` 的读取。读取结果必须在每次 `start` 看到最新写入。

**明确不改什么：** `PreferenceProvider` 的 `service` 偏好文件，以及 `BaseService` / `TunService` 走进程内 `MODE_PRIVATE`、其他上下文走 `MultiProcessPreference` 的分支（17–29 行）。`TunService` 与 `ClashService` 的内部实现。`McpBridge.reload` 与 `stop` 使用的广播。

**怎么本地验证：** `McpProtocolTest` 与 `McpHttpTest` 没有覆盖 `start`。新增断言：模式值为 false 时选择 `ClashService`，为 true 时选择 `TunService`；同一读取方先看到 true、存储改为 false 之后，下一次选择必须是 `ClashService`。断言针对每次读取的结果，不必启动前台服务。
