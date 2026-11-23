---
title: "WebDAV 同步"
type: index
status: accepted
phase: N/A
updated: 2026-10-30
summary: "v1 配置与策略同步；账号目录隔离、远端原包缓存、严格只读与条件发布；跨桌面集成仍在实施"
---

# WebDAV 同步

对应需求：PRD-005。

实现在 `service/src/main/java/com/github/kr328/clash/service/webdav/`。设置界面是 `app` 的 `WebdavSettingsActivity` 与 `design` 的 `WebdavSettingsDesign`。

## 权威常量

默认公共地址、局域网地址和用户名都为空，首次使用时由用户填写。默认远端路径在 `WebdavProtocol.kt` 的 `WEBDAV_DEFAULT_REMOTE_PATH`。本文不复制这些字面量。密码没有默认值。

设置存在应用文件目录的 `webdav/settings.json`。密码由 `WebdavCipher` 密封后再落盘，界面只显示已设置或未设置，不回填明文。编辑密码时输入框从空开始：留空确认保持原密码，点「清除」写入空串，输入新值则覆盖。`autoSync` 默认关闭。

密文读不出来（Keystore 密钥失效、换机或恢复备份）时，`WebdavCipher.open` 返回 null，与「本来没有密码」的空串区分开。界面写「无法读取，请重新输入」，地址、用户名和远端路径照常保留。写入时只有显式改密码才重新密封，其余字段的写入沿用磁盘上的原密文，不会把读不出的密码固化成空。

公共 URL 必须是 HTTPS。`requirePublicWebdavUrl` 拒绝公网 HTTP。局域网地址在 `joinWebdavUrl(..., allowPrivateHttp = true)` 下可以是私网 HTTP，因此 `app/src/main/res/xml/network_security_config.xml` 允许明文。公网 HTTP、`..` 和会改掉源站的路径仍然被拒绝。

是否探测局域网由 `prefersWebdavLan` 决定：当前要有一条具备 `TRANSPORT_WIFI` 的网络。探测是一次 2.5 秒超时的 PROPFIND，不带认证。只有带 DAV 头、207，或带认证要求的 401/403 才选用局域网。超时、拒绝连接、DNS 失败，以及普通网页的 200/404 都改用公共地址。同一组地址在 `WEBDAV_ROUTE_MEMO_MS` 内不再探测。局域网地址为空时直接用公共地址。没有公共地址且探测失败时，同步失败。

## 拉取与推送

`WebdavSync` 的拉取应用到本机，不把本机配置再上传。推送合并后上传，不把远端结果写回本机已导入的配置、访问控制或本地删除记录。

手动同步与自动同步共用一把锁，串行执行。测试连接和拉取的远端请求仅为 GET/PROPFIND；不建目录、不清理临时文件、不解除远端锁。测试连接不推进上次拉取时间。

正文、用量、身份投影和字节级指纹以 [v1 契约](protocol-v1.md) 为准。同源对象先选择共享代表 UUID，再收集所有别名的正文和用量候选分别裁决；Android 本机 UUID 不改。来源身份变化时登记旧身份删除与新共享 UUID。去重别名暂保留，尚未实现确认完整往返后的条件清理。

PROPFIND 的 ETag 与已持久化远端原包一致时跳过正文 GET；不能拿本机编辑后的正文冒充远端缓存。ETag 缺失必须下载。meta 用量仍参与每次合并，正文未改不重传。空正文、解析失败和未知版本不删除、不覆盖。原 JSON 的未知键伴存；发布及 412 重试都以最新远端原对象透传未知键。

写操作先验证完整 DAV 目录与 meta，再创建父目录，并用独占临时对象探测服务端是否执行 create-only 和条件 PUT/DELETE。真实新建带 `If-None-Match: *`，更新和删除要求强 ETag 与 `If-Match`；不使用 MOVE 代替目标 CAS。探测不通过就停止真实配置与 meta 发布。412 只重读并重算冲突对象，最多 3 次；从不降为无条件覆盖。只有获胜删除记录能触发远端删除，列表缺项本身不是删除。探测失败可能留下忽略扫描的临时文件。

GET、PUT、DELETE 并发上限见权威常量；遇到 429/423 降低并发，不发 UNLOCK。429 的 `Retry-After` 秒数或 HTTP-date 在进程内按服务器、账号、目录记录，到期前不发请求；重启后不保留该限流记录。

## 本地状态、纳管与暂缓

状态存在应用文件目录 `webdav/scopes/<scope>/state.json`。scope 由配置的公共根地址（无公共地址时用 LAN 根地址）、账号和远端目录生成；同一配置下自动选用公网或 LAN 不换 scope。更改账号、目录或公共根地址进入另一 scope。当前实现尚无可跨公共地址重命名的显式 endpointId。旧 `webdav/state.json` 原样保留，不自动迁入旧 ETag、删除意图或全量本机配置。

新 scope 只导出已纳管对象；显式「推送」可将现有配置和策略加入该目标。用户后续成功保存的配置、已登记策略也纳入当前 scope。首次拉取遇到未纳管的同源/同 UUID 本机对象时暂缓关联，不覆盖本机，也不默认为整库上传授权。

本机正在编辑、下载期间出现新保存、类型不支持或缺少非空 config.yaml 时暂缓该配置。已下载原包可以缓存，已应用/已发布确认不能推进；相同 ETag 的下一轮仍尝试应用。配置导出与最终应用使用 ProfileProcessor 的写入边界；安装先完整暂存，再换目录，目录/数据库/版本之间的中断由当前 scope 的 apply journal 在下一轮导出前重放。重放遇到后续不同内容的本地编辑会保留现场，等待合并。

策略先写正文再写同步版本；跨进程策略写锁和可恢复整体事务仍未完成。本地用户保存/删除与同步意图也未合为完整可恢复事务。配置暂存目前验证路径、大小、类型和非空正文，尚未增加真实内核离线校验或崩溃故障注入验收。因此本轮实现不等于完整双端 P0–P4 交付。

自动同步里，本地改动经过 `WEBDAV_PUSH_DEBOUNCE_MS` 再做一次同步。连续失败 3 次以后改为按 `WEBDAV_PULL_INTERVAL_MS` 再试。scope 内待推送修订持久化；成功同步只确认开始时的修订，后续编辑及暂缓对象继续重试。旧任务不能改写较新调度的 generation。服务启动发现未完成推送会恢复调度，否则周期任务只拉取。名单和策略的变更只调度同步，页面不等待它结束。

访问控制离开页面时的自动同步条件见 [访问控制](../access-control/INDEX.md)。

## 测试

`service/src/test/java/com/github/kr328/clash/service/webdav/` 的测试覆盖路径、身份与别名、合并、删除记录、字段保留、scope 和 HTTP 条件请求。Kotlin/Rust 共用向量见 [v1 契约](protocol-v1.md)。命令见 [构建指南](../../guides/getting-started/build.md)，本轮实际结果与未完成项见 [实施看板](../../../dev/parallel/active/webdav-cross-client-implementation.md)。

模块入口：[service](../../modules/service/INDEX.md)、[app](../../modules/app/INDEX.md)。
