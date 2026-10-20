---
title: "service 模块设计"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: "service 模块内部的进程、ClashRuntime 和持久化面"
---

# service 模块设计

这里只写 `service` 模块自己的结构。用户可见的运行、配置、访问控制和 WebDAV 规则在系统文档里，不在这里重写。

| 主题 | 文档 |
|:-----|:-----|
| 隧道与外部控制 | [运行时](../../docs/systems/runtime/INDEX.md) |
| 配置的导入与当前选择 | [配置](../../docs/systems/profiles/INDEX.md) |
| 名单与分流策略 | [访问控制](../../docs/systems/access-control/INDEX.md) |
| 同步行为 | [WebDAV](../../docs/systems/webdav/INDEX.md) |

| 文档 | 内容 |
|:-----|:-----|
| [进程与远程接口](process.md) | `:background` 里的 Service、Provider、Receiver，以及谁绑定谁 |
| [文件提供者](documents.md) | `FilesProvider` 的文档树：`config.yaml`、`providers`，以及查询、打开和删除 |
| [运行时模块](runtime.md) | `ClashRuntime` 如何装上 `clash/module`，各模块做什么 |
| [配置库与 ServiceStore](profiles-store.md) | Room 实体、配置目录，以及 `ServiceStore` 的字段和文件 |

源码在 `service/src/main/java/com/github/kr328/clash/service/`。进程声明在 `service/src/main/AndroidManifest.xml`。模块介绍见 [README](../README.md)。
