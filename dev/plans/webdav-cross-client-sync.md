---
title: "AgenticClash 与 Clash Verge Rev 的 WebDAV 同步适配方案"
type: plan
status: in_progress
phase: N/A
created: 2026-10-28
updated: 2026-10-30
summary: "两端基础接入与设置页已实现；Service HTTP provider 与真实跨端运行验收仍待完成"
---

# AgenticClash 与 Clash Verge Rev 的 WebDAV 同步适配方案

状态：执行中。

覆盖需求：PRD-005。


> 当前状态：方案审查完成后，用户已要求并行实施并认可桌面本地 issue 豁免；两端基础后端已落盘，不能将以下全部条款视为已经交付。实际范围、测试与缺口见 [实施看板](../parallel/active/webdav-cross-client-implementation.md)。
> 范围：当前 AgenticClash 工作区与 `/sdd/Projects/clash-verge-rev`。本轮没有修改 Service/IPC 第三仓库，当前 API 不支持的 HTTP provider 离线播种明确暂缓。
> 决策：[ADR 005 草案](../../docs/decisions/cross-client-webdav-sync.md)。审查：[并行看板](../parallel/archive/webdav-cross-client-review.md)。

## 1. 背景与事实依据

| 事实 | 当前源码入口 |
|:-----|:-------------|
| Android 按配置 JSON 和 meta 同步，带修订、删除记录、策略及独立用量 | [模型](../../service/src/main/java/com/github/kr328/clash/service/webdav/WebdavModels.kt)、[同步编排](../../service/src/main/java/com/github/kr328/clash/service/webdav/WebdavSync.kt) |
| 当前协议名称、版本、目录、默认值和各项限额 | [WebdavProtocol.kt](../../service/src/main/java/com/github/kr328/clash/service/webdav/WebdavProtocol.kt)；默认常量不在文档复制 |
| 正文哈希、排序、合并和 tombstone 裁决已有明确算法 | [WebdavDecision.kt](../../service/src/main/java/com/github/kr328/clash/service/webdav/WebdavDecision.kt)、[WebdavMerge.kt](../../service/src/main/java/com/github/kr328/clash/service/webdav/WebdavMerge.kt) |
| Android 导出 config.yaml 与 providers，运行时重定位 provider 路径 | [WebdavProfiles.kt](../../service/src/main/java/com/github/kr328/clash/service/webdav/WebdavProfiles.kt)、[process.go](../../core/src/main/golang/native/config/process.go) |
| Verge 现有 WebDAV 上传、恢复 ZIP，不是逐配置合并 | 目标仓库 `src-tauri/src/core/backup.rs`、`cmd/webdav.rs`、`feat/backup.rs` |
| Verge 原始配置、merge/script 等增强、运行配置分开 | 目标仓库 `config/prfitem.rs`、`config/profiles.rs`、`enhance/mod.rs` |
| 两端都存在本次之前的实现与约束，不能以 HEAD 替代当前工作区分析 | Android 有大量既有未提交改动；目标仓库在本次检查时干净 |

当前行为以 [WebDAV 系统文档](../../docs/systems/webdav/INDEX.md) 为准。本文件后续条款是目标方案，只有实施看板明确列出的部分已经落到源码。

## 2. 目标、首期范围与取舍

目标是手机与桌面共享配置资产，保留各自的本机运行设置，并支持离线编辑后合并、删除传播和订阅用量更新。

首期必须交付：普通 HTTP(S) 订阅、文件配置、相关 provider 文件、名称、来源、更新间隔、订阅用量及删除记录；先完成手动拉取，再完成手动双向，最后开放自动同步。

首期不跨平台执行或上传桌面覆写、JavaScript、节点选择、排序、系统代理、TUN、端口和设备偏好。Android 应用分流策略仍由 Android 管理，桌面保留远端字段。应用集定义当前不属于 v1 同步字段，不顺带扩展范围。Age、content URI 和平台内核差异按能力显式处理，不默认为完整支持。

推荐保留 v1 的配置包与合并语义，在 Android 同步模块和 Verge 原生模块各自实现同一契约；不为了平台中立命名改写远端 kind，也不引入 JNI/FFI 共用同步引擎。两端共同改动用于修复状态隔离、字段保留、提交与冲突边界。破坏性模型变更另走新版本和显式迁移，禁止直接写入同一 v1 目录。

## 3. 共享契约与能力边界

