---
title: "WebDAV 跨客户端实现看板"
type: roadmap
status: active
phase: N/A
created: 2026-10-28
updated: 2026-10-30
summary: "两端基础接入与设置页已落盘；Android 87 项、桌面 18 项聚焦测试及前端门禁通过；真实跨端验收待补"
---

# WebDAV 跨客户端实现看板

状态：执行中。

覆盖需求：PRD-005。


关联：[实施方案](../../plans/webdav-cross-client-sync.md)、[ADR 005](../../../docs/decisions/cross-client-webdav-sync.md)。保留当前两仓库既有 WIP，不提交、不安装或发布未验证产物。

## 任务

| ID | 范围 | 负责人 | 状态 |
|:---|:-----|:-------|:-----|
| T1 | v1 规格、身份投影/迁移、全别名合并、一致性向量 | implement_contract | 已落盘，Kotlin/Rust 共享向量通过 |
| T2 | Android 严格只读、条件发布、远端原包缓存 | implement_android | 已落盘，HTTP 模拟回归通过；真实服务器待验证 |
| T3 | Android scope、未知字段、显式纳管与落盘 | 主代理 / implement_contract | 基础落盘、编译通过；完整事务和故障注入未完成 |
| T4 | Service/IPC P0、目标后端及源提交钩子 | verge_prerequisites | 正式后端已接入，真实 HTTP 聚焦回归及严格 Clippy 通过；Service HTTP 播种暂缓 |
| T5 | 桌面 provider/运行投影、可移植 ZIP | implement_contract | 已落盘，路径/ZIP/Service 边界测试通过；本机缓存 Mihomo 离线运行通过 |
| T6 | 桌面 IPC、typed event、自动同步与恢复生命周期 | implement_android | 已接入，真实生成绑定、调度测试及严格 Clippy 通过 |
| T7 | 桌面 UI/服务层/意图入口与双端集成验收 | 主代理 | 设置页入口、命令接线和前端门禁已通过；真实桌面/双端运行待补 |

## 已确认依赖

用户已认可目标 AGENTS.md 的本地 issue 豁免，桌面实施范围为 `/sdd/Projects/clash-verge-rev`；本轮不改 Service/IPC 第三仓库。

锁定 Service/IPC v2.7.6 源码已只读取得，提交 `b2a7427d6681c8d3780ab83a73f4dc21da2f22d6`：`src/core/runtime_generation/staging.rs` 明确拒绝同 destination 同时为 asset 与 remote provider，且有对应测试；缺失/URL 变化时还会清缓存，readback 只接收声明的 remote provider。现有 API 无法播种 HTTP provider 缓存，目标实现对此显式拒绝/暂缓；不暗改 provider type，不自动切 Sidecar 冒充支持。

## 已落盘范围与复核

- 契约：共享 UUID 与本机 ID 分离，保留全部别名正文/用量候选；投影前按原 UUID 补齐用量 fallback。来源变化输出旧身份 tombstone 与新共享 UUID；远端代表原包承载未知键。共享 JSON 含 2 组哈希、4 组合并与 2 组身份变化输入。
- 传输：TEST/PULL 仅 GET/PROPFIND，严格 207 与完整 XML；写模式先做独占临时 CAS 探测，再条件 PUT/DELETE，不用 MOVE/UNLOCK。412 重新读取最新原包；429 的 Retry-After 进程内节流。明确 source 删除能清理被 tombstone 击败的可读别名，普通去重不删。
- 本机应用：scope 隔离并保留 legacy state；显式推送纳管旧配置/策略，后续用户保存登记配置。Pending、并发编辑、未知类型或缺正文暂缓且不确认发布；导出和应用共享提交锁，完整目录换入与 WebDAV apply journal 提供下一轮重放入口。本地 provider 符号链接和目录循环拒绝读取。
- 调度：待推送/已确认修订按 scope 持久化；只确认同步开始时的修订，旧 generation 不覆盖新调度，暂缓继续重试。
- 桌面后端：正式 config/core/feat/cmd 模块、七个 IPC、typed status event 与真实生成绑定；共享向量、最新 CAS 原包保留、只读 HTTP、条件写探测、显式纳管、可恢复提交 journal、并发编辑/删除 revision guard 已接入。
- 桌面生命周期：源持久化后登记 dirty 并防抖，周期纯拉取；设置变更/关闭自动/退出取消旧 generation，429 scope 冷却跨 generation 保留；运行应用失败保留 runtime_pending，开启自动时八秒纯拉取重试。两种 ZIP 恢复先只读预检，再取消同步、持久 restore epoch、持源写锁恢复资产/可移植绑定并失效旧缓存和意图。
- 桌面资产：保留原始 YAML/文件字节与哈希，按 identity/provenance 投影 provider 路径及播种 Sidecar；ZIP 收录共享嵌套资产及 portable binding，不导出凭据/设备身份/ETag/旧 pending；恢复预检拒绝逃逸、符号链接、敏感状态、重复与缺资产的 manifest。

