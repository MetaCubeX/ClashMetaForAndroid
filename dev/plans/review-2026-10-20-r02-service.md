---
title: "审查发现 r02 service"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "service 有 5 条缺陷：WebDAV 墓碑会删掉同源的本地 File 副本，策略文件读失败会被写空，订阅更新失败后不再排程，流量用量不会同步，保存配置会把用量写成 0。"
---

# 审查发现 r02 service

范围只包括 `service` 活码。下面 5 条都是这次读到的源码。

## r02-1

- 严重度：高
- 症状：把订阅克隆成本地 File 配置后，再删掉原来的订阅并做 WebDAV 同步，本地这份副本会被删掉。

克隆会保留原来的订阅地址，类型改成 File：

```76:80:service/src/main/java/com/github/kr328/clash/service/ProfileManager.kt
        val pending = Pending(
            uuid = newUUID,
            name = imported.name,
            type = Profile.Type.File,
            source = imported.source,
```

删除订阅时写入的墓碑带这个地址，并且 `deletedAt` 至少比原文件时间新 1 毫秒：

```29:38:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavAutoSync.kt
    fun noteDeleted(context: Context, uuid: UUID, source: String, type: String, updatedAt: Long) {
        val deletedAt = maxOf(System.currentTimeMillis(), updatedAt + 1)
        WebdavLocalState(context).update { state ->
            state.copy(
                tombstones = state.tombstones + TombstonePayload(
                    uuid = uuid.toString(),
                    source = source,
                    type = type,
                    deletedAt = deletedAt,
```

合并时只要墓碑类型是 Url 或 External，并且地址相同，就认为命中，不再看被比较配置自己的类型：

```181:187:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavMerge.kt
fun matchesTombstone(tombstone: TombstonePayload, profile: ProfilePayload): Boolean {
    if (tombstone.uuid.isNotBlank() && tombstone.uuid == profile.uuid) return true
    val source = normalizeSubscriptionSource(profile.source)
    if (source.isEmpty() || normalizeSubscriptionSource(tombstone.source) != source) return false
    val type = if (tombstone.type.isBlank()) profile.type else tombstone.type
    return type == "Url" || type == "External"
}
```

墓碑时间不早于副本的 `updatedAt` 时，副本直接从合并结果里丢掉：

```44:49:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavMerge.kt
        val tombstone = keptTombstones
            .filter { matchesTombstone(it, merged) }
            .maxByOrNull { it.deletedAt }
        if (tombstone != null && tombstone.deletedAt >= merged.updatedAt) {
            continue
        }
```

File 的身份是 uuid，不是订阅地址，所以后面的「同源仍保留」判断不会放过它，`apply` 会把本地目录和数据库行删掉：

```358:362:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavProtocol.kt
fun subscriptionIdentity(type: String, source: String, uuid: String): String {
    val src = normalizeSubscriptionSource(source)
    if ((type == "Url" || type == "External") && src.isNotEmpty()) return "src:$src"
    return "id:$uuid"
}
```

```119:131:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavProfiles.kt
        for (profile in local.profiles) {
            if (profile.uuid in mergedIds) continue
            if (local.profiles.any { it.uuid != profile.uuid && sameSubscription(it, profile) && it.uuid in mergedIds }) {
                continue
            }
            val stillThere = result.profiles.any { sameSubscription(it, profile) }
            if (stillThere) continue
            if (PendingDao().exists(UUID.fromString(profile.uuid))) {
                skipped += 1
                continue
            }
            remove(UUID.fromString(profile.uuid))
            removed += 1
        }
```

克隆发生在删除之前时，副本文件时间早于墓碑的 `deletedAt`，同步就会删掉这份已经保存下来的本地配置。

方向：按地址匹配墓碑时同时要求配置类型也是 Url 或 External。

## r02-2

- 严重度：高
- 症状：应用分流策略文件一旦读不出来，下一次 WebDAV 同步会把策略列表写成空的，本机策略丢失。

读失败时直接当成没有策略；写入是整文件覆盖，没有临时文件：

