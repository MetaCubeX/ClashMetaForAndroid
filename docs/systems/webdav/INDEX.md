---
title: "WebDAV 同步"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: "配置与分流策略的拉取、推送和合并。默认常量以 WebdavProtocol.kt 为准"
---

# WebDAV 同步

实现在 `service/src/main/java/com/github/kr328/clash/service/webdav/`。设置界面是 `app` 的 `WebdavSettingsActivity` 与 `design` 的 `WebdavSettingsDesign`。

## 权威常量

默认公共地址、局域网地址、用户名、远端路径和旧值迁移都在 `WebdavProtocol.kt` 的 `WEBDAV_DEFAULT_*` 与 `WEBDAV_LEGACY_*`。本文不复制这些字面量。密码没有默认值。

设置存在应用文件目录的 `webdav/settings.json`。密码由 `WebdavCipher` 密封后再落盘。`autoSync` 默认关闭。

公共 URL 必须是 HTTPS。`requirePublicWebdavUrl` 拒绝公网 HTTP。局域网地址在 `joinWebdavUrl(..., allowPrivateHttp = true)` 下可以是私网 HTTP，因此 `app/src/main/res/xml/network_security_config.xml` 允许明文。公网 HTTP、`..` 和会改掉源站的路径仍然被拒绝。

是否探测局域网由 `prefersWebdavLan` 决定：当前要有一条具备 `TRANSPORT_WIFI` 且带 `NET_CAPABILITY_INTERNET` 的网络。探测是一次 2.5 秒超时的 PROPFIND，并带上 Basic 认证。传输层返回了任意 HTTP 响应就选用局域网；超时、拒绝连接和 DNS 失败则改用公共地址。局域网地址为空时直接用公共地址。没有公共地址且探测失败时，同步失败。

## 拉取与推送

`WebdavSync` 的拉取应用到本机，不把本机配置再上传。推送合并后上传，不把远端结果写回本机已导入的配置、访问控制或本地删除记录。

手动同步与自动同步共用一把锁，串行执行。同一订阅 URL 在两侧都存在时，保留本机 UUID 和较新的配置。

`mergeWebdav` / `mergeStrategies` 按同一侧重复项保留更新的 `updatedAt`，再比较 UUID。上传时删除远端未被保留的 UUID 文件。

访问控制离开页面时的自动同步条件见 [访问控制](../access-control/INDEX.md)。

## 测试

`service/src/test/java/com/github/kr328/clash/service/webdav/` 里的 `WebdavSyncTest` 与 `WebdavClientTest` 覆盖路径、局域网选择、合并、删除记录和 ETag。命令见 [构建指南](../../guides/getting-started/build.md)。

模块入口：[service](../../modules/service/INDEX.md)、[app](../../modules/app/INDEX.md)。