### 3.1 契约来源

首期 v1 字段、默认值、身份规范化与哈希按上表源码冻结为一致性向量；实施 P0 时在 `docs/systems/webdav/` 新增协议规格并更新系统索引，成为两端实现引用的唯一规格。当前模型缺修订字段的老文件仍按现有默认值解析。

Rust 使用 camelCase 的线字段名，保持整数精度与 Kotlin Long 范围一致；不得经 JavaScript Number 中转正文协议。区分缺省、null、空串；Rust 的路径排序须复现 JVM String 的 UTF-16 code unit 顺序，不能使用默认 UTF-8/Unicode 标量顺序。文件按该顺序、无换行 Base64、UTF-8 编码和 NUL 分隔计算哈希，不能改成“对序列化 JSON 求哈希”。路径同时含 U+10000 与 U+E000 的向量必须覆盖排序反转。

未知版本拒绝写入。可识别 v1 中客户端暂不执行的字段、配置类型、文件及 meta 均保留；未知字段以原始 JSON 伴存，修改已知字段时合并回原对象。Android 现有 ignoreUnknownKeys 并不提供往返保留，必须一起补齐。保留不等于执行未知字段。

每次发布以最新 CAS 读取的远端原对象承载未知字段，再应用已知字段的合并结果；412 后丢弃旧原对象并重新读取，不能随已知字段胜者带回旧未知字段。嵌套策略、用量和文件记录按各自身份保留未知键；本地不编辑未知键、不做通用 deep merge。已知/未知语义发生同键冲突时停止该对象写入；原对象已消失且不能证明允许重建时也不能以旧伴存数据复活对象。

首期不添加依赖旧客户端保留的新线字段：旧版 Android 会丢未知字段，不能声称新扩展在旧客户端参与时无损。将来扩展客户端专属数据时必须采用独立命名空间或新版本，不能把额外 JSON 放进现有 profiles 扫描目录。

### 3.2 能力矩阵

| 资产 | Android | Verge 首期 | 写回规则 |
|:-----|:--------|:-----------|:---------|
| File / 普通 HTTP(S) Url | 原生导入与更新 | local / remote 映射 | 允许修改共同字段，保留其余字段 |
| providers 文件 | 配置目录下使用 | 按同步身份存放，运行时映射路径 | 仅上传原始资产，不上传路径重写后的投影 |
| Android 应用策略与旧访问控制 | 执行并合并 | 保留，不执行 | meta CAS 时保留最新远端策略，不以桌面空集合覆盖 |
| External / content URI | 依赖设备提供者 | 暂不启用来源更新；可校验的快照允许本地查看或使用 | 保留原 type/source，不因本机映射改成 File |
| Age 订阅 | 已有私钥及解密链路 | 无已核实的等价链路 | 无法解密则不可激活，原包保留；不清空密钥，不宣称快照必为明文 |
| 桌面增强与设备设置 | 无对应等价语义 | 留在本机 | 不写进共同配置或 Android 策略 |

未知类型、不可解密、不可校验的配置显示未应用原因，不参与远端删除推导。原始私钥及订阅地址属于敏感配置资产，继续遵守两仓库现有凭据与备份边界，不写日志或文档样例。

## 4. 身份、版本与本地状态

### 4.1 身份

共享 UUID 与本机 ID 分开：Android 通常已是 UUID，Verge 保存 `syncUuid ↔ vergeUid`。不重命名现有桌面 UID，不破坏增强项引用。

同订阅识别沿用 v1 的 type/source 规则；保留 URL 查询串，不扩展 URL 重排、转小写等规范化。初次发现相同 URL 的两份配置时关联一个共享身份。桌面多个相同 URL 本地条目属于歧义：要求用户选择关联项，其余保持未纳管，不擅自合并用户数据。

现有 v1 合并会保留传入的 local.uuid；两端都要在合并前做身份投影：每次完整读取后，按现有远端 collapse 规则选择该订阅的远端代表 UUID，将本机导出对象及用量键投影到代表，实际 Android 本地 UUID / Verge UID 保持不变。状态保存代表、别名和本地 ID 的关联。首次远端为空的并发创建允许暂时出现两个 UUID，下一次完整读取后选择同一代表；代表正文与用量确认保存、旧别名仍属同源且版本未变时，才条件清理旧别名。去重清理不发带 source 的 tombstone，否则会连代表一起删除；用户删除才使用来源级删除记录。

