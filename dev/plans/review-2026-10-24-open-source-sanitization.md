---
title: "开源前脱敏与发布边界审查"
type: plan
status: review
phase: N/A
updated: 2026-10-30
summary: "审查工作区、全部 Git 历史、构建配置、工作流和子模块；列出开源前必须脱敏或轮换的项"
---

# 开源前脱敏与发布边界审查

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


> 审查日期：2026-10-24
> 范围：当前工作区、全部本地 Git 历史、未跟踪文件、GitHub Actions、Android 备份配置、mihomo 子模块。
> 原则：本文只记录位置和类型，不复制真实域名、内网地址、用户名、口令或密钥内容。

## 发布方式

- 新仓库：`https://github.com/TIIEHenry/AgenticClash`。
- 新仓库不带旧 Git 历史，从当前工作区重新初始化。
- 发布步骤已从当前工作区初始化 `https://github.com/TIIEHenry/AgenticClash`，不带旧历史。旧仓库 `main` 不接收这次提交。
- 个人联系方式统一为 Clarence Henry，酷安主页 `https://www.coolapk.com/u/469700`。
- 公众号：超越观。Telegram 频道：`https://t.me/universesoftware`。
- QQ 群号保持原样，不因本次开源调整。

## 结论

没有发现常见的云厂商密钥、私钥块、真实订阅链接或 WebDAV 口令进入已跟踪文件或历史。测试里的 `user`、`secret`、`letmein` 等是占位值，不是生产凭据。

需要处理的是四类发布边界：

1. **签名密钥库被 Git 跟踪**，且来自上游历史；只删工作区文件不够，必须决定是否重写历史。
2. **WebDAV 默认常量是维护者的真实基础设施**，并同步出现在多语言界面文案和单元测试里。
3. **两个发布工作流把 `signing.properties` 打印到 Actions 日志**；如果日志曾经公开，签名口令应按已泄露处理。
4. **Android 自动备份包含可能含令牌、订阅地址和节点凭据的数据**；这不是仓库里的字符串泄露，但会随开源版本进入用户设备。

## 必须处理

### P0-1 签名密钥库

已决定继续使用仓库里的 `release.keystore`，签名方式和上游一样：`storeFile` 固定为仓库根的 `release.keystore`，口令、别名和 key 口令仍来自 `signing.properties`。该文件与上游是同一个 blob，已经在公开的上游仓库和 `TIIEHenry/ClashMetaForAndroid` 里。

- 发布工作流不再从 `SIGNING_KEYSTORE_BASE64` 解码覆盖它，也不回显 `signing.properties`。
- `scripts/write-signing-properties.py` 只写 `keystore.password`、`key.alias`、`key.password`。
- 其它 `*.keystore` 仍被忽略。不要另提交一份密钥库，也不要把口令写进仓库。

### P0-2 WebDAV 真实默认值

权威常量在 `service/src/main/java/com/github/kr328/clash/service/webdav/WebdavProtocol.kt`：

- 公共地址、局域网地址和默认用户名已改为空，首次使用时由用户填写。
- 默认远端目录改为中性的 `/clash-meta`，个人目录迁移分支已删除。

同一组值还出现在：

- `design/src/main/res/values/strings.xml` 的 `webdav_url_placeholder`、`webdav_lan_hint`、`webdav_remote_path_default`。
- `design/src/main/res/values-*/strings.xml` 的同一组键，共 8 个 locale。
- `service/src/test/java/com/github/kr328/clash/service/webdav/WebdavSyncTest.kt` 的局域网地址和目录断言。

处置建议：

- 已完成：默认公共地址、局域网地址和用户名留空。
- 已完成：默认远端目录改为 `/clash-meta`，删除个人目录迁移分支。
- 已完成：所有 locale 的占位文案改成 `example.com` 和通用私网地址。
- 已完成：测试改用通用夹具，不再断言真实内网地址。

如果该 WebDAV 服务曾经随公开仓库暴露，除改代码外还要轮换口令、检查服务端访问日志，并确认公共地址没有目录列表或匿名读取。

### P0-3 CI 日志中的签名口令

| 文件 | 位置 | 问题 |
|:-----|:-----|:-----|
| `.github/workflows/build-pre-release.yaml` | `Signing properties` 步骤 | 原先写入后执行 `cat signing.properties`，已删除 |
| `.github/workflows/build-release.yaml` | `Signing properties` 步骤 | 同上，已删除 |
| `.github/workflows/build-debug.yaml` | 已注释的签名步骤 | 注释里的 `cat` 写法已删除 |

