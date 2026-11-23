---
title: "并行看板：ProjectWiki 文档系统接入"
type: plan
status: in_progress
phase: N/A
updated: 2026-11-04
summary: "流程已改到共享 workflow；钩子和 adopted_at 仍待接入提交"
---

# 并行看板：ProjectWiki 文档系统接入

状态：执行中。负责：agent:grok。

覆盖需求：无（基础设施）。流程取舍已写入 [projectwiki-shared-workflow.md](../../../docs/decisions/projectwiki-shared-workflow.md)，不改写 [001](../../../docs/decisions/git-markdown-docs.md) 的决定正文。

参照：外部 ProjectWiki 仓库的 `docs/guides/adoption.md`（设计源，不链入本库）。

## 目标

把 AgenticClash 文档系统从「frontmatter + check-docs-health」扩展到 ProjectWiki 的 `wiki.py` 检查、归档、remind 与需求追踪，且不一次性推翻现有 `docs/` 布局。

## 分轨任务

### 轨 A — 工具链（本批）

- [x] 复制 `scripts/wiki/`（含 README、reference、formats）与 `tests/test_wiki.py`
- [x] 新增 [docs/wiki.json](../../../docs/wiki.json)（模块、archive 根、verify、remind）
- [x] 过渡表使用期间登记了权威列；规则写回规范后已删除该文件
- [x] 新增 [dev/INDEX.md](../../INDEX.md)
- [x] 新增根目录 `.ignore` 归档搜索排除
- [x] 首次 `wiki check` 通过后生成 [docs/wiki-baseline.json](../../../docs/wiki-baseline.json) 登记存量错误
- [ ] 可选：`git config core.hooksPath scripts/wiki/hooks`（需维护者确认，代理不自动改 git config）

### 轨 B — 入口与 AGENTS

- [x] [docs/standards/development.md](../../../docs/standards/development.md) 验证入口指向 `verify`
- [x] [docs/INDEX.md](../../../docs/INDEX.md) 登记 wiki.json 与开发规范
- [x] 收敛 [AGENTS.md](../../../AGENTS.md)：双轨检查、状态行、归档目的地、remind 和核对人写法直接写在约束里

### 轨 C — 规范正文

- [x] [DOCUMENTATION.md](../../../docs/DOCUMENTATION.md) 增加 wiki 归档/remind/check-new 与双轨检查说明
- [x] [DOCS-SPEC.md](../../../docs/DOCS-SPEC.md) 登记 wiki.json 为结构配置源；说明 `dev/archive` 与将来根 `archive/` 策略
- [x] 产品需求 PRD 已补「状态：已接受。」和 `PRD-NNN.1` 验收条目；设计页写了「对应需求：」。未标成已实现，所以不要求测试引用
- [x] `dev/plans/` 补了标题下状态行。2026-10-20 已完成的 15 份审查方案已归档到 `dev/archive/dev/plans/`。发现稿和 10 月 24 日的审查仍是草案，留在计划目录；`dev/plans` 预算改为 65

### 轨 D — ADR 与归档

- [x] 仍约束行为的决定已迁入 [docs/decisions/](../../../docs/decisions/INDEX.md)，文件名改为主题；`dev/decisions/INDEX.md` 只留指针
- [x] 新归档命令写在 [DOCUMENTATION.md](../../../docs/DOCUMENTATION.md) 规则 16：优先 `wiki.py archive` 预览后再 `--apply`，旧路径仍接受手工移入 `dev/archive/`
- [x] 过渡表已删除。目录职责在 DOCS-SPEC，项目适配在 DOCUMENTATION，硬规则在 AGENTS

### 轨 E — 对齐共享流程（2026-11-04）

- [x] 用 ProjectWiki 当前工具包替换 `scripts/wiki/`（含 `workflow.md` 与五份技能）。旧副本没有本地补丁
- [x] `docs/wiki.json` 登记 `status_frontmatter`，并排除 `scripts/wiki/**`
- [x] `AGENTS.md`、开发入口和 CONTRIBUTING 改为指向共享流程，删掉每次通读维护规则和每次改写快照的要求
- [x] 新增 [projectwiki-shared-workflow.md](../../../docs/decisions/projectwiki-shared-workflow.md)
- [x] 删除 `tests/test_wiki.py`。那是 ProjectWiki 仓库对自己的测试，当前工具说明写明测试不随工具复制；留在本仓库会去找不存在的 `templates/project`
- [ ] `adopted_at` 等这次接入提交存在后再写入。代理不为此单独提交
- [ ] 每个克隆启用钩子仍须维护者执行 `git config core.hooksPath scripts/wiki/hooks`

## 验证

```bash
python3 scripts/check-docs-health.py
python3 scripts/wiki/wiki.py check --root .
python3 scripts/wiki/wiki.py doctor --root .
python3 scripts/wiki/wiki.py verify --root . docs-health wiki-check
```

## 完成标准

- [x] 双轨检查写在 [CONTRIBUTING.md](../../../CONTRIBUTING.md)，并由 `.github/workflows/docs-check.yaml` 在 pull request 与 `main` 上运行
- [x] `wiki check` 无未登记 baseline 的新 error
- [ ] 本看板移入 `dev/parallel/archive/`，并更新 [parallel/INDEX.md](../INDEX.md)。钩子仍须维护者在每个克隆执行一次 `git config core.hooksPath scripts/wiki/hooks`，代理不改 git config
