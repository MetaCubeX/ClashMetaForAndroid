---
title: "design 模块"
type: overview
status: accepted
phase: N/A
updated: 2026-10-16
summary: "Compose 屏幕，以及从 AgenticUILibrary 复制的 iOS 控件"
---

# design

界面实现。Activity 在 `app`。本模块的 `*Design` 用 Jetpack Compose 画屏幕，文件在 `src/main/java/.../design/ios/`。`res/layout` 只留下关于对话框、文本和列表编辑器、订阅二维码、age 密钥和拉取进度。

列表行、分组偏好行、开关和搜索框复制自 AgenticUILibrary（Apache-2.0），源码在 `src/main/java/nota/ui` 和 `src/main/java/universe/agentic/ui/search`。屏幕只调用 `design/ios` 里的 `Ios*`。没有把这个库加进 Gradle。见仓库根目录 `NOTICE`。

导航：[docs/modules/design/INDEX.md](../docs/modules/design/INDEX.md)

模块设计：[docs/INDEX.md](docs/INDEX.md)
