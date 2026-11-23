---
title: "文档系统的项目适配"
type: concept
status: accepted
phase: N/A
updated: 2026-11-04
summary: "共享流程未覆盖、本仓库仍然执行的文档约定"
---

# 文档系统的项目适配

状态：当前有效。

目录树见 [DOCS-SPEC.md](DOCS-SPEC.md)。检查范围和预算见 [wiki.json](wiki.json)。需求到验证的顺序、决定变化时同步哪些文档、审查分工和归档时机，以 [scripts/wiki/workflow.md](../scripts/wiki/workflow.md) 为准。本文件只写共享流程没有覆盖、本仓库仍然要执行的约定。

## 不再执行的旧要求

这些要求曾经写在本文，并和共享流程重复或冲突。不要再执行：

- 不要再把每次新会话的必读顺序写成向导、本文和 `dev/progress/status.md`。
- 不要再在每次实施结束或每次提交时改写 `status.md`。
- 不要再把「先交方案、再交实现」当成固定手续。
- 不要再把「提交前先更新行动层」当成每次行为变更的固定步骤。行为变化在同一次改动里更新已经失实的现行知识；需要多步骤协作、交接或交代重要风险时才写计划。

## Frontmatter

共享流程不要求 frontmatter。本仓库仍要求，因为 [scripts/check-docs-health.py](../scripts/check-docs-health.py) 用它检查字段是否齐全，并同时检查 Gradle 模块索引和 `status.md` 行数。`wiki check` 不读 `settings.gradle.kts`，也不读这 200 行上限。两项检查的输入不同，所以都要通过，不能互相替代。

除豁免文件外，每个 `.md` 以 YAML frontmatter 开头：

```yaml
---
title: "人类可读的标题"
type: architecture | concept | decision | reference | roadmap | plan | progress | demand | index | design | spec | guide | overview
status: draft | review | accepted | implemented | completed | archived | active | proposed | in_progress | partially_completed | deprecated | paused | ready | superseded
phase: N/A
updated: YYYY-MM-DD
summary: "一行摘要"
---
```

可选 `created: YYYY-MM-DD`。`phase` 填 `N/A`，除非文档确实对应一个编号阶段。

豁免：`AGENTS.md`、`CLAUDE.md`、根 `README.md`，以及 `SKILL.md`。`dev/archive/` 下的存量文件不强制补 frontmatter。

`docs/wiki.json` 把 `status_frontmatter` 登记为 `status`。计划和决策没有状态行时，工具从这里取状态；两处都写时以标题下的状态行为准。取值和类别的对应关系写在 `status_values`，不在正文再维护一份。

新建或改过的知识层、行动层文档要更新 `updated`。

## 计划目录里的两份目录页

`dev/plans/INDEX.md` 和 `dev/plans/overview.md` 是目录，不是一份会结束的计划。`remind.plans` 因此写成 `dev/plans/*-*.md`，只匹配带连字符的主题文件。新计划沿用这种文件名，例如 `webdav-cross-client-sync.md`，不要写成 `dev/plans/sync.md`。

`ui-web-demo/` 是界面原型，不在知识根里。`docs/wiki.json` 的 `exclude` 排除了它，检查不读那里的链接。原型结论要写回 `docs/` 或 `dev/plans/` 才算现行说明。

## 归档根

主归档根是 `dev/archive/`，已在 `wiki.json` 的 `archive_root` 登记。这是目录配置，不是另一套归档流程。新归档不要放到仓库根的 `archive/`。移动和改链接只用 `wiki.py archive` 预览后再 `--apply`。

## 协调快照

`dev/progress/status.md` 继续保留，上限 200 行，且必须有快照正文。行数由 `check-docs-health.py` 检查。

共享流程只在已有计划承担不了全局协调时保留短状态页。本仓库同时有多条跨会话轨道，这份快照还承担「现在哪几件事在进行」的协调，所以保留。只有跨计划的协调事实变了才改它。任务步骤、验证命令和完成情况留在对应计划或 `dev/parallel/active/` 看板里。

溢出后注用 `bash scripts/archive-status-overflow.sh` 滚入归档。

## 产品需求的文件布局

一条需求一个文件，编号 `PRD-NNN`，放在 `docs/product/requirements/`。`wiki.json` 的 `requirements.id_pattern` 登记的就是这个格式，不要为了迎合默认的 `APP-01` 改号。

用户能观察到的承诺变了，在同一次改动里更新对应 PRD。系统怎样工作的变化，更新 `DESIGN.md` 或 `docs/systems/`。`dev/demand/` 只放调研，不升级成产品承诺。根 `README.md` 不是验收标准。

产品文件的 `type`：`INDEX.md` 用 `index`，`vision.md` 用 `concept`，`requirements/PRD-NNN.md` 用 `demand`，`traceability.md` 用 `reference`。

## 入口与模板

系统规定的入口可以有 README：根 `README.md`、`docs/README.md`、`dev/README.md`，以及每个 Gradle 模块根上的 `README.md`。除此之外不新建 README，除非用户明确要求。这条约束防止索引入口随轮次增加；共享流程没有等价检查。

模板仍放在 [docs/templates/](templates/)。`orphan_ignore` 排除了这个目录，避免把模板当成现行知识。本次不把它们搬出 `docs/`：工作区里还有无关的未提交改动，批量改链接会卷进去。新模板继续放在这里，等单独一次只做搬家的改动再用 `wiki.py move`。

## 提交范围

只 `git add` 本次主题的路径。提交前用 `git status` 和 `git diff --cached` 核对暂存范围。不要为了得到干净工作区而对未纳入本次提交的文件执行 `git restore`、`git reset --hard`、`git clean` 或 `git stash`。存在无关改动时不要 `git add -A`。

不要把设备序列号、证书指纹、密钥口令写进仓库。构建命令、WebDAV 默认值和模块职责只在权威文件里写一次。

## 检索

| 我想... | 去哪里 |
|:--------|:------|
| 看开发流程 | `scripts/wiki/workflow.md` |
| 看项目约束 | `AGENTS.md` |
| 看目录结构 | `docs/DOCS-SPEC.md` |
| 看产品承诺 | `docs/product/INDEX.md` |
| 看正在做的任务 | `dev/INDEX.md` |
| 看一个跨模块行为 | `docs/systems/<system>/INDEX.md` |
| 看为什么这样选 | `docs/decisions/INDEX.md` |
| 检查文档 | `python3 scripts/wiki/wiki.py verify --root .` |