代表只决定远端文件身份，不能决定保留哪份旧内容：全部别名的正文与用量候选分别按对应 rank 合并后，才确认代表发布。P0 一致性向量必须覆盖“代表命名来自一份、较高正文修订和最新用量分别来自另两份”的组合。

修改 source/type 导致 subscriptionIdentity 变化时，不能直接在同一 UUID 上改变身份：在一笔可恢复本地提交中，为旧身份记录删除意图，为新身份创建新共享 UUID，保持本地条目和增强引用；如果新来源已有关联项，先解决关联歧义。Url 与 External 在同 source 下按 v1 属于同一身份，不能为这种类型切换发来源 tombstone 误删新配置。迁移不得让合并结果包含两个同 UUID 的对象或并发写同一远端/临时文件。将来改变这条身份规则必须另立版本决策。

配置纳管是显式关系。首次拉取不上传全部本机配置；本机未纳管条目、增强辅助条目与远端不支持条目不成为删除候选。删除远端仅来自已纳管对象的用户删除记录或 v1 合并明确淘汰的可读重复项，不能以列表缺项判断删除。

### 4.2 字段与时钟

| 语义 | v1 / Android | Verge | 规则 |
|:-----|:-------------|:------|:-----|
| 更新间隔 | 毫秒 | 分钟 | 转换检查溢出；不能整除分钟的原值旁存，无修改时原样写回；0 表示禁用 |
| 过期时间 | 毫秒时间戳 | 秒时间戳 | 保存原毫秒值，避免往返截断制造变更 |
| 正文版本 | syncRev、updatedAt、writerId、内容哈希 | 新增同步状态 | 不复用 PrfItem.updated，也不复用文件 mtime |
| 用量版本 | 独立 updatedAt、writerId 与计数 | 同步状态与 extra 映射 | 更新用量不推进正文修订或重传正文 |
| 当前配置、排序和节点选择 | 本机状态 | 本机状态 | 不通过同步切换另一台设备的选择 |

已观察的远端修订必须记入本地基线，用户编辑后从已知修订递增；拉取落盘、单位转换和运行投影不能伪造一次本地编辑。正文按现有 rank、删除按现有 tombstone 规则比较，不简化为墙钟优先，不把用量按最大值合并。

### 4.3 状态与恢复

两端持久化 endpoint scope、device/clientId、身份映射、远端原包与别名、正文/用量版本、tombstones、ETag、成功应用指纹及待发布操作。scope 使用稳定逻辑 endpointId，绑定账号和远端目录；公网与 LAN 是用户显式声明属于同一存储的路由别名，不能靠实际选路 URL 自动拆分或合并 scope。网络切换不换 endpointId；账号/目录/实际存储变更建立新 scope，凭据更换使连接缓存失效，旧 ETag 与删除记录不得跨 scope 使用。

Android 现有 state.json 没有原 endpoint 来源证明，不能默认认领当前设置：原文件隔离保留，先只读远端重建基线；旧 tombstone、dirty 和待发布意图只有在明确确认原目标归属后才能迁入。证明不足时不自动发布删除。当前全部本地配置也不因迁移而自动加入一个新目标。clientId 留在本机，不通过云同步分发；跨设备恢复生成新身份，保留配置关联但重建服务器缓存。

删除记录有有限保留期，不能承诺无限离线后仍不复活。超过协议保留窗口或状态来源不明的副本进入重新关联流程：先读取远端，以远端存量重建基线，本机独有旧对象不自动上传；用户可显式作为新配置导出。窗口取值仍引用协议常量。

### 4.4 备份与恢复

Verge 保留 ZIP 备份产品入口，但必须扩展资产策略：当前只收集 profiles 顶层文件，不能覆盖新增的嵌套 provider 资产。备份包含可移植原始配置、provider 和必要关联信息；排除设备身份、凭据、服务器 ETag/原包缓存及旧待发布操作。新增同步凭据不能因进入 IVerge 而漏入 ZIP，导出采用明确的可移植字段清单。

本地和 WebDAV 恢复共用同步生命周期边界：暂停并取消当前 generation，恢复资产和本机配置，失效旧 pending/ETag/已应用指纹，再读取远端重建关联。恢复旧正文不继承恢复前的新同步修订。同机恢复保留当前设备身份，跨设备恢复生成新身份；恢复动作的 origin 不触发自动上传。Android 沿用现有系统备份排除边界，新增同步文件同样不得意外进入系统备份。