处置：

- 已完成：删除两处 `echo "cat signing.properties"` 和 `cat signing.properties`。
- 写入时使用 `java.util.Properties.store` 等能正确转义的方式，避免口令里的 `\`、`$`、反引号被 shell 或 `Properties.load` 改写。
- 构建脚本只从文件读取，不把内容回显到 stdout。
- 如果这些工作流的日志曾经对公众可见，轮换 `SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`，并删除旧日志/旧工件。
- 给发布环境加保护规则，限制谁能触发 `Build Release` 和 `Build Pre-Release`。

两个发布工作流使用仓库里的 `release.keystore`。写入 `signing.properties` 由 `scripts/write-signing-properties.py` 完成，按 `java.util.Properties` 规则转义。首次发布前需要配置三个 secret：

- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

## 建议处理

### P1-1 Android 备份会带走令牌和配置

`app/src/main/AndroidManifest.xml` 打开了 `allowBackup`，`app/src/main/res/xml/full_backup_content.xml` 包含：

- 全部 `sharedpref`，其中 `ServiceStore` 的 `mcp_token` 是本地 MCP 的 Bearer 令牌。
- 全部 `database`，配置库可能保存订阅来源 URL。
- `imported`、`pending`，可能包含节点地址和代理口令。
- `clash/override.json`。

已完成：

- `full_backup_content.xml` 只保留 `app.xml`、`tips.xml`、`ui.xml`。
- 新增 `data_extraction_rules.xml`，云备份和设备迁移使用同一份白名单。
- `imported`、`pending`、配置数据库、`clash/override.json`、WebDAV 设置和 MCP 令牌都不再进入系统备份。
- 隐私政策写明备份范围和本地数据流。

跨设备迁移配置仍需使用应用自己的导出功能，不依赖系统备份。

### P1-2 WebDAV 口令的旧版落盘

`WebdavCipher` 在 API 21–22 上走 `v0:` 分支，实际是 Base64 而不是加密。minSdk 仍是 21 时，这条路径会保留。建议改用 Android Keystore 可用的最低版本策略，或明确提高 minSdk；不要把 Base64 当作加密写进文档。

### P1-3 网络安全配置

`app/src/main/res/xml/network_security_config.xml` 信任用户证书。对代理类应用可能是刻意为之，但开源前应确认这是产品承诺，而不是调试遗留；如果是后者，移除 `<certificates src="user" />`。

### P1-4 工作流供应链

发布工作流使用的第三方 action 多数是浮动 tag，不是提交 SHA。开源后建议全部 pin 到 SHA，开启 Dependabot，并限制 `update-dependencies` 的写入权限。`build-pre-release` 跟随 `main` push 触发，主干必须有分支保护。

### P1-5 文档与元数据过期

已完成：

- 根 `README.md` 改为本 fork 的英文介绍，写明 JDK 21、`universe.agentic.device.clash`、外部控制 action、社区链接和 GPL-3.0。
- 移除指向上游包名的 F-Droid 徽章。
- `PRIVACY_POLICY.md` 按实际数据流重写，明确没有分析、广告或崩溃上报 SDK。
- `CONTRIBUTING.md` 明确贡献只按 GPL-3.0 授权，不并入闭源分支。
- 新增 `SECURITY.md`，给出私下报告漏洞的入口和仓库密钥边界。

### P1-6 提交作者信息

新仓库不带旧历史，因此旧提交里的邮箱不会进入发布仓库。首次提交时使用公开身份 `Clarence Henry` 和公开联系方式，不要沿用旧仓库的本地 Git 身份。

### P1-7 子模块可复现性

已创建公开 fork `https://github.com/TIIEHenry/mihomo`。`.gitmodules` 指向该仓库的 `ca-bundle`。本地子模块 `origin` 是这个 fork，`upstream` 仍是 `MetaCubeX/mihomo`。

`ca-bundle` 的 tip 是 `e4c61f30d8011aa3eb6a0f8cb46b1d9356018c9a`。`563a0da461ab1cdae3dd9c943d85fcd3ff4700f1` 上的审查问题已修复并推送，子模块工作区干净。父提交 `90a5afcc06a174033538aa7c844917cf0933cade` 是 CA 证书包。新仓库的 gitlink 是 `e4c61f30`。旧仓库工作区里的 `.gitmodules` 和 gitlink 仍未提交。

不要把 fork 的默认分支改成 `ca-bundle`。CMake 读取 `git branch -r --contains` 的第一行；`origin/HEAD` 指向 `ca-bundle` 时，版本字符串会带上 `HEAD`。默认分支保持 `main`。新鲜克隆若报分支为空，在子模块里执行 `git fetch origin ca-bundle`。

