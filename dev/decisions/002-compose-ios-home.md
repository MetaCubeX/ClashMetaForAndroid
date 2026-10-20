---
title: "首页和设置改用 Compose，视觉按 iOS 人机界面指南"
type: decision
status: accepted
phase: N/A
updated: 2026-10-16
summary: "显示名改为 ClashMetaX。用 Jetpack Compose 重排首页和设置，首页加上模式、速率和当前节点，不改包名，也不引入 Cupertino 库"
---

# 002. 首页和设置改用 Compose，视觉按 iOS 人机界面指南

## Context

原来的首页把连接、代理、配置、日志、设置、帮助和关于排成同一权重的卡片。模式藏在代理页菜单里，代理入口在核心停止时直接消失。

仓库当时没有 Compose。Jetpack Compose 和 Compose Multiplatform 都没有官方 Cupertino 组件。第三方 compose-cupertino 面向 Kotlin Multiplatform，并带有 SF Symbols 的许可限制。本应用只做 Android。

## Decision

- 用户可见的应用名改为 ClashMetaX。`applicationId`、URL scheme 和外部控制 action 保持不变。
- 在 `design` 模块启用 Jetpack Compose（BOM `2025.02.00`，Kotlin `2.1.0`）。
- 首页和设置的视觉按 Apple Human Interface Guidelines：分组列表、系统蓝、大标题、44dp 点按。模式是三个等宽按钮。控件写在 `design/.../design/ios/`，不依赖第三方 Cupertino 库。
- 模式切换放在首页，写入会话 override，与代理页菜单共用同一次 `patchOverride`。
- Activity 与 `service` 的请求关系保留。其余屏幕继续用现有 XML，按同一套控件逐屏替换。

## Consequences

- 已安装用户可以覆盖升级。桌面名称、通知和关于页会变成 ClashMetaX。
- 首页的信息权重变了：帮助和关于不再和连接并列。
- 模式切换会提示只对当前会话有效。
- 代理、配置、日志和各设置子页在换掉之前仍是原来的 Material 列表。
- `minSdk` 仍是 21。Compose 依赖只加在 `design`。

## 后续

2026-10-15：首页参考 Clash Verge Rev 的仪表盘，只取手机上成立的三块。模式按钮下加一行说明。隧道运行时显示上下行速率和最近采样的折线，并显示一个策略组的当前节点。没有增加系统代理和 TUN 两套开关。

界面进程不能直接调用核心的 `Clash.queryTrafficNow()`。`IClashManager` 增加同名方法，由 `ClashManager` 转到已有的核心查询。累计流量仍用 `queryTrafficTotal()`。

2026-10-16：其余用户可见屏幕按 [分组交互方案](../plans/compose-remaining-screens.md) 换成同一套 Compose 列表。关于对话框和文本、列表编辑器留在原来的布局。页面级 XML 已删除。没有装到手机上，也没有打 release 包。

## Alternatives Considered

- **引入 compose-cupertino**：组件更像 UIKit，但不是官方库，还要把一个 KMP 组件集拉进仅 Android 的工程。
- **整包改成 Material 3**：和这次要的 iOS 层级、分组列表和系统色不是同一套语言。
- **同时改 applicationId**：会让已安装用户无法覆盖升级，并断开现有的外部控制 action。