## 5. 原始资产、运行投影与提交

### 5.1 provider 文件

共同包保存原始 config.yaml 与允许同步的附属文件。Verge 为每个共享身份保存独立附属目录，运行生成阶段解析 proxy/rule provider 引用，转换到本地受控路径，禁止只复制 YAML 后假定联网能补齐所有文件。

转换覆盖显式相对路径、自动生成路径及 Android 的 providers 前缀语义；桌面向手机导出时也要构造符合 Android 语义的文件包。源 YAML 字节与协议哈希不因运行时重定位变化。拒绝目录逃逸、Windows 盘符/UNC、符号链接越界与超限文件，不复制应用目录或任意外部文件。

Verge 必须覆盖 Sidecar 与 Service 两条运行路径。当前 `core/runtime_bundle.rs` 把 HTTP provider 作为 remote_providers 交给 Service，不把已有 HTTP 缓存打成 RuntimeAsset；因此只在 enhance 改 path 不足以支持离线。P0 要确定受控 provider 快照如何播种到两条运行路径，以及 Service 更新后的文件怎样经 `core/service.rs` 回读。播种按配置身份、provider section/name、原 URL 和内容版本定位，不能覆盖另一个 URL 的缓存。只回读共享原始配置声明的 provider，脚本/覆写新增的 provider 不反向并入手机配置；运行后由内核更新的共享 provider 内容应形成可辨认的资产变更。

Service/IPC 是第三仓库 `clash-verge-service-ipc` 的外部依赖，目标 `src-tauri/Cargo.toml` 当前引用其 v2.7.6 client，服务端由 `scripts/service-release.mjs` 下载；`scripts/dev-service.mjs` 期待的相邻源码目录当前不存在。因此 P0 必须先核验已发布服务端能否通过现有 RuntimeBundle assets 对 HTTP provider 播种，同时保持 URL 身份与缓存生命周期。客户端结构能表达 assets 不代表服务端一定接受。若需改 IPC/服务端，则要将第三仓库、协议兼容、版本发布、打包和安装纳入后续明确范围；未具备配套前，Service 离线验收不能通过。不得静默改为 file provider 或自动改用 Sidecar 来冒充实现；P0 未解此依赖不得进入承诺完整双运行模式的交付。

遇到缺文件、平台特有字段或内核不兼容时，保留远端原包、标为未应用，不替换上一份可用配置。转换需用实际本地 provider 和离线运行验证，不能只验证 YAML 能解析。

### 5.2 两端共同的提交边界

同步过程是读取基线、远端下载、纯合并、暂存校验、本地提交、远端发布和记录结果；不把本地文件系统、数据库与 WebDAV 伪装成一笔分布式事务。每个对象独立记录已下载、已保存、已应用和已发布状态，部分成功允许下一次恢复。

网络请求不持有配置写锁。最终提交前，在本机写入边界下重新校验版本；期间有用户编辑则重新合并或暂缓该对象，不能用旧快照覆盖新编辑。新正文、附属文件、元数据、版本与待发布意图需要可恢复的提交记录；崩溃恢复先修复未完成提交，再启动自动同步。

Android 调整 WebdavProfiles.install/apply，与 ProfileProcessor 的目录提交、数据库和 Pending 编辑保护统一；遵守 [ADR 004](../../docs/decisions/commit-projection-session.md)，暂存完整前不删正式目录，数据库失败保留恢复依据。同步失败不终止隧道或页面生命周期。

Android 的提交范围还包括 ServiceStore 策略集合、当前策略引用、旧访问控制迁移标记及其同步版本和删除记录。它们不能先写新版本、后写正文；与 Activity/Shell 的跨进程编辑共享可恢复提交和资源写入边界，ProfileProcessor 的文件锁不能代替策略锁。Pending 编辑暂缓的对象可以记录已下载原包，但不推进已应用/已发布确认、不清 dirty；编辑保存或取消后从缓存重新合并，ETag 不变也重试应用，并显示等待编辑结束。

Verge 通过 config 层提交原始数据，feat 层编排校验与运行时应用；遵守目标 `src-tauri/AGENTS.md` 的锁顺序。已有 save_profile_file 直接写正文，必须与新增同步提交共享资源写入边界。先落盘/提交，再发成功事件；运行时失败保留既有权威状态，区分“配置已保存，运行时未应用”和“本地提交失败”。

