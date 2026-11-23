---
title: "归档"
type: concept
status: accepted
phase: N/A
updated: 2026-10-30
summary: "过时文档由 wiki.py archive 移入这里，保持原相对路径"
---

# 归档

过时的计划整页移到这里，不删除。用 `python3 scripts/wiki/wiki.py archive <原路径> --root .` 预览，确认后再 `--apply`。目的地是 `dev/archive/<原相对路径>`，文件名不变。

`check-docs-health.py` 不把本目录算进断链。`wiki.py check` 仍检查，现行文档上的断链是错误，这里的断链降为警告。

已归档：

- [docs-product-requirements.md](docs-product-requirements.md)：合订需求拆成 `docs/product/requirements/` 之前的正文。
- [dev/plans/](dev/plans/)：2026-10-20 十六路审查里已经完成的 15 份方案。发现稿还在 `dev/plans/`。
