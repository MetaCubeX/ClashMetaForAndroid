---
title: "采用 Git + Markdown 文档系统"
type: decision
status: accepted
phase: N/A
updated: 2026-10-15
summary: "把 UniverseAgent 的文档结构移植到本仓库，并按六个 Android 模块裁剪"
---

# 001. 采用 Git + Markdown 文档系统

## Context

本仓库原来只有上游 `README.md` 和 `CONTRIBUTING.md`。构建约束、flavor、内核子模块和 fork 行为散落在对话里，下一次修改对不上权威说明。

UniverseAgent 已有一套可检查的文档系统：知识层与行动层分开、frontmatter、模块索引导航、`status.md` 行数上限，以及只读的 `check-docs-health.py`。

## Decision

采用同一套职责划分，并按本仓库改写：

- 知识层放在 `docs/`，行动层放在 `dev/`，向导放在 `AGENTS.md`。`CLAUDE.md` 只做指针。
- Gradle 模块以 `settings.gradle.kts` 为准：`app`、`core`、`service`、`design`、`common`、`hideapi`。每个模块有 `docs/modules/<name>/INDEX.md`。
- 不移植 Singularity、TUI 产品树和 `dev/loop`。
- 健康检查只读，不生成索引。`scripts/generate-docs-index.sh` 保持禁用。
- 根 `README.md` 继续作为上游英文介绍，只加文档系统入口。
- 设备序列号、证书指纹和密钥口令不进入仓库文档。

## Consequences

- 结构变更后要跑 `python3 scripts/check-docs-health.py`。缺模块索引或 `status.md` 超长会使检查失败。
- 用户可见行为要更新 `docs/systems/` 或 `docs/product/`，不能只写在 `status.md`。
- 上游 README 里的 OpenJDK 11 步骤与当前 Java 21 目标并存。构建以 `docs/guides/getting-started/build.md` 为准。

## Alternatives Considered

- **只加长根 README**：构建和 fork 行为会跟上游介绍缠在一起，也没有模块导航和检查。
- **原样复制 UniverseAgent 的 docs 树**：里面的引擎、集群和客户端文档不是这个应用的事实。
