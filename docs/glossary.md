---
title: "术语表"
type: concept
status: accepted
phase: N/A
updated: 2026-10-15
summary: "本仓库文档里的用词。定义以这里为准"
---

# 术语表

| 术语 | 含义 |
|:-----|:-----|
| mihomo | MetaCubeX 的 Clash Meta 内核。本仓库用 Git 子模块引入 |
| flavor | `alpha` 与 `meta` 两个 product flavor。默认是 `alpha` |
| 应用包名 | `applicationId`。未改 `local.properties` 时基名是 `com.github.metacubex.clash`，再加 flavor 后缀 |
| namespace | 源码与资源的包名，应用模块是 `com.github.kr328.clash`，与 `applicationId` 不是同一个字段 |
| 配置（profile） | 一份可导入、可选用的 Clash 配置 |
| 覆盖（override） | 在所选配置之上的本地修改 |
| 访问控制 | 按已安装应用的包名决定哪些应用进入隧道 |
| 应用集合 | 访问控制列表顶部的系统应用集合与国内应用集合。成员按当前安装动态计算 |
| 分流策略 | 一份命名的访问控制配置，可被选为当前生效的名单 |
| `:background` | `service` 模块里服务与 Provider 所在的进程 |
| WebDAV 同步 | 把配置和分流策略与远程目录合并。默认常量在源码，不在本文重复 |
