---
title: "WebDAV v1 跨客户端配置契约"
type: spec
status: review
phase: N/A
created: 2026-10-28
updated: 2026-10-28
summary: "v1 字段、身份投影、修订与用量合并、JVM 字符串排序和共享指纹向量；运行接入状态另见实施计划"
---

# WebDAV v1 跨客户端配置契约

本规格约束 AgenticClash 与 Clash Verge Rev 对同一份配置资产的解释。它不表示两端运行接入、服务器条件写或迁移已经验收。实施边界与验收见 [适配方案](../../../dev/plans/webdav-cross-client-sync.md)，当前能力见 [系统入口](INDEX.md)。

## 1. 文件与字段

远端根由用户配置。文件名称、kind、版本、默认值、限额和删除记录保留窗口以 [WebdavProtocol.kt](../../../service/src/main/java/com/github/kr328/clash/service/webdav/WebdavProtocol.kt) 的常量为准；禁止为改客户端品牌而修改既有 kind。本文不复制默认服务器或默认目录常量。

根下 meta 文件保存策略、访问控制、独立用量与删除记录；配置目录下每个共享 UUID 对应一份 JSON 配置包。配置包不直接保存客户端的本机 ID。临时文件不是配置包，不参与合并。

线字段使用 camelCase，完整定义见 [WebdavModels.kt](../../../service/src/main/java/com/github/kr328/clash/service/webdav/WebdavModels.kt)：

| 对象 | 字段与单位 |
|:-----|:-----------|
| ProfilePayload | kind、version、uuid、name、type、source、interval（毫秒）、upload/download/total（字节）、expire（毫秒时间戳）、createdAt/updatedAt（毫秒时间戳）、ageSecretKey、files、syncRev、writerId |
| ProfileFilePayload | path（包内相对路径）、contentBase64（文件原始字节的 Base64） |
| ProfileUsagePayload | uuid、upload/download/total、expire、updatedAt、writerId；单位与配置包一致 |
| TombstonePayload | uuid、source、type、deletedAt（毫秒）、rev、writerId |
| AppsStrategyPayload | uuid、name、mode、packages、updatedAt、syncRev、writerId |
| SyncMetaPayload | kind/version、updatedAt、旧访问控制字段、策略及策略删除记录、当前策略及其版本、profileUsage、tombstones |

整数须保留 Kotlin Long 精度，不能经 JavaScript Number 重编码。kind/version 必须显式匹配，其余可选字段缺省按模型默认值解释；非空字段的 null 不等于缺省。空对象不能当作有效空 meta。无法解析的对象、错误 kind 或未知版本不得覆盖或删除远端原文件。

未知字段属于远端原对象，不能因为当前客户端不执行就写成空值。发布须透传最新条件读取的原对象，再覆盖已知字段；412 后重新读取原对象。嵌套文件、策略、用量等按各自身份关联，不对未知键做任意 deep merge。内部伴存字段不是协议扩展，不得泄漏到线格式。

## 2. 共享身份与本机 ID

`normalizeSubscriptionSource` 只 trim 首尾空白并去除末尾 `/`；不改变大小写、查询参数顺序或认证参数。Url/External 且来源非空时，身份为规范化 source；其他配置按 UUID 识别。Url 与 External 在同 source 下是同一身份。

两端本机 ID 不必相同。发布前调用 [projectProfileIdentities](../../../service/src/main/java/com/github/kr328/clash/service/webdav/WebdavIdentity.kt)，其规则为：

1. 完整读取远端后，按身份分组。
2. 该身份有远端对象时，以远端 `updatedAt` 最大、再以 UUID 字符串最大的一份作为远端代表；没有远端对象时对本地候选做同样选择。
3. 记录原 UUID 到代表 UUID 的别名，将本地和远端合并输入及用量键投影为代表 UUID；客户端自己的存储 ID 保留。
4. 代表只决定文件名。正文与用量必须收集全部别名候选，再按第 3 节分别选胜者，不能把代表那份旧正文直接当成最终内容。
5. 代表正文与用量成功保存，且旧别名仍属同源、版本未变后，才能条件清理旧别名。去重清理不产生 source tombstone；用户删除才产生来源级删除记录。

投影前先用原 UUID 确认每个别名的显式用量或正文回退候选，再改用量键。未知字段的原对象基准也须在投影前按原 UUID 选定：同一身份所有投影候选使用实际远端代表的原对象，不能在 UUID 已统一后重新打破更新时间并列。

同一个输入 UUID 对应多个身份属于冲突，合并与投影都拒绝产生两个同名发布任务。改变 source/type 且导致身份变化时，使用 `planProfileIdentityChange`：同一可恢复意图记录旧身份删除、新共享 UUID 和新正文；不能原地改身份。新对象修订高于已知旧对象和草稿修订，溢出时拒绝。未改变身份的 Url/External 类型切换不产生来源 tombstone。

本地多条相同来源的纳管歧义由产品入口先解决，纯合并函数不代表用户授权合并全部本机配置。首次远端为空而并发创建的两份对象允许暂时共存，下次完整读取后再选代表。旧客户端仍可能按本机 UUID 发布，故旧端持续写同目录不属于无抖动自动双向保证。

