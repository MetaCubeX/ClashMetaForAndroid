---
title: "审查发现 r02 service"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条；最严重的是配置文件 etag 未变时同步丢掉远端流量统计，并可能用本地旧值写回。"
---

# 审查发现 r02 service

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖 service/。下面 5 条都来自这次读到的源码。

## r02-1
- 严重度：高
- 症状：用户可见的错误行为
- 代码：
```148:175:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavSync.kt
        for (entry in files) {
            val name = "$WEBDAV_PROFILES_DIR/${entry.name}"
            val uuid = profileUuidFromFileName(name)
            val cachedFp = cache.pushedFingerprint[name]
            if (shouldSkipProfileGet(entry.etag, cache.remoteEtags[name], cachedFp, uuid != null && uuid in localIds)) {
                etags[name] = entry.etag
                if (!cachedFp.isNullOrBlank()) fingerprints[name] = cachedFp
            } else {
                toFetch.add(entry)
            }
        }
        progressFloor.set(0)
        val fetched = fetchProfiles(config, toFetch)
        val profiles = mutableListOf<ProfilePayload>()
        val unreadable = mutableSetOf<String>()
        for (item in fetched) {
            if (item.absent) continue
            etags[item.name] = item.etag
            when (val read = item.read) {
                ProfileBodyRead.Unreadable -> unreadable.add(item.name)
                is ProfileBodyRead.Ok -> {
                    profiles.add(read.profile)
                    fingerprints[item.name] = read.fingerprint
                }
                null -> unreadable.add(item.name)
            }
        }
        return RemoteBundle(profiles, readMeta.meta, etags, unreadable, fingerprints)
```
- 为什么是问题：流量计数不在 `profileBodyFingerprint` 里（`service/src/main/java/com/github/kr328/clash/service/webdav/WebdavDecision.kt` 第 71–79 行），所以只更新上传、下载、总量或到期时间时，配置文件的 etag 保持不变。这里命中缓存后不会把该配置放进 `remote.profiles`。合并只在远端配置对象存在时才读取 meta 里的 `profileUsage`（`WebdavMerge.kt` 第 67–73 行）。拉取因此留着本地旧计数；之后的 sync 或 push 发现 meta 不同，会把这份旧计数写回去（`WebdavSync.kt` 第 214–217 行、第 253–258 行）。另一台设备刚更新的订阅用量会被覆盖。
- 方向：etag 未变时仍把 meta 里的用量拿来参与合并，避免因为跳过文件下载就丢掉更新的计数。

## r02-2
- 严重度：高
- 症状：用户可见的错误行为
- 代码：
```135:139:service/src/main/java/com/github/kr328/clash/service/ProfileProcessor.kt
                profileLock.withLock {
                    val imported = ImportedDao().queryByUUID(snapshot.uuid)
                    if (imported != null) {
                        context.importedDir.resolve(snapshot.uuid.toString()).deleteRecursively()
                        context.processingDir.copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))
```
- 为什么是问题：订阅自动更新先删掉已导入目录，再把处理目录复制回去，复制结果也没有检查。复制中途失败，或进程在这两步之间退出时，已经提交的配置文件已经不在原处。下一次更新一开始就会 `processingDir.deleteRecursively()`，崩溃后仅剩的那份副本也会被清掉。提交流程是同一顺序（同文件第 56–57 行）。目录一旦变空，WebDAV 导出又会把内容变化记成新修订，空配置会同步到其他设备。
- 方向：新内容先落在临时目录，确认完整后再替换已导入目录，失败时保留原来的文件。

## r02-3
- 严重度：中
- 症状：用户可见的错误行为
- 代码：
```414:425:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavProfiles.kt
    private fun exportActiveStrategy(store: ServiceStore, local: LocalState): Pair<String?, Long> {
        val active = store.activeAppsStrategyConfigUuid?.toString()
        var stamp = local.activeStrategyUpdatedAt
        if (stamp == 0L && active != null) {
            val stamped = System.currentTimeMillis()
            state.update { current ->
                if (current.activeStrategyUpdatedAt > 0) current
                else current.copy(activeStrategyUpdatedAt = stamped)
            }
            stamp = state.read().activeStrategyUpdatedAt
        }
        return active to stamp
    }
```
- 为什么是问题：旧访问控制迁移只写入策略和当前项，不增加 `activeStrategySyncRev`（`ServiceStore.kt` 第 184–195 行）。服务端迁移完成后，`applyLegacyAccess` 会直接返回，也不会补记修订。第一次导出看到时间戳仍是 0，就写成当前时间，修订号却仍用原来的 `local.activeStrategySyncRev`（同文件第 169 行）。`pickActive` 在修订号相同的时候比时间（`WebdavMerge.kt` 第 280–284 行）。两台设备修订号都是 0 时，后同步的那台会把对方正在使用的应用策略换成自己的；对方下次拉取会在 `applyActiveStrategy`（第 512–513 行）接受这个选择。用户没有改选，代理哪些应用却变了。
- 方向：首次导出不要把 0 时间戳盖成现在；只在用户真正改选当前策略时推进修订和时间。

## r02-4
- 严重度：中
- 症状：用户可见的错误行为
- 代码：
```492:493:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavProfiles.kt
        store.appsStrategyConfigs = current.values.toList()
        applyActiveStrategy(store, result)
```
- 为什么是问题：同步把新的应用策略和当前项写进存储之后，没有发出重建隧道的广播。`TunService` 只在建立接口时读取这份名单（`TunService.kt` 第 172–180 行）。运行中的重载只监听 `ACTION_CLASH_REQUEST_RELOAD`（`ReloadModule.kt` 第 9–14 行），WebDAV 这条路径不会发。VPN 已经连着时，同步过来的允许或拒绝名单要到下次重建隧道才生效，这段时间仍按旧名单转发。
- 方向：策略列表或当前项确实变化时，让正在运行的隧道按新名单重建接口。

## r02-5
- 严重度：中
- 症状：用户可见的错误行为
- 代码：
```140:150:service/src/main/java/universe/agentic/device/clash/McpBridge.kt
    private fun start() {
        val vpn = app.getSharedPreferences("ui", Context.MODE_PRIVATE)
            .getBoolean("enable_vpn", true)
        if (vpn) {
            if (VpnService.prepare(app) != null) {
                throw IllegalStateException("vpn consent is required; open ClashMetaX and connect once")
            }
            app.startForegroundServiceCompat(Intent(app, TunService::class.java))
        } else {
            app.startForegroundServiceCompat(Intent(app, ClashService::class.java))
        }
    }
```
- 为什么是问题：`McpService` 跑在 `:background` 进程（`service/src/main/AndroidManifest.xml` 第 44 行）。`start()` 用 `MODE_PRIVATE` 读 `ui` 偏好里的 `enable_vpn`。service 模块里没有人写这个键；跨进程设置走的是 `PreferenceProvider` 的 `MultiProcessPreference`（`PreferenceProvider.kt` 第 17–28 行），这次读取不在那条通道上。后台进程读过一次之后就用自己的内存缓存。界面上改掉 VPN 开关后，常驻的 MCP 再执行 start，仍按旧值启动 `TunService` 或 `ClashService`。缓存里还没有这个键时默认是 true，会去拉起 VPN。
- 方向：MCP 启动隧道时从跨进程可见的存储读取当前模式，并且每次都能看到最新值。
