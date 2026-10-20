---
title: "文档系统维护规则"
type: concept
status: accepted
phase: N/A
updated: 2026-10-15
summary: "frontmatter、提交前文档门禁、ADR 与阶段日志、产品需求更新顺序"
---

# 文档系统维护规则

> **受众**：在本仓库里改代码的人和代理。
> **结构**：目录树和健康检查见 [DOCS-SPEC.md](DOCS-SPEC.md)。本文件只写维护行为。

## 1. 目录职责

| 区域 | 路径 | 职责 |
|:-----|:-----|:-----|
| 知识库 | `docs/`、`<module>/docs/` | 相对稳定的行为与设计 |
| 产品需求 | `docs/product/` | 用户可观察的承诺 |
| 行动层 | `dev/` | 计划、ADR、当前快照 |
| 归档 | `dev/archive/` | 过时文档只移入，不删除 |
| 根向导 | `AGENTS.md`、`CLAUDE.md` | 豁免 frontmatter |

五类职责见 [DOCS-SPEC §3](DOCS-SPEC.md#3-目录)。

| 原则 | 说明 |
|:-----|:-----|
| 稳定与动态分离 | `docs/` 少改，`dev/progress/status.md` 随实施更新 |
| 单一事实源 | 用链接，不复制正文 |
| 决策可追溯 | 架构取舍写 ADR，与阶段日志分开 |
| 只归档，不删除 | 过时文件移入 `dev/archive/` |
| 快照不是流水账 | `status.md` ≤200 行，且必须有快照正文 |
| 向导最高 | 文档与 `AGENTS.md` 冲突时改文档。`CLAUDE.md` 只是指针 |

新会话阅读顺序：`AGENTS.md` → 本文件 → `dev/progress/status.md`。

## 2. Frontmatter

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

可选 `created: YYYY-MM-DD`。

豁免：`AGENTS.md`、`CLAUDE.md`、根 `README.md`。`dev/archive/` 下的存量文件不强制补 frontmatter。

`type: decision` 用于 ADR。不要新增 `adr` 或 `parallel`。看板文件使用 `roadmap`。

`phase` 填 `N/A`，除非文档确实对应一个编号阶段。

状态常用路径：`draft` → `review` → `accepted` → `implemented` → `completed`，过时则为 `archived`。

## 3. 维护规则

### 规则 1：新会话三步走

按顺序读 `AGENTS.md`、本文件、`dev/progress/status.md`。

### 规则 2：编码前查阅

看对应 `docs/systems/` 或 `docs/modules/`，看 `status.md`，并搜索 `dev/decisions/` 里是否已有 ADR。

### 规则 3：编码后更新快照

实施会话结束时，把本次结果写入 `status.md` 的当前快照。有 roadmap 时勾选对应项。

### 规则 3a：提交前文档门禁

代码与文档在同一次主题提交里对齐。不要先合并代码、以后再补文档。

| 层级 | 位置 | 何时更新 |
|:-----|:-----|:---------|
| 行动层 | `dev/` | 有行为变化的实施提交之前 |
| 知识层 | `docs/`、`<module>/docs/` | 用户可见行为、契约或构建方式变了 |

```
问：本次提交包含代码吗？
├── 否 → 更新改过文件的 updated；动了目录就更新索引并跑健康检查
└── 是
    ├── 无行为变化的单行修正？ → 可以只提交代码
    └── 否 → 更新行动层，再判断知识层
        ├── 用户可见行为或对外承诺变了？ → 按规则 10a
        ├── 跨模块接口变了？ → 更新系统文档或模块索引，必要时 ADR
        ├── 只是内部整理或测试？ → 行动层通常够了
        └── 文档目录有增删移动？ → 更新索引并跑健康检查
```

实施提交的最小清单：

- [ ] `dev/progress/status.md` 写了本次结果，总行数 ≤ 200，且有快照正文
- [ ] 活跃 roadmap 的对应项已勾选（若有）
- [ ] 改过的规格 `updated` 与 `status` 与实现一致
- [ ] 动过索引或目录后已运行 `python3 scripts/check-docs-health.py`

### 规则 3b：提交时保留其他未提交改动

只 `git add` 本次主题的路径。提交前用 `git status` 和 `git diff --cached` 核对暂存范围。工作区里其他改动留在原地。

不要为了得到干净工作区而对未纳入本次提交的文件执行 `git restore`、`git reset --hard`、`git clean` 或 `git stash`。存在无关改动时不要 `git add -A`。同一文件里两套改动冲突时，不要静默还原。

### 规则 4：ADR 与阶段日志

写入 `dev/decisions/` 的条件：新的架构概念、两个方案里选一个、跨模块契约、推翻已有 ADR。

写入 `dev/iterations/<subsystem>/` 的条件：一次实现的过程、踩坑、测试或构建脚本变更，且不构成上面的决策。

### 规则 5：ADR 写法

文件名 `dev/decisions/NNN-short-name.md`。正文用 [决策模板](templates/decision-template.md)。`accepted` 之后不改原文。要推翻时新建 ADR，并在后果里指向旧编号。

### 规则 6：阶段日志

文件名 `dev/iterations/<subsystem>/phase-NNN-topic.md`。不要把阶段日志放进 `dev/decisions/`。

### 规则 7：归档

过时文档移入 `dev/archive/`，`status` 改为 `archived`，并更新受影响的索引。

### 规则 8：向导优先

文档与 [AGENTS.md](../AGENTS.md) 冲突时，改文档。要改变向导里的项目约束，先跟用户确认。`CLAUDE.md` 不是第二份向导。

### 规则 9：Frontmatter 随改随新

新建文件带齐必填字段。每次编辑知识层或行动层文档，更新 `updated`。状态变了就改 `status`。

### 规则 10：不重复

新建文档前先搜索。构建命令、WebDAV 默认值和模块职责只在权威文件里写一次。

不要把设备序列号、证书指纹、密钥口令写进仓库。

### 规则 10a：产品需求与设计

`DESIGN.md` 与 `docs/systems/` 描述系统怎样工作。`docs/product/` 只记录用户能观察到的承诺。

行为变更的顺序：

1. 更新 `DESIGN.md`、系统文档或 ADR。
2. 实现与文档一致。
3. 对外承诺变了，再改 `docs/product/requirements/PRD-NNN.md` 和 `traceability.md`。一条需求一个文件，并更新 `requirements/INDEX.md`。

需求编号 `PRD-NNN`。状态用 `proposed`、`accepted`、`implemented`、`blocked`。没有一次对照运行或测试的记录，不要标 `implemented`。源码里已经有的行为先标 `accepted`，追踪表指向体现它的代码。

产品文件的 `type`：`INDEX.md` 用 `index`，`vision.md` 用 `concept`，`requirements/PRD-NNN.md` 用 `demand`，`traceability.md` 用 `reference`。

`dev/demand/` 只放调研，不升级成产品承诺。根 `README.md` 不是验收标准。

### 规则 11：索引随结构变更

新增、移动或删除文档后，更新受影响的 `INDEX.md`，并运行：

```bash
python3 scripts/check-docs-health.py
```

不要运行 `scripts/generate-docs-index.sh` 来生成索引。该脚本会直接退出。

### 规则 12：不要额外制造 README

系统规定的入口可以有 README：根 `README.md`、`docs/README.md`、`dev/README.md`，以及每个 Gradle 模块根上的 `README.md`。除此之外不新建 README，除非用户明确要求。

### 规则 13：方案与实施分开

方案提交与实现提交分开。两类提交都遵守规则 3a 和 3b。无行为变化的小修正可以跳过方案。

### 规则 14：会话结束自检

- [ ] `status.md` 反映本次结果，并且不超过 200 行
- [ ] 改过的 Markdown 已更新 `updated`
- [ ] 新文件有 frontmatter（豁免文件除外）
- [ ] 动过结构或索引时跑过健康检查
- [ ] 没有把其他主题的未提交改动卷进本次提交

### 规则 15：并行看板

同时改很多文件、多个代理分工，或工作要跨会话接着做时，在 `dev/parallel/active/<topic>.md` 建看板，用 [并行模板](templates/parallel-template.md)。全部完成后移入 `dev/parallel/archive/`，并更新 [parallel/INDEX.md](../dev/parallel/INDEX.md)。

Roadmap 是事先拆好的阶段清单。看板是执行中的任务状态。

## 4. 检索

| 我想... | 去哪里 |
|:--------|:------|
| 看产品承诺 | `docs/product/INDEX.md` |
| 看项目约束 | `AGENTS.md` |
| 看当前在做什么 | `dev/progress/status.md` |
| 看一个跨模块行为 | `docs/systems/<system>/INDEX.md` |
| 看一个模块从哪读 | `docs/modules/<module>/INDEX.md` |
| 看为什么这样选 | `dev/decisions/INDEX.md` |
| 看怎样构建 | `docs/guides/getting-started/build.md` |
| 检查文档 | `python3 scripts/check-docs-health.py` |
