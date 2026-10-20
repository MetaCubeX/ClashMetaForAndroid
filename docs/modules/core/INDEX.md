---
title: "core 模块导航"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: "JNI、Go 桥的模块设计与源码入口"
---

# core

介绍见 [README](../../../core/README.md)。模块设计见 [core/docs](../../../core/docs/INDEX.md)。

| 文档 | 内容 |
|:-----|:-----|
| [桥接边界](../../../core/docs/bridge.md) | `Clash`、`Bridge`、JNI 与 Go 导出 |
| [Go 包布局](../../../core/docs/native-layout.md) | `golang/native` 各目录 |

| 入口 | 路径 |
|:-----|:-----|
| Kotlin 桥 | `core/src/main/java/com/github/kr328/clash/core/` |
| JNI / CMake | `core/src/main/cpp/` |
| Go 桥 | `core/src/main/golang/` |
| mihomo 子模块 | `core/src/foss/golang/clash` |

构建与子模块 pin 见 [构建指南](../../guides/getting-started/build.md)。运行时角色见 [运行时](../../systems/runtime/INDEX.md)。