Verge 新增专用的纯候选构造：无网络、无持久化副作用地生成主配置和必要辅助项，再统一提交。不能直接复用会重新请求订阅、逐个提交增强辅助项且 current=None 时自动选中的现有 create/import 路径。已有条目保留增强引用，新增配置不自动切换当前项，原先 current=None 也保持不选。更新当前配置必须校验后走现有 Core 更新通路。删除当前项沿用各客户端已验证的回退/停用流程，失败不清空当前有效配置或提前删除文件。

拉取、推送、同步保持明确：pull 合并并只应用本地；push 合并并只发布远端；sync 两者都做。自动本地变更来源登记在配置持久化提交点，与同步版本/dirty 一起记录，不依赖整个命令返回 Ok 或前端 onFulfilled：订阅已保存但运行时应用失败时仍有待发布变更。origin 区分用户编辑、订阅更新、远端应用和备份恢复；后两者不重新记为本地编辑。发布失败保留 dirty，不能把一次 pull 当作推送已完成。

## 6. WebDAV 发布、冲突与调度

新同步传输与 ZIP 客户端分开，配置可由用户显式复用服务器凭据，但远端目录、状态和重试不能混用。目标现有客户端的忽略证书验证行为不能成为新同步默认值；公共 HTTPS 验证证书，局域网路由遵循已有显式设置与探测规则，探测不带凭据，跳转不泄漏认证。

沿用 ETag 增量读取，但缓存基于“已成功持久化的远端原包”，不能用已经被本地编辑过的正文代替远端快照。ETag 相同仍合并 meta 中的独立用量；本机文件缺失或缓存不可信必须重新下载。

更新和删除使用条件请求，新建使用 create-only。412 只重读并重算受影响对象，复用后端有限重试策略。423 不自动解除其他客户端持有的锁；429 尊重服务端退避。缺失/不可信 ETag、损坏 meta、无法完整列目录时禁止自动破坏性写入，保留待发布数据并显示原因。

Android 现有临时 PUT + MOVE 及冲突恢复不能直接视为严格 CAS。P0 必须验证服务端对目标条件的实际执行；首期双向支持条件 PUT/DELETE 可靠的端点，条件 MOVE 仅在验证过的兼容路径使用。不提供从真实冲突退化为无条件覆盖的路径；不支持可靠条件写的端点只开放读取或拒绝双向。

只读模式与只读连接测试严格只发 GET/PROPFIND；不调用当前 download 中会产生 MKCOL 的 ensureParents、会 DELETE 的 sweep，也不发 PUT/MOVE/UNLOCK。空目录显示尚无同步数据，建目录和临时文件维护只属于明确开启的写入模式。不能将现有 ACTION_PULL 的实现直接标为只读。

远端正文与 meta 不是原子集合：正文逐份条件发布，meta 最后条件发布；中途失败记录部分成功并重试重算，不假装全部回滚。孤立正文不得仅因 meta 暂未发布被清理，删除由 tombstone 和可验证的对象版本驱动。

手动与自动操作按 endpoint 串行。自动同步独立于 Verge 自动 ZIP 备份，使用现有 Android 防抖与周期常量作为初期行为参考，具体运行参数只在权威位置定义。关闭自动同步、退出或改端点取消旧 generation，旧请求结束后不得写入新 scope。

## 7. 两端落点与实施顺序

| 阶段 | Android 改动 | Verge 改动 | 退出条件 |
|:-----|:-------------|:-----------|:---------|
| P0 契约与样本 | 固化 v1 向量；核对别名、旧客户端和 CAS；写系统规格 | 核对纯候选导入、双路径 provider、外部 Service/IPC 能力与发布依赖、保存锁；落实议题关联 | 一致性向量与兼容矩阵冻结；外部依赖已闭合或显式重定范围；ADR 定稿；不开放写入 |
| P1 状态与安全边界 | scope 与 legacy 隔离迁移、远端原包、未知字段、配置/策略可恢复提交和 Pending 保护 | config 同步状态与 ID 映射、受控存储、ZIP 资产与恢复协调 | 故障注入恢复通过，既有 Android 同步回归通过 |
| P2 手动拉取 | 用更新后的 Android 产生互通样本 | models/merge/transport、纯候选导入、两条运行路径的文件映射、拉取命令和状态 UI | 普通订阅/文件/provider 两端样本通过；尚不开放桌面推送 |
| P3 手动双向 | 验证条件发布、重复 UUID、删除和降级 | 纳管导出、条件发布、删除、当前配置应用 | 并发与部分成功收敛，旧 Android 兼容边界已验证 |
| P4 自动与发布 | 验证后台/前台与设置切换 | 成功提交钩子、防抖、周期拉取、typed 事件、intent 入口 | Android 实机与目标桌面运行验证，退出/切端点无陈旧提交 |

