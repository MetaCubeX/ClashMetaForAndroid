---
title: "开发入口"
type: index
status: accepted
phase: N/A
updated: 2026-11-04
summary: "任务从计划和需求进入；多代理协调用并行看板"
---

# 开发入口

状态：当前有效。

流程见 [scripts/wiki/workflow.md](../scripts/wiki/workflow.md)。本仓库的目录和 frontmatter 约定见 [项目适配](../docs/DOCUMENTATION.md)。

- 任务步骤在 [plans/](plans/)。接续一项工作时，先打开那份计划，不先通读快照。
- 多代理或跨会话分工的占用写在 [parallel/active/](parallel/INDEX.md)。单人单任务不新建看板。
- 方向与阶段：[roadmap/INDEX.md](roadmap/INDEX.md)。按编号或关键词检索，不整篇当作当前规范。
- 架构决策在 [docs/decisions/](../docs/decisions/INDEX.md)。`dev/decisions/INDEX.md` 只指向那里。
- 跨计划的协调事实在 [progress/status.md](progress/status.md)。只在这类事实变化时更新，上限 200 行。

## 活跃工作

| 主题 | 入口 |
|:-----|:-----|
| ProjectWiki 文档系统接入 | [parallel/active/projectwiki-adoption.md](parallel/active/projectwiki-adoption.md) |
| 壳层四条边界提交 | [parallel/active/shell-commit-boundaries.md](parallel/active/shell-commit-boundaries.md) |
| 跨客户端 WebDAV 实施 | [parallel/active/webdav-cross-client-implementation.md](parallel/active/webdav-cross-client-implementation.md) |

已结束看板在 [parallel/archive/](parallel/archive/)。过时材料用 `wiki.py archive` 进入 [archive/](archive/)。

## 记录分工

本仓库没有单独的执行套件。下面是实际在用的来源，不另建收件箱或状态协议。

| 角色 | 实际来源 |
|:-----|:---------|
| 任务来源 | [plans/](plans/) 里状态为执行中的计划，或 [产品需求](../docs/product/requirements/INDEX.md) 的 `PRD-NNN`。用文件路径或需求编号识别。 |
| 执行与协调记录 | 计划正文记录步骤和验证。多人同时改同一范围时，占用写在 [parallel/active/](parallel/)。跨计划的当前事实写在 [progress/status.md](progress/status.md)。 |
| 待处理项来源 | 未采用 `dev/inbox/`。拿不准要不要做的事项写进相关计划；还没有计划时写在 [demand/](demand/INDEX.md)，不直接改 `docs/`。 |
| 验证入口 | `python3 scripts/wiki/wiki.py verify --root .`。构建和测试命令登记在 `docs/wiki.json`；计划里的验证记录引用这次输出。 |
| 归档约定 | 计划或看板状态改为已完成、剩余步骤各有去向之后，用 `wiki.py archive` 归档。主根是 `dev/archive/<原相对路径>`。归档后从本页的活跃表删掉链接。 |

开始任务时运行 `python3 scripts/wiki/wiki.py remind --root .`。
