---
title: "产品需求入口"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: "ClashMetaX 对使用者的可观察承诺。每条需求在 requirements/ 下单独成文"
---

# 产品需求

本目录记录使用者能观察到的承诺。系统怎样组成见 [DESIGN.md](../../DESIGN.md) 与 [架构概览](../architecture/overview.md)。怎样实施见 [dev/plans](../../dev/plans/)。做到哪一步见 [status](../../dev/progress/status.md)。

## 权威范围

1. 进程与模块边界以 [DESIGN.md](../../DESIGN.md) 和代码为准。
2. 已写入本目录的承诺，以本目录为准。现行条目保持 `accepted`，不因为模块存在就升成 `implemented`。
3. 根 `README.md` 是上游介绍，不是验收标准。
4. `dev/demand/` 只放调研。

## 阅读顺序

1. [vision.md](vision.md)
2. [requirements/](requirements/INDEX.md)
3. [traceability.md](traceability.md)

| 文件 | 回答 | 不写 |
|:-----|:-----|:-----|
| [vision.md](vision.md) | 为谁、原则与边界 | 需求编号、类名 |
| [requirements/](requirements/INDEX.md) | 每条承诺一个 `PRD-NNN.md` | 验证命令、文件切片、把多条需求写进同一文件 |
| [traceability.md](traceability.md) | 承诺体现在哪 | 把需求正文再抄一遍 |

## 相关入口

- [文档索引](../INDEX.md)
- [维护规则](../DOCUMENTATION.md)
- [结构规范](../DOCS-SPEC.md)
