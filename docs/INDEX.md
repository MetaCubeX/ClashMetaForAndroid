---
title: "文档索引"
type: concept
status: accepted
phase: N/A
updated: 2026-10-15
summary: "全局导航。模块列表与 settings.gradle.kts 的 include 一一对应，设计文档在各模块 docs/"
---

# 文档索引

> 人工维护。结构变化后同步本文件，并运行 `python3 scripts/check-docs-health.py`。[文档介绍](README.md)

## 快速入口

| 我想... | 去这里 |
|:--------|:-------|
| 看产品承诺 | [产品](product/INDEX.md) |
| 看模块怎么分 | [架构概览](architecture/overview.md) |
| 看一个模块的入口 | [模块列表](#模块列表) |
| 看构建和测试 | [构建指南](guides/getting-started/build.md) |
| 看当前快照 | [status.md](../dev/progress/status.md) |
| 看架构决策 | [ADR](../dev/decisions/INDEX.md) |

## 模块列表

| 模块名 | 职责 | 介绍 | 设计文档 |
|:-------|:-----|:-----|:---------|
| app | Activity、外部控制、日志 | [README](../app/README.md) · [导航](modules/app/INDEX.md) | [docs](../app/docs/INDEX.md) |
| common | 常量、Store、兼容工具 | [README](../common/README.md) · [导航](modules/common/INDEX.md) | [docs](../common/docs/INDEX.md) |
| core | JNI / Go 桥与 libclash.so | [README](../core/README.md) · [导航](modules/core/INDEX.md) | [docs](../core/docs/INDEX.md) |
| design | 界面布局与列表控件 | [README](../design/README.md) · [导航](modules/design/INDEX.md) | [docs](../design/docs/INDEX.md) |
| hideapi | 隐藏 API 编译期存根 | [README](../hideapi/README.md) · [导航](modules/hideapi/INDEX.md) | [docs](../hideapi/docs/INDEX.md) |
| service | 后台服务、配置库、WebDAV | [README](../service/README.md) · [导航](modules/service/INDEX.md) | [docs](../service/docs/INDEX.md) |

行动层快照：[dev/progress/status.md](../dev/progress/status.md)。目录说明：[dev/README.md](../dev/README.md)。

## 系统文档

| 系统 | 索引 | 说明 |
|:-----|:-----|:-----|
| 运行时 | [索引](systems/runtime/INDEX.md) | `:background` 里的隧道、启停与 native 核心 |
| 配置 | [索引](systems/profiles/INDEX.md) | 导入、提交、选择、覆盖 |
| 访问控制 | [索引](systems/access-control/INDEX.md) | 包名名单、应用集合、分流策略 |
| WebDAV | [索引](systems/webdav/INDEX.md) | 拉取、推送与合并 |
| 界面 | [索引](systems/ui/INDEX.md) | `app` 与 `design` 的分工 |

## 分类索引

| 类别 | 链接 |
|:-----|:-----|
| 架构 | [概览](architecture/overview.md) · [DESIGN.md](../DESIGN.md) · [ADR](../dev/decisions/INDEX.md) |
| 产品 | [入口](product/INDEX.md) |
| 指南 | [构建](guides/getting-started/build.md) · [单元测试](guides/getting-started/testing.md) |
| 参考 | [术语表](glossary.md) |
| 规范 | [DOCS-SPEC](DOCS-SPEC.md) · [维护规则](DOCUMENTATION.md) |