### P1-8 演示页示例数据

`ui-web-demo` 原先含旧显示名 `ClashMetaX`、`TIIEHenry Fork` 字样、带地区与线路描述的节点名、公网 IP 和真实服务域名。它们不是凭据，但会让人误以为示例指向具体服务。

已完成：

- 品牌统一为 `AgenticClash`。
- 节点名改为「示例节点 NN」，机房改为「示例机房」。
- IP 改为 RFC 5737 文档地址 `192.0.2.0/24`。
- 连接与日志里的服务域名改为 `example.com`。
- `index.html` 由 `assemble.py` 从 `parts_*` 重新生成，源文件与生成物一致。

## 已确认干净

- 没有跟踪 `.env`、`.env.local`、`local.properties`、`signing.properties`、`google-services.json`、`tracker.properties`。
- 没有在已跟踪文件或全部历史中发现 PEM 私钥块、AWS/GitHub/OpenAI/Slack/Google 等常见密钥格式。
- 没有发现真实订阅链接或 WebDAV 口令；测试里的账号口令是占位值。
- `ui-web-demo` 里的订阅/WebDAV 域名是演示占位，不是本仓库凭据；但开源前应换成保留域名，避免看起来像在为某个服务背书。
- `.mimocode/` 已在根 `.gitignore` 中排除，且未被跟踪。

## 执行顺序

1. 已确定新仓库不带旧历史；不要从旧仓库 clone 后直接推送。
2. 处理 P0-1：`release.keystore` 与上游一样留在仓库里。CI 只配置口令、别名和 key 口令。
3. 处理 P0-3：日志回显已删除；仍要轮换可能暴露的签名口令。
4. 处理 P0-2：WebDAV 默认值、locale 文案和测试已替换；文档已同步。
5. 处理 P1-1 到 P1-3：备份、旧版口令落盘、网络安全配置。
6. 处理 P1-4 到 P1-7：工作流、文档、作者信息、子模块。
7. 加 secret scan（例如 gitleaks）到 CI 和本地 pre-commit；上线前再跑一次全历史扫描。

## 验收

- [x] `release.keystore` 与上游同一份文件，继续放在仓库里。暂存区不另含口令文件或第二份密钥库。
- [x] 新仓库不带旧历史，旧历史中的密钥库不会随发布仓库分发。
- [x] `WebdavProtocol.kt`、所有 locale、测试和演示页不再出现真实域名、内网地址、用户名或私有目录。通用示例地址保留。
- [x] 两个发布工作流不再回显 `signing.properties`，也不解码覆盖 `release.keystore`。
- [x] `./gradlew :service:testMetaDebugUnitTest` 与 `:design:testMetaDebugUnitTest` 通过。
- [x] `python3 scripts/check-docs-health.py` 通过，或只剩与本次无关的既有告警。
- [x] 备份规则不再包含 MCP 令牌、订阅数据库和导入的节点配置。
- [x] 根 README、隐私政策、贡献指南与实际包名、JDK、数据流一致。
- [x] 帮助页的社区分组显示 Clarence Henry、公众号「超越观」和 Telegram 频道；QQ 群号未改动。
- [x] 子模块 `ca-bundle` 已推到 `https://github.com/TIIEHenry/mihomo`，子模块工作区没有未提交修改。新仓库 gitlink 为 `e4c61f30`。
- [x] 演示页不再包含旧品牌、真实节点名、公网 IP 或真实服务域名。

## 风险

| 风险 | 缓解 |
|:-----|:-----|
| 只删工作区文件，历史仍可恢复 | 用 `git filter-repo` 或新建干净历史；不要只做删除提交 |
| 重写历史导致协作者分叉失效 | 提前通知，统一重新 clone；保留旧仓库只读备份 |
| 改默认值破坏首次使用体验 | 首次进入 WebDAV 设置时要求填写地址；补空状态文案和测试 |
| 轮换签名密钥导致已安装版本无法升级 | 先确认应用商店的签名方案；必要时走官方密钥轮换流程 |
| 备份规则改动导致用户丢失设置 | 提供显式的加密导出/导入，而不是依赖系统备份 |

## 参考

- 构建与签名：[构建指南](../../docs/guides/getting-started/build.md)
- WebDAV 行为：[WebDAV 同步](../../docs/systems/webdav/INDEX.md)
- 文档维护：[维护规则](../../docs/DOCUMENTATION.md)