交叉审查实际修复了手动 IPC 缺内部设置字段造成的误拒绝、旧设置任务可能使用新 scope、同时间别名原包基准、用量 fallback 被投影压掉、删除失败提前发 tombstone，以及本机 provider 越界读取。源码 diff 与测试后的实际结果为准，不把纯函数通过当作运行验收。

## 尚未完成

- 完整 P0–P4 尚未验收。桌面设置页、命令接线和事件状态已接入，尚无已安装客户端的手动/自动同步体验验证。
- Android scope 当前按配置公共根（无公共根用 LAN）、账号、目录派生；无可跨公共根重命名的显式 endpointId。legacy 状态不自动认领，首次唯一同源既有条目仍需显式推送纳管，尚无单项关联入口。
- Android WebDAV apply journal 不等于所有本地保存/删除及策略的整体事务；跨进程策略锁、本地意图的可恢复提交、故障注入和 Android 真实内核离线校验未完成。
- 去重别名条件清理未实施；只保留别名并收敛正文/用量。独占 CAS 探测可能留下隐藏临时对象；Retry-After 不跨进程重启持久化。
- 未安装 APK/桌面客户端、未做双端真实运行或崩溃故障注入、未接触实际 WebDAV 账号或远端数据。Mihomo 离线验证仅使用本机缓存可执行文件，不是目标打包 Sidecar；Service HTTP 播种仍暂缓。Android 代码在 `be1bb6f`。这次 WebDAV 改动没有修改 mihomo 子模块。

## 验证

2026-10-28 至 30：使用构建指南中的 JDK 21 工具链，运行 `:service:testMetaDebugUnitTest :app:compileMetaDebugKotlin`，最终通过（87 tests，0 failures/errors；WebDAV Sync 47、Client 19、Wire 7，另有 ProfileDirectory 等 14 项）。本轮新测试保护共享向量、未知字段、scope、IPC 有效连接比较、CAS 探测与越界导出。桌面前端 typecheck、lint、architecture check、34 项 Vitest 通过；Rust `cargo check --locked --features clippy`、`cargo fmt --all -- --check` 和 10 项 WebDAV 聚焦测试通过。命令入口仍以[构建指南](../../../docs/guides/getting-started/build.md)为准。

首次子任务 `--offline` 在依赖配置阶段失败；主代理在线解析后两次构建均通过，不能把首次环境失败记成源码回归。最终日志在工作区临时文件 `/tmp/agenticclash-webdav-gradle-final.log`，JVM XML 报告在 `service/build/test-results/testMetaDebugUnitTest/`。

桌面正式 crate 使用目标固定 Rust 1.99.1，`cargo fetch --locked` 成功后离线验证。统一 Cargo lane 的命令、`clippy` mock context、`/sdd` 工具链/缓存环境见目标仓库 `docs/webdav-sync-development.md`（CONTRIBUTING 已链接）；provider/ZIP 和 native smoke 命令见 `docs/webdav-provider-assets.md`。没有修改第三仓库或打包资源。

最终聚焦筛选为 `webdav_sync` 10、`provider_projection` 3、`portable` 4、`frontend_event_wire_contract` 1，加单独显式运行 ignored native test 1；`portable` 重复覆盖一个 provider 路径测试，合计 18 个独立测试全部通过。严格 `cargo clippy -p clash-verge --lib --features clippy --locked --offline -- -D warnings` 通过。`frontend-contract` example 成功编译并生成绑定，经项目 Biome 版本格式化后与最终 `src/services/bindings.ts` 完全一致。日志在 `/sdd/.codex-cache/clash-verge-build/webdav-{sync,provider,backup,event,native}-tests.log`、`webdav-sync-clippy.log` 与 `webdav-contract.log`。

native smoke 使用本机缓存 Mihomo Meta 1.10.0 / Go 1.25.5，订阅 URL 指向不可连接的本机端口，由 controller 证实同步快照中的 HTTP provider `OfflineSeed` 已加载，原始源与资产字节不变；子进程退出并回收。此证据不代替目标打包内核、Service 支持或客户端整机验收。临时独立 Rust 后端的早期 12 项通过仅为迁移前证据，现以上述正式 crate 验证为准。

严格文档检查：0 错误，32 条既有 updated 日期告警。新增规格和本看板已落索引。上述测试不代表本看板全部任务或 P0–P4 已交付。
