---
title: "架构概览"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-15
summary: "六个 Gradle 模块的依赖，以及 UI 进程与 :background 进程"
---

# 架构概览

原则见 [DESIGN.md](../../DESIGN.md)。术语见 [术语表](../glossary.md)。

```
app  ──►  design ──► service ──► core ──► common
 │          │          │                    ▲
 │          └──────────┴────────────────────┘
 └── compileOnly hideapi ◄── common
```

`app` 直接依赖 `core`、`service`、`design`、`common`。`design` 依赖 `core`、`service`、`common`，因为列表和设置需要配置与隧道模型。`service` 依赖 `core` 和 `common`。`hideapi` 只在编译期提供隐藏 API 存根。

## 进程与原生库

`:background` 进程承载 `ClashService`、`TunService`、`RemoteService`、`ProfileWorker`，以及文件、状态、偏好 Provider。声明见 `service/src/main/AndroidManifest.xml`。

`core` 用 golang-android 把 `core/src/main/golang` 和 mihomo 子模块编成 `libclash.so`，JNI 入口在 `core/src/main/cpp`。两个 flavor 都使用 `src/foss/golang`，Go build tag 为 `foss`、`with_gvisor`、`cmfa`。

## 版本与兼容

根 `build.gradle.kts` 把 `minSdk` 设为 21，`targetSdk` 设为 35，`versionName` 为 `2.11.35`，`versionCode` 为 `211035`。编译目标是 Java 21。NDK 版本与 ABI 过滤见 [构建指南](../guides/getting-started/build.md)。

## 相关系统

| 主题 | 文档 |
|:-----|:-----|
| 隧道与外部控制 | [运行时](../systems/runtime/INDEX.md) |
| 配置生命周期 | [配置](../systems/profiles/INDEX.md) |
| 按应用分流 | [访问控制](../systems/access-control/INDEX.md) |
| 同步 | [WebDAV](../systems/webdav/INDEX.md) |
| 界面分层 | [界面](../systems/ui/INDEX.md) |
