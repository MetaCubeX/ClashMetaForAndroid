---
title: "审查方案 r02 service"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 5、drop 0、weaken 0"
---

# 审查方案 r02 service

hold 5、drop 0、weaken 0。

## r02-1

- 问题类：按订阅地址套用 Url 或 External 墓碑时，会删掉同源的 File 配置。
- 不变量：地址匹配只作用于类型为 Url 或 External 的配置。File 只在墓碑 uuid 与自己的 uuid 相同时才算命中。
- 要动的符号或文件：`matchesTombstone`（`service/src/main/java/com/github/kr328/clash/service/webdav/WebdavMerge.kt`，实际读到 181–187 行）。地址比较之前要求 `profile.type` 也是 `Url` 或 `External`。现有逻辑在墓碑 `type` 非空时只用墓碑类型，不看配置自己的类型。克隆保留地址的位置在 `ProfileManager.clone`（76–87 行），删除写入墓碑在 `ProfileManager.delete`（150–157 行）和 `WebdavAutoSync.noteDeleted`（29–40 行）。
- 明确不改什么：uuid 相同仍命中。`subscriptionIdentity`、`tombstoneKey`、`noteDeleted` 的时间戳、`WebdavProfiles.apply` 的删除循环都不改。
- 怎么本地验证：保留 `WebdavSyncTest.tombstoneRemovesMatchingSubscriptionOnEitherUuid`、`newerProfileDropsTombstone`、`expiredTombstoneIsDropped`。新增断言：File 配置与 Url 墓碑地址相同、uuid 不同、`updatedAt` 早于 `deletedAt` 时仍留在合并结果里；同地址的 Url 配置仍被去掉。

## r02-2

- 问题类：策略文件读失败被当成空列表，合并结果里没有这些策略时会整文件写回。
- 不变量：文件不存在才是空列表。文件存在但读或解析失败时，本次同步不导出空策略，也不调用写入。成功写入先写临时文件再替换，替换失败时原文件保持原样。
- 要动的符号或文件：`ServiceStore.appsStrategyConfigs` 的 getter 和 setter（实际读到 81–103 行）；`WebdavProfiles.exportStrategies` 与 `applyStrategies`（写回在 302 行，移除循环在 292–301 行）。写入方式对齐同模块 `WebdavSettingsStore.write` 的临时文件再 `renameTo`。
- 明确不改什么：`mergeStrategies` 的新旧与墓碑规则、访问控制、`TunService` 只读查找。不把读失败变成删远端策略。
- 怎么本地验证：没有现成测试。新增断言：文件内容不是合法 JSON 时，同步写回之后字节与读失败前相同；合法列表写入后读回一致，且可见的是替换后的正式文件名。已有 `WebdavSyncTest.newerAppGroupReplacesPackagesWithoutRemovingTheOtherGroup` 仍通过。

## r02-3

- 问题类：订阅更新失败后，这一轮闹钟已经消费掉，进程还活着时不会再登记下一次。
- 不变量：间隔不少于 15 分钟且 `config.yaml` 仍在时，失败也要留下下一次 RTC 闹钟，触发时刻不早于当前时间加完整间隔。
- 要动的符号或文件：`ProfileWorker.run` 的 `catch`（82–95 行）。在这里登记下一次闹钟。不要直接调用 `ProfileReceiver.scheduleNext`（79–101 行）：它用 `config.yaml` 的 `lastModified` 算剩余时间，失败时文件时间不变，剩余会变成 0，闹钟会立刻再响。成功路径仍用 `scheduleNext`。
- 明确不改什么：`schedule` 的立即触发、`rescheduleAll` 每个进程只做一次（51–55 行，由 `ProfileManager` 初始化调用）、间隔不足 15 分钟或文件不存在时不排程、File 类型不参与自动更新。
- 怎么本地验证：没有现成测试。新增断言：更新抛异常后仍登记一次闹钟，时间不早于现在加 `interval`；成功后仍按文件时间计算剩余间隔。

## r02-4

- 问题类：订阅正文没变、只改了用量时，同步比较会认为配置没变，用量到不了另一台设备。
- 不变量：`upload`、`download`、`total`、`expire` 任一变化，都要上传并在对端写进数据库。导出的 `updatedAt` 必须晚于 `LocalState.configUpdatedAt` 里该配置已记下的值，避免 `mergeProfile` 在时间相同时留住旧用量再传回去。
- 要动的符号或文件：`WebdavProfiles.exportLocal` 冻结 `updatedAt` 的分支（43–47 行）；`sameContent`（328–337 行）；`WebdavSync.sameRemote`（286–294 行）。四字段目前不在 `LocalState` 里，冻结判断要能看见用量变化。`mergeProfile`（`WebdavMerge.kt` 171–178 行）在 `updatedAt` 前进之后会选中新的一份，不必另写一套用量合并。
- 明确不改什么：文件正文指纹的计算方式、墓碑、访问控制和策略同步。
- 怎么本地验证：保留 `WebdavSyncTest.sameSubscriptionKeepsLocalUuidAndNewerConfig`。新增断言：两份载荷文件相同且原 `updatedAt` 相同、四字段不同时，导出或合并结果带上新用量，并且比较结果为不相等。

## r02-5

- 问题类：提交配置时没有订阅流量头，会把已用流量、总量和到期时间写成 0。
- 不变量：本次有 `subUpload` 时用流量头覆盖四字段。没有流量头时，已有 `Imported` 行保持原四字段；没有旧行时使用 pending 里的四字段。
- 要动的符号或文件：`ProfileProcessor.apply` 构造 `Imported` 的四个用量参数（实际读到 63–75 行，强制下载标志在 51 行）。克隆抄用量在 `ProfileManager.clone`（81–86 行），提交时目前没有读这些字段。
- 明确不改什么：`ProfileProcessor.update` 在 `subUpload == null` 时不改列（119–128 行）。不改 File 的 `force = false`，不改更新间隔和 `createdAt` 的取值。不改 `ProfileManager.patch` 把 pending 用量写成 0；已导入配置再提交时以旧 `Imported` 行为准。
- 怎么本地验证：没有现成测试。新增断言：File 提交且没有 `SubscriptionInfo` 时，新行四字段等于 pending；已有行再提交且没有流量头时四字段等于旧行；有 `subUpload` 时四字段等于流量头。