Android 主要改动集中在 service/webdav、ProfileProcessor 的提交接口及必要设置反馈，不预设修改 mihomo 子模块。若后来确需修改 `core/src/foss/golang/clash`，提交或推送前按根向导单独子代理审查内核 diff。

Verge 建议新增 `core/webdav_sync/` 负责协议与网络，`config` 负责存储，`feat` 负责操作编排，`cmd` 负责 IPC；配套改动包括 `core/runtime_bundle.rs`、`core/service.rs`、`core/backup.rs` 与 `feat/backup.rs`。配置新增字段须进入现有效果登记；事件和命令由后端生成绑定，经 services、共享 intent、bus/store 接入，不能在组件里另建通信链。既有 ZIP 入口保留，资产与恢复语义按 §4.4 扩展。

目标仓库根 AGENTS.md 要求产品功能实施有预先存在的 issue。方案阶段不创建外部 issue、不改目标仓库；P0 实施前关联既有 issue 或取得维护者认可。目标代码、注释及 PR 使用英文。协议规格以本仓库为权威，目标仓库按自身规则落接入说明和本仓库的版本引用，避免长期依赖绝对工作区路径。

## 8. 验收依据与实际验证范围

测试只用于保护跨端算法与数据不丢失的关键行为，不复制实现、不批量增加空壳测试。Android 用现有 WebdavSyncTest/WebdavClientTest 加必要向量；Rust 用相同输入及期望结果建立对应测试。构建和 Android 测试命令沿用 [构建指南](../../docs/guides/getting-started/build.md)；目标仓库的具体命令在实施时从其 CONTRIBUTING 与 package/Cargo 配置核实并落入目标文档，本轮不虚构已运行证据。

| 场景 | 必须观察到的结果 |
|:-----|:-----------------|
| 相同/缺省字段、Unicode、Base64、整数边界、旧版样本 | Kotlin/Rust 得到相同哈希、胜者与删除结果；非法输入不覆盖 |
| 相同来源不同 UUID，且两侧均先独立导入 | 多轮往返不新增重复条目；别名用量与删除传播正确 |
| 修改来源、修改到已有关联来源、并发首次创建 | 旧身份删除+新 UUID 创建可恢复；无同 UUID 双任务；空操作多轮后无持续 PUT/DELETE |
| 双方同时改名/正文，时钟漂移，删除对编辑 | 按 v1 规则收敛；无条件覆盖次数为零 |
| ETag 不变但用量更新 | 正文 GET/PUT 不增加，用量仍更新 |
| 本地已有增强，provider 相对路径/自动路径/离线文件 | 增强保持，本地 provider 真实可读，源包哈希不被运行投影改变 |
| Sidecar/Service 首次离线启动、运行后 provider 更新 | 两路都能使用导入资产，共享 provider 新内容可回读，增强新增资源不外溢 |
| 无网络导入、current=None、旧 ZIP 同机与跨设备恢复 | 无隐式订阅下载或逐项提交，无自动选中；provider/关联恢复完整，设备状态不跨机重放 |
| Age、External、未知字段/类型/版本 | 按能力降级且原包保留；不错误激活、不清空密钥和策略 |
| 编辑未返回，下载中保存，目录/数据库失败或进程被杀 | 最新已提交编辑不丢，正式目录可用，可重启恢复 |
| 策略版本写后崩溃、跨进程策略编辑、Pending 保存/取消 | 内容与版本可恢复；暂缓不确认成功，ETag 不变仍重试应用 |
| 订阅已落盘但 Core 应用失败 | 保留 dirty 与已保存事实；运行失败不吞掉自动发布调度 |
| 正文发布后 meta 失败，412/423/429，无 ETag | 部分结果诚实报告，条件写有效，重试不解除他人锁 |
| 端点/账号/目录切换，设备恢复、长离线副本 | scope/身份隔离，不复用 ETag，不批量复活远端删除 |
| 公网/LAN 自动选路、无来源 legacy state、只读端点 | 路由不换 scope，旧删除不误投；只读请求记录仅 GET/PROPFIND |
| 新旧 Android + Verge，关闭自动同步和退出 | 兼容矩阵可复现；旧 generation 不再提交或通知新操作成功 |

