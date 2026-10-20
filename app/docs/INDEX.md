---
title: "app 模块设计"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: "应用进程的 Activity、对 :background 的调用，以及日志捕获"
---

# app 模块设计

`app` 是默认进程里的界面入口。屏幕在 `design`，隧道、配置库和 WebDAV 实现在 `service` 的 `:background` 进程。本目录只写这个模块自己怎么把两边接起来。

| 文档 | 写什么 |
|:-----|:-------|
| [Activity](activities.md) | `BaseActivity` 如何把界面交给 `design`，各 Activity 的职责，外部控制与主界面的差别 |
| [远程调用](remote.md) | 应用进程怎样绑定 `:background`，以及状态、文件、偏好和启停各走哪条路 |
| [日志捕获](logcat.md) | `LogcatService` 到 `LogcatReader` 的实际链路，以及崩溃页用的系统 logcat |

模块导航在 [docs/modules/app/INDEX.md](../../docs/modules/app/INDEX.md)。

跨模块行为以这些文档为准，这里不重写：

| 主题 | 文档 |
|:-----|:-----|
| 界面分层 | [界面](../../docs/systems/ui/INDEX.md) |
| 隧道与外部控制动作 | [运行时](../../docs/systems/runtime/INDEX.md) |
| 配置 | [配置](../../docs/systems/profiles/INDEX.md) |
| 访问控制与分流策略 | [访问控制](../../docs/systems/access-control/INDEX.md) |
| WebDAV | [WebDAV](../../docs/systems/webdav/INDEX.md) |
| 用户可观察的承诺 | [产品需求](../../docs/product/requirements/INDEX.md) |
