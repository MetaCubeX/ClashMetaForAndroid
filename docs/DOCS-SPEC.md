---
title: "文档系统设计规范"
type: concept
status: accepted
phase: N/A
updated: 2026-10-15
summary: "本仓库的 Git + Markdown 文档结构：产品、架构、系统、模块导航、行动层与健康检查"
---

# 文档系统设计规范

> **来源**：目录职责、frontmatter 和只读健康检查移植自 UniverseAgent 的文档系统，并按本仓库的 Android 模块改写。
> **维护行为**（提交门禁、ADR、归档）：[DOCUMENTATION.md](DOCUMENTATION.md)。本文件只写结构。

## 1. 原则

- **单一事实源**：每个行为、接口和构建约束只在一份文件里定义，其他地方用链接。
- **文档与代码同库**：Markdown 随 Git 评审。本地用 `python3 scripts/check-docs-health.py` 做只读检查，脚本不改写索引。
- **内容就近，索引导航**：模块内部设计放 `<module>/docs/`；跨模块行为放 `docs/systems/`；`docs/modules/` 只做导航。
- **稳定与行动分开**：`docs/` 是知识层。`dev/` 是行动层（计划、ADR、当前快照）。

## 2. 分类

| 分类 | 位置 | 内容 |
|:-----|:-----|:-----|
| 顶层 | 仓库根 | `README.md`、`AGENTS.md`、`CLAUDE.md`、`DESIGN.md` |
| 产品需求 | `docs/product/` | 为谁、交付什么、怎样算还成立 |
| 架构 | `docs/architecture/` | 全景与横切约束 |
| 系统 | `docs/systems/` | 跨模块行为 |
| 模块导航 | `docs/modules/<module>/INDEX.md` | 指向模块 README 和源码入口 |
| 模块设计 | `<module>/docs/` | 该模块自己的设计，有内容时才建 |
| 指南 | `docs/guides/` | 构建、配置、测试 |
| 行动层 | `dev/` | plan、ADR、roadmap、status |

根 `README.md` 保持上游英文项目介绍。Fork 的说明进 `docs/`。

## 3. 目录

```
AGENTS.md                          # 开发向导（无 frontmatter）
CLAUDE.md                          # 指向 AGENTS.md（无 frontmatter）
DESIGN.md                          # 高层设计
README.md                          # 上游介绍（无 frontmatter）

docs/
├── README.md                      # 文档总览
├── INDEX.md                       # 全局索引，含「模块列表」表
├── DOCS-SPEC.md                   # 本文件
├── DOCUMENTATION.md               # 维护规则
├── glossary.md
├── product/
│   ├── INDEX.md
│   ├── vision.md
│   ├── requirements/              # 每条 PRD 一个文件
│   │   ├── INDEX.md
│   │   └── PRD-NNN.md
│   └── traceability.md
├── architecture/
│   └── overview.md
├── systems/
│   ├── runtime/
│   ├── profiles/
│   ├── access-control/
│   ├── webdav/
│   └── ui/
├── modules/
│   └── <gradle-module>/INDEX.md   # 与 settings.gradle.kts 一一对应
├── guides/getting-started/
└── templates/

dev/
├── README.md
├── progress/status.md             # ≤200 行当前快照
├── decisions/                     # ADR：NNN-short-name.md
├── plans/
├── roadmap/
├── iterations/
├── parallel/
├── demand/
└── archive/                       # 只移入，不删除
```

Gradle 模块现为：`app`、`core`、`service`、`design`、`common`、`hideapi`。`docs/modules/` 的目录名必须与 `include(":…")` 一致，不在这里合并多个模块。

`docs/modules/` 不写大段设计。模块内部设计在 `<module>/docs/`（`app`、`core`、`service`、`design`、`common`、`hideapi` 已有）。`docs/systems/` 写跨模块行为。`dev/` 不写可长期引用的规格正文。

## 4. 模板与检查

新计划、ADR、进度和看板从 [templates/](templates/) 复制。frontmatter 字段定义见 [DOCUMENTATION.md](DOCUMENTATION.md)。

健康检查：

```bash
python3 scripts/check-docs-health.py
python3 scripts/check-docs-health.py --format json
```

默认缺 frontmatter 和断链是 warning。`--strict-frontmatter` 与 `--strict-links` 把它们升为 error。必须存在的入口缺失、`status.md` 超过 200 行或没有快照正文、Gradle 模块没有 `docs/modules/<name>/INDEX.md`、模块不在 `docs/INDEX.md` 的「模块列表」表里，都是 error。

`scripts/generate-docs-index.sh` 已禁用，不会覆盖人工索引。`status.md` 后注溢出用 `bash scripts/archive-status-overflow.sh`。

扫描范围是 `docs/`、`dev/`、各 Gradle 模块的 `README.md` 与 `<module>/docs/`，以及根上的 `AGENTS.md`、`CLAUDE.md`、`DESIGN.md`、`README.md`。`dev/archive/` 与 `dev/iterations/` 不参与断链检查。mihomo 子模块里的 Markdown 不在本系统范围内。

## 5. 相对上游文档系统的取舍

UniverseAgent 里的 Singularity / TUI 产品树、`dev/loop` 自动化套件，以及按引擎模块写死的链接前缀，没有搬进来。健康检查改为读取本仓库的 `settings.gradle.kts`。产品层只有这一份 Android 应用，不再拆客户端产品目录。
