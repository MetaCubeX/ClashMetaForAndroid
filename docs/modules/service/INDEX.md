---
title: "service 模块导航"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: "后台进程、配置库、ServiceStore 与 WebDAV"
---

# service

介绍见 [README](../../../service/README.md)。

| 入口 | 路径 |
|:-----|:-----|
| 进程声明 | `service/src/main/AndroidManifest.xml` |
| 隧道服务 | `TunService.kt`、`ClashService.kt` |
| 偏好与策略文件 | `store/ServiceStore.kt` |
| 配置 | `ProfileManager.kt` |
| WebDAV | `webdav/` |
| 单元测试 | `service/src/test/java/.../webdav/` |

模块设计：

| 文档 | 内容 |
|:-----|:-----|
| [设计索引](../../../service/docs/INDEX.md) | 本模块设计入口 |
| [进程与远程接口](../../../service/docs/process.md) | `:background` 组件、绑定关系和 Binder |
| [运行时模块](../../../service/docs/runtime.md) | `ClashRuntime` 与 `clash/module` |
| [配置库与 ServiceStore](../../../service/docs/profiles-store.md) | Room 实体、配置目录和 `ServiceStore` |

所属系统：[运行时](../../systems/runtime/INDEX.md)、[配置](../../systems/profiles/INDEX.md)、[WebDAV](../../systems/webdav/INDEX.md)、[访问控制](../../systems/access-control/INDEX.md)。
