---
title: "文档流程改以 ProjectWiki 共享规则为准"
type: decision
status: accepted
phase: N/A
updated: 2026-11-04
summary: "流程正文只保留 scripts/wiki/workflow.md；目录、双检查和 dev/archive 继续沿用"
---

# 文档流程改以 ProjectWiki 共享规则为准

状态：当前有效。

## Context

[001](git-markdown-docs.md) 已经决定用 Git + Markdown，把知识放在 `docs/`、过程放在 `dev/`，并用只读检查看模块索引和链接。后来的维护规则又要求每次新会话通读一份长规范、每次实施改写 `status.md`，以及把方案提交和实现提交拆开。

ProjectWiki 把需求到验证、决定同步、审查和归档写成一份给接入项目用的 [workflow.md](../../scripts/wiki/workflow.md)。接入规则要求删掉与它重复或冲突的旧要求，不能因为旧规则更严就两套一起执行。

## Decision

- 文档开发流程以 `scripts/wiki/workflow.md` 为准。`AGENTS.md` 只保留忽略后会直接做错的规则，并链接这篇流程。
- 001 的目录职责、六个 Gradle 模块的导航、只读检查，以及不把密钥写入文档，继续有效。本篇不替代 001。
- `check-docs-health.py` 与 `wiki.py check` 都保留。前者读 frontmatter、模块表和快照行数，后者读链接、状态和需求编号。
- 主归档根继续是已登记的 `dev/archive/`，不另建仓库根的 `archive/`。
- frontmatter 的 `status` 登记为 `status_frontmatter`。计划和决策在标题下另有状态行时，以状态行为准。
- `dev/progress/status.md` 只在跨计划的协调事实变化时更新。
- `adopted_at` 只指向已经存在的接入提交。这次工具和入口改造还没有单独提交，所以配置里先不写这个字段。

## Consequences

- 代理不再为了旧的「会话结束自检」去追加 `status.md`。
- 每个克隆仍要自己执行一次 `git config core.hooksPath scripts/wiki/hooks`。本决定不修改 git config。
- `scripts/wiki/` 与 ProjectWiki 源仓库的当前工具包对齐，包括 `workflow.md` 和五份技能。升级前的本地副本是旧版本，没有需要保留的补丁。
- 模板仍留在 `docs/templates/`，等可以单独改链接时再搬。

## Alternatives Considered

- **保留原维护规则作为第二套流程**：每次任务都要判断听哪一份，和共享流程叠加。
- **把历史迁到仓库根 `archive/`**：目录已经登记，批量改链接会碰到工作区里无关的未提交改动。
- **去掉 `check-docs-health.py`**：`wiki check` 不读 Gradle 模块表，也不读快照行数。
