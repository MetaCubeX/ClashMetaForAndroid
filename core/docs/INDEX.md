---
title: "core 模块设计"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: "Kotlin 桥、JNI 边界和 golang/native 包布局"
---

# core 设计

这里只写 `core` 自己的桥：Kotlin 对象、JNI，以及 `core/src/main/golang/native`。mihomo 协议在子模块里，不在这里展开。

进程里谁加载这条桥，见 [运行时](../../docs/systems/runtime/INDEX.md)。构建、flavor 和子模块 pin 见 [构建指南](../../docs/guides/getting-started/build.md)。模块入口表见 [modules/core](../../docs/modules/core/INDEX.md)。

| 文档 | 内容 |
|:-----|:-----|
| [桥接边界](bridge.md) | `Clash`、`Bridge`、JNI 与 Go 导出函数的分界 |
| [Go 包布局](native-layout.md) | `golang/native` 各目录做什么 |
| [Kotlin 模型](models.md) | `model` 包的公开类型 |

源码位置仍以 [README](../README.md) 为准。