新旧版本混用只承诺经测试的 v1 公共配置读取/受限手动互通。旧 Android 保留本地 UUID 的写回可能持续切换远端代表，旧客户端的未知字段丢失、条件 MOVE 和原有提交行为也不会因新端发布而自动变安全；旧端持续写同目录不列为无抖动自动双向支持，完整保证以双方升级和可靠端点为前提。界面不能通过缺失的新能力字段推断远端全是新端，自动模式启用需明确说明参与版本要求。

## 9. 任务状态与审查记录

- [x] 基于两边当前源码形成方案初稿，明确允许两端修改。
- [x] 协议与兼容性独立审查完成，意见已处理并复核。
- [x] Verge 架构与提交接入独立审查完成，意见已处理并复核。
- [x] Android 数据安全与验收独立审查完成，意见已处理并复核。
- [x] 文档门禁通过并区分基线告警（严格 frontmatter/链接检查：0 错误，32 条既有 updated 日期告警）。
- [x] 开始 Android 并行实施，落 [v1 规格](../../docs/systems/webdav/protocol-v1.md) 与共享一致性向量。
- [x] 核验已发布 Service/IPC：服务端拒绝同 destination 同时为 asset 与 remote provider，现有 API 不支持 HTTP provider 快照播种。
- [x] 取得桌面本地实施认可；本轮仅改两仓库，不修改 Service/IPC 第三仓库。
- [x] 桌面正式后端、七个 IPC、typed event、自动调度与 ZIP 恢复接入；18 个独立聚焦测试及严格 Clippy 通过，真实生成绑定。
- [x] 桌面基础 config/feat/cmd/IPC/UI/调度/provider asset/ZIP 接入，Rust 与前端门禁通过。
- [ ] P0–P4 完整实施、故障注入、Service HTTP provider 配套、构建产物与双端运行验证；当前代码接入不代表阶段全部通过。

2026-10-28 三路子代理独立核对两端当前源码，主代理统一修订后由原审查者复核。初审共 13 项（8 项 P1、5 项 P2），均在方案层关闭；没有运行测试或接触远端服务器。“方案审查完成”不等于“代码实施完成”或“跨端运行通过”。

| 审查 | 初审意见 | 修订与复核结果 |
|:-----|:---------|:---------------|
| 协议 P1 ×2 | 来源变更产生同 UUID 双对象；别名代表与清理规则缺失 | §4.1 定义身份迁移、代表投影、全部候选合并与条件清理；去重不发来源 tombstone；已复核关闭 |
| 协议 P2 ×2 | 412 后可能写回旧未知字段；Rust/JVM 路径顺序不同 | §3.1 采用最新 CAS 原对象透传、UTF-16 顺序与反转向量；已复核关闭 |
| Verge P1 ×2 | Service provider 无离线播种；ZIP 与新资产/状态不闭环 | §4.4/§5.1 补双路径资产、回读和备份恢复；方案缺口已关闭，外部 Service 能力仍为 P0 核验项 |
| Verge P2 ×2 | 现有导入有下载/逐项提交/自动选择副作用；命令失败可能漏 dirty | §5.2 纯候选统一提交，持久化提交点登记变更；已复核关闭 |
| Android P1 ×4 | legacy 状态错误认领；LAN 切 scope；只读实际会写；策略版本与内容分离 | §4.3/§5.2/§6 补状态隔离、逻辑端点、严格只读及跨进程策略提交；已复核关闭 |
| Android P2 ×1 | Pending 暂缓被当作已确认 | §5.2 分离下载与确认，编辑结束后重合并；已复核关闭 |

剩余硬门槛：真实服务器兼容、双方新客户端的身份收敛和崩溃恢复、外部 Service/IPC HTTP provider 播种配套、桌面 native smoke 与已安装客户端验收。Sidecar 已用本机缓存 Mihomo 验证离线 HTTP provider，尚非目标打包内核或已安装客户端验收；Service HTTP 播种明确暂缓，没有暗改 provider type 或自动切 Sidecar。三路审查未发现首期必须立即升级 v2 的证据。
