---
title: "单元测试"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-15
summary: "列出 service 与 design 的 JVM 单元测试任务和覆盖范围。这次补文档没有跑这些测试。"
---

# 单元测试

本文只列任务和范围，这次补文档没有跑这些测试。

工具链、JDK、flavor 和 mihomo 子模块见 [构建、配置与测试](build.md)。那一节只给出任务名。覆盖范围以本文为准。

根 `build.gradle.kts` 的 flavor 名是 `alpha` 和 `meta`。`alpha` 是默认 flavor。构建类型包含 `debug`。下面的任务名只写 debug，与构建指南一致。

## 任务

仓库里只有 `service` 和 `design` 有 `src/test`。没有 `androidTest`。

| 模块 | 源码 | metaDebug | alphaDebug |
|:-----|:-----|:----------|:-----------|
| service | `service/src/test/` | `:service:testMetaDebugUnitTest` | `:service:testAlphaDebugUnitTest` |
| design | `design/src/test/` | `:design:testMetaDebugUnitTest` | `:design:testAlphaDebugUnitTest` |

`app`、`common`、`core`、`hideapi` 没有 `src/test`。

```bash
./gradlew :service:testMetaDebugUnitTest :design:testMetaDebugUnitTest
```

默认 flavor 把任务名里的 `Meta` 换成 `Alpha`。

## service

类在 `service/src/test/java/com/github/kr328/clash/service/webdav/`。行为规则见 [WebDAV](../../systems/webdav/INDEX.md)。本文不写默认地址、用户名和密码。

`WebdavSyncTest` 检查地址拼接、公网必须 HTTPS、私网 HTTP 的允许条件，以及局域网探测失败时回到公共地址。它也检查 PROPFIND 条目、ETag、临时文件是否过期，以及配置、删除记录、访问控制和应用分组的合并。同一订阅留下本机 UUID 和较新内容。路径不能离开源站。空的远端 meta 视为损坏。

`WebdavClientTest` 注入假的 HTTP 传输，不访问真实服务器。目标未改时，带 `If-Match` 的 MOVE 若得到 412，会去掉该条件再移动，并采用新的 ETag。目标已改则记为冲突，并删除临时文件。删除请求带上 `If-Match`。412 是冲突。404 当作目标已经不在。

## design

`AppSetMembershipTest` 在 `design/src/test/java/com/github/kr328/clash/design/model/AppSetMembershipTest.kt`。空集合保持未选，切换不改已选包名。部分选中时切换会选中全部成员。已经全选时只清掉这些成员，集合外的包名还在。

## 没有覆盖

没有设备安装验收。没有 UI 仪器测试。外部控制不在这些测试里，见 [Activity](../../../app/docs/activities.md)。

也不覆盖真实 WebDAV 往返、隧道启停、配置导入和界面点击。

本文只列任务和范围，这次补文档没有跑这些测试。
