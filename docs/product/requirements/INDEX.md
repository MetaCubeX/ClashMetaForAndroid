---
title: "产品需求目录"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: "每条 PRD 单独一个文件。状态 accepted 表示代码已体现，尚未做设备验收"
---

# 需求

一条需求一个文件，编号 `PRD-NNN`。本目录只写使用者能观察到的承诺。升到 `implemented` 前，要在 [追踪](../traceability.md) 记下一次对照运行或测试。

| ID | 需求 | 状态 |
|:---|:-----|:-----|
| [PRD-001](PRD-001.md) | 隧道 | accepted |
| [PRD-002](PRD-002.md) | 配置 | accepted |
| [PRD-003](PRD-003.md) | 访问控制 | accepted |
| [PRD-004](PRD-004.md) | 分流策略 | accepted |
| [PRD-005](PRD-005.md) | WebDAV | accepted |
| [PRD-006](PRD-006.md) | 外部控制 | accepted |

新需求复制 [调研模板](../../templates/demand-template.md) 的 frontmatter，`type` 仍用 `demand`，文件名用下一个编号。不要把多条需求再写回同一个文件。
