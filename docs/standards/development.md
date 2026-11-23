---
title: "开发规范"
type: spec
status: accepted
phase: N/A
updated: 2026-11-04
summary: "修改范围、验证命令与协作约定；流程见 workflow.md，命令清单只在 wiki.json"
---

# 开发规范

范围：AgenticClash 全仓库（不含 `core/src/foss/golang/clash` 子模块内的独立流程，内核改动另见 [AGENTS.md](../../AGENTS.md)）。状态：当前有效。

## 修改与验证

修改前明确目标、适用约束、实现入口和验证方式。需求到验证的顺序、什么时候写计划和决策，见 [scripts/wiki/workflow.md](../../scripts/wiki/workflow.md)。行为变化在同一次改动里更新已经失实的设计或需求；本仓库的目录和 frontmatter 约定见 [项目适配](../DOCUMENTATION.md)。

按改动范围选择验证。命令和运行目录只登记在 [docs/wiki.json](../wiki.json) 的 `verify` 字段；本页不复制完整命令清单。统一入口：

```bash
python3 scripts/wiki/wiki.py verify --root .
```

| 改动范围 | 通过条件 |
|:---------|:---------|
| 任意提交前（文档结构） | `verify` 中的 `docs-health` 与 `wiki-check` 退出码均为 0 |
| `:service` / `:design` / `:common` 单元测试 | `./gradlew :service:testDebugUnitTest` 等对应任务（接入 `verify` 前在计划里手写一次） |
| Release 构建 | `./gradlew :app:assembleMetaRelease`（见 [构建指南](../guides/getting-started/build.md)） |

已知失败要写明复现方式与退出条件。「改动前就是红的」不能作为一直不处理的理由。

## 协作

只暂存本次主题相关文件。不还原、清理或 stash 他人未提交改动。合并时两边都改过的文件做三方合并。

## 文档与工作记录

知识层在 `docs/` 与各 `<module>/docs/`；过程在 `dev/`。整页归档用 `scripts/wiki/wiki.py archive` 预览后执行，目的地是 `dev/archive/`（见 [scripts/wiki/README.md](../../scripts/wiki/README.md)）。

`dev/progress/status.md` 只在跨计划的协调事实变化时更新，≤200 行。用户可见承诺变化同步 [产品需求](../product/INDEX.md)；系统行为变化同步 `DESIGN.md` 或对应系统文档。

## 写作

写清对象、条件、行为与原因。避免空泛评价、未解释简称和未执行的「已通过」结论。固定写法见 [scripts/wiki/formats.md](../../scripts/wiki/formats.md)。