## 3. 正文、用量与删除

正文按下列元组从大到小比较：

```text
(syncRev, updatedAt, writerId, profileContentHash)
```

数值按有符号 Long 比较；字符串使用 JVM UTF-16 code unit 的字典序。Rust 必须显式复现此字符串顺序，不能使用默认 UTF-8/Unicode scalar 顺序。`createdAt` 取全部候选中大于零的最小值，均缺失时沿用正文胜者的值。

底层 `mergeWebdav` 为保持 Android 本机应用兼容，在未投影输入时保留本地代表 UUID；跨端发布调用方必须先做身份投影，并在本地应用前通过映射恢复本机 ID。正文 rank 胜者与远端文件代表是不同概念。

用量独立按 `(updatedAt, writerId, usageContentHash)` 比较，不按计数最大值比较。对于某 UUID，只要任一侧有显式 profileUsage，就不以该 UUID 正文里的旧计数作为候选；没有显式条目才使用正文计数与正文的更新时间/作者作兼容回退。不同别名的所有候选均参与，最终用量 UUID 写成代表。正文指纹不包括用量，因此用量更新不推进正文修订。

删除记录按订阅来源身份或 UUID 分组，保留较大 rev，其次较大 deletedAt。仅保留 deletedAt 大于零且未超过协议保留窗口的记录。匹配配置时，UUID 相同即匹配；Url/External 还按规范化 source 匹配。删除 rev 大于正文修订时删除；相同修订时 deletedAt 大于等于正文 updatedAt 则删除；正文修订较高或同修订正文时间较新时正文保留，并移除被击败的删除记录。

策略、访问控制和当前策略沿用 v1 的 rank 与字段定义。无法执行 Android 策略的桌面客户端仍须保留其字段；没有本机映射不代表远端记录应删除。删除记录存在有限保留窗口，超期离线副本必须重新建立基线，不能自动把本机旧对象重新上传。

## 4. 指纹的逐字节定义

哈希均为 SHA-256，小写十六进制。`NUL` 是单个零字节。文本使用 UTF-8；字符串排序发生在编码之前，按 JVM UTF-16 单元进行。

文件指纹 `fingerprint(files)`：按 path 排序后，对每个条目依次追加：

```text
UTF8(path) + NUL + UTF8(contentBase64) + NUL
```

Base64 哈希的是线字符串字面值，不能在比较前解码再重编码；新导出的文件使用标准、无换行 Base64。不能对 JSON 求哈希，也不能忽略相对路径。空文件列表的文件指纹是 SHA-256 空字节串。

正文内容哈希 `profileContentHash` 对以下字符串以 NUL 连接后求哈希，末尾不额外加 NUL：

```text
name, type, normalizeSubscriptionSource(source), decimal(interval),
ageSecretKey 或空串, fingerprint(files)
```

上传正文指纹 `profileBodyFingerprint`：

```text
profileContentHash, decimal(syncRev), writerId, decimal(updatedAt)
```

用量哈希：

```text
decimal(upload), decimal(download), decimal(total), decimal(expire)
```

`decimal` 不含前导加号、分组符或科学计数法。正文哈希排除 UUID、创建时间、用量、未知字段和本地伴存状态；上传指纹额外纳入正文修订。未知字段仍须通过条件原对象透传，不能因为不参与哈希而覆盖成旧值。

## 5. 一致性向量与集成责任

[共享向量](../../../service/src/test/resources/webdav/cross-client-v1.json) 包含普通空配置和路径同时含 U+10000、U+E000 的反转样本，以及大于 2^53 的时间整数。预期值是固定的外部输入，不由被测函数在测试运行时生成。Android 的 `WebdavSyncTest.crossClientHashVectorsUseJvmPathOrderAndExactLongValues` 读取该文件验证；其他客户端应消费同一份文件或保留明确版本引用。

同一文件的 `merges` 包含四组投影后合并输入与完整预期结果，覆盖三份别名分别提供代表、正文、用量，按原 UUID 补用量回退，以及墓碑的修订与时间裁决。`identityChanges` 包含来源变更与同来源 Url/External 切换两组输入和预期结果。Android 的 `crossClientMergeVectorsPreserveAliasCandidatesAndDeletionRanks` 与 `crossClientIdentityChangeVectorsKeepOldAndNewIdentitiesSeparate` 消费它们。

额外回归覆盖多轮空操作、同更新时间时远端原对象的 UUID 并列裁决、单侧重复项的修订优先和未经规划的身份冲突。测试命令沿用 [构建指南](../../guides/getting-started/build.md)，实际运行证据在实施快照记录。

本规格不使文件系统、数据库与 WebDAV 成为原子事务。调用方仍负责可恢复提交、远端条件写、缺失/弱 ETag 的降级、只读不发变更方法、Pending 编辑暂缓以及公开/LAN 路由与逻辑 endpoint 的区分。纯函数测试通过不代表这些集成责任已通过。