```81:102:service/src/main/java/com/github/kr328/clash/service/store/ServiceStore.kt
    var appsStrategyConfigs: List<AppsStrategyConfig>
        get() {
            return if (appListsConfigsFile.exists()) {
                try {
                    json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(AppsStrategyConfig.serializer()),
                        appListsConfigsFile.readText()
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
        set(value) {
            appListsConfigsFile.writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(AppsStrategyConfig.serializer()),
                    value
                )
            )
        }
```

同步应用合并结果时，不在合并名单里的本地 id 会被移除，然后无条件写回文件：

```292:302:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavProfiles.kt
        for (id in current.keys.toList()) {
            if (id in keep) continue
            val stamp = fresh.strategyUpdatedAt[id] ?: 0L
            if (stamp > startedAt) continue
            current.remove(id)
            removed += 1
            state.update { item ->
                item.copy(strategyUpdatedAt = item.strategyUpdatedAt - id)
            }
        }
        store.appsStrategyConfigs = current.values.toList()
```

`writeText` 写到一半进程被杀掉时，文件会变成截断 JSON。下一次同步读到异常就得到空列表，空列表里没有任何 id，于是把空列表写回去。策略在这一次同步之后从磁盘消失。

方向：解析失败时保留原文件并跳过本次策略写回；写入先写临时文件再替换。

## r02-3

- 严重度：中
- 症状：订阅自动更新只要失败一次，在后台进程还活着的时候就不会再安排下一次。

立即更新会先取消已有闹钟：

```71:76:service/src/main/java/com/github/kr328/clash/service/ProfileReceiver.kt
        fun schedule(context: Context, imported: Imported) {
            val intent = pendingIntentOf(context, imported)

            context.getSystemService<AlarmManager>()?.cancel(intent)

            intent.send(context, 0, null)
        }
```

下一次闹钟只在 `AlarmManager.set` 里登记一次，不是重复闹钟：

```79:100:service/src/main/java/com/github/kr328/clash/service/ProfileReceiver.kt
        fun scheduleNext(context: Context, imported: Imported) {
            val intent = pendingIntentOf(context, imported)

            context.getSystemService<AlarmManager>()?.cancel(intent)

            if (imported.interval < TimeUnit.MINUTES.toMillis(15))
                return

            val current = System.currentTimeMillis()
            val last = context.importedDir
                .resolve(imported.uuid.toString())
                .resolve("config.yaml")
                .lastModified()

            // file not existed
            if (last < 0)
                return

            val interval = (imported.interval - (current - last)).coerceAtLeast(0)

            context.getSystemService<AlarmManager>()
                ?.set(AlarmManager.RTC, current + interval, intent)
        }
```

更新失败只发通知，不调用 `scheduleNext`：

```82:95:service/src/main/java/com/github/kr328/clash/service/ProfileWorker.kt
    private suspend fun run(uuid: UUID) {
        val imported = ImportedDao().queryByUUID(uuid) ?: return

        try {
            processing(imported.name) {
                ProfileProcessor.update(this, imported.uuid, null)
            }

            completed(imported.uuid, imported.name)

            ProfileReceiver.scheduleNext(this, imported)
        } catch (e: Exception) {
            failed(imported.uuid, imported.name, e.message ?: "Unknown")
        }
    }
```

全量重排在本进程里只做一次，失败之后不会再进来：

```51:55:service/src/main/java/com/github/kr328/clash/service/ProfileReceiver.kt
        suspend fun rescheduleAll(context: Context) = lock.withLock {
            if (initialized)
                return

            initialized = true
```

网络失败是更新的正常结果。闹钟已经被这次触发消费掉，失败分支又不补登记，订阅就停在上次成功的内容上，直到进程重启后重新 `rescheduleAll`。

方向：失败分支里也调用 `scheduleNext`，让下一次仍按间隔触发。

## r02-4

- 严重度：中
- 症状：订阅正文没变、只更新了已用流量时，WebDAV 同步不会把 upload、download、total、expire 传到另一台设备。

导出时如果文件指纹没变，就沿用旧的 `updatedAt`，即使数据库里的流量数字已经变了：

