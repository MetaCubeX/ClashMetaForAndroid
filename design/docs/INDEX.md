---
title: "design 模块设计"
type: index
status: accepted
phase: N/A
updated: 2026-10-16
summary: "Design 请求通道、屏幕与 Activity 的对应，以及仍留给编辑器的 preference 控件"
---

# design 模块设计

`app` 与 `design` 的分工见 [界面](../../docs/systems/ui/INDEX.md)。这里只写本模块的结构。

| 文档 | 内容 |
|:-----|:-----|
| [屏幕与请求通道](screens.md) | `Design` 基类、请求如何回到 Activity、每个 `*Design` 对应哪个 Activity、`model` 包 |
| [设置项控件](preferences.md) | `preference` 包里每种控件做什么 |
| [Compose 屏幕](compose.md) | `*Design` 用 `ComposeView` 接上原来的请求通道；`ios` 包的主题和控件 |
| [列表适配器](adapters.md) | 还留在 `adapter` 包里的三个适配器，服务 preference 弹层 |

应用集合的点选规则见 [访问控制](../../docs/systems/access-control/INDEX.md)，不在这里重写。

模块导航：[docs/modules/design](../../docs/modules/design/INDEX.md)。介绍：[README](../README.md)。
