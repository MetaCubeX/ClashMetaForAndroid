---
title: "术语表"
type: concept
status: accepted
phase: N/A
updated: 2026-10-24
summary: "本仓库文档里的用词。定义以这里为准"
---

# 术语表

| 术语 | 含义 |
|:-----|:-----|
| mihomo | MetaCubeX 的 Clash Meta 内核。本仓库用 Git 子模块引入 |
| flavor | `alpha` 与 `meta` 两个 product flavor。默认是 `alpha` |
| 应用包名 | `applicationId`。未改 `local.properties` 时是 `universe.agentic.device.clash`，flavor 不再加后缀 |
| namespace | 源码与资源的包名，应用模块是 `com.github.kr328.clash`，与 `applicationId` 不是同一个字段 |
| 配置（profile） | 一份可导入、可选用的 Clash 配置 |
| 覆盖（override） | 在所选配置之上的本地修改 |
| 访问控制 | 按已安装应用的包名决定哪些应用进入隧道。全局名单只在应用分流的「默认回退」里编辑 |
| 应用集合 | 访问控制里的系统应用、国内应用、浏览器和自定义集合。内置集合成员默认按当前安装计算；确认固定后改用保存的包名，直到恢复自动计算。浏览器集合包含国内和国外网页浏览器，不与另外两个内置集合重叠 |
| 分流策略 | 一份命名的访问控制配置，可被选为当前生效的名单 |
| `:background` | `service` 模块里服务与 Provider 所在的进程 |
| WebDAV 同步 | 把配置和分流策略与远程目录合并。默认常量在源码，不在本文重复 |
| 本地 MCP | 只在 `127.0.0.1` 上监听的本机工具服务。端口、路径和工具见 [本地 MCP](systems/mcp/INDEX.md) |