```43:47:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavProfiles.kt
            val updatedAt = if (fingerprint == storedFingerprint && storedUpdatedAt > 0) {
                storedUpdatedAt
            } else {
                fileUpdatedAt.coerceAtLeast(1)
            }
```

这四个字段在同步载荷里：

```26:31:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavModels.kt
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
```

本地应用和远端上传的相等判断都不看它们：

```328:336:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavProfiles.kt
private fun sameContent(left: ProfilePayload, right: ProfilePayload): Boolean {
    return left.uuid == right.uuid &&
        left.name == right.name &&
        left.type == right.type &&
        left.source == right.source &&
        left.interval == right.interval &&
        left.updatedAt == right.updatedAt &&
        left.ageSecretKey == right.ageSecretKey &&
        left.files == right.files
}
```

```286:294:service/src/main/java/com/github/kr328/clash/service/webdav/WebdavSync.kt
private fun sameRemote(existing: ProfilePayload, profile: ProfilePayload): Boolean {
    return existing.name == profile.name &&
        existing.type == profile.type &&
        existing.source == profile.source &&
        existing.interval == profile.interval &&
        existing.updatedAt == profile.updatedAt &&
        existing.ageSecretKey == profile.ageSecretKey &&
        fingerprint(existing.files) == fingerprint(profile.files)
}
```

订阅刷新经常只改流量头、不改配置正文。指纹相同就保持旧 `updatedAt`，两边都认为配置没变，新的用量既不上传，也不写进另一台设备。

方向：比较配置时带上这四个流量字段；正文未变但流量变了也要上传并应用。

## r02-5

- 严重度：中
- 症状：提交已有配置时，如果这次没有订阅流量头，界面上的已用流量、总量和到期时间变成 0。

File 类型不会强制重新下载。写入已导入记录时，没有流量头就用 0，已有行的 `old` 只保留了 `createdAt`：

```51:74:service/src/main/java/com/github/kr328/clash/service/ProfileProcessor.kt
                val force = snapshot.type != Profile.Type.File
                val subscriptionInfo = fetchProfile(context, snapshot.source, force, callback)

                profileLock.withLock {
                    if (PendingDao().queryByUUID(snapshot.uuid) == snapshot) {
                        context.importedDir.resolve(snapshot.uuid.toString()).deleteRecursively()
                        context.processingDir.copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))

                        val old = ImportedDao().queryByUUID(snapshot.uuid)
                        val updateInterval = subscriptionInfo?.subUpdateInterval
                            ?.takeIf { old == null && snapshot.interval == 0L }
                            ?: snapshot.interval
                        val new = Imported(
                            snapshot.uuid,
                            snapshot.name,
                            snapshot.type,
                            snapshot.source,
                            updateInterval,
                            subscriptionInfo?.subUpload ?: 0,
                            subscriptionInfo?.subDownload ?: 0,
                            subscriptionInfo?.subTotal ?: 0,
                            subscriptionInfo?.subExpire ?: 0,
                            old?.createdAt ?: System.currentTimeMillis(),
```

克隆时明确把用量抄进 pending：

```81:86:service/src/main/java/com/github/kr328/clash/service/ProfileManager.kt
            interval = imported.interval,
            upload = imported.upload,
            total = imported.total,
            download = imported.download,
            expire = imported.expire,
            ageSecretKey = imported.ageSecretKey
```

自动更新在没有 `subUpload` 时不会改这些列：

```119:128:service/src/main/java/com/github/kr328/clash/service/ProfileProcessor.kt
                        val upload = subscriptionInfo?.subUpload
                        if (upload != null) {
                            ImportedDao().update(
                                imported.copy(
                                    upload = upload,
                                    download = subscriptionInfo.subDownload ?: 0,
                                    total = subscriptionInfo.subTotal ?: 0,
                                    expire = subscriptionInfo.subExpire ?: 0,
                                )
                            )
                        }
```

提交克隆出的 File 配置时 `force` 为 false，流量头通常不存在，pending 里抄来的用量被写成 0。已经导入的配置再保存时，只要响应里没有流量头，原来的用量同样被写成 0。

方向：没有流量头时保留 `old` 或 pending 里的 upload、download、total、expire。
