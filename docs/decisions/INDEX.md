---
title: "架构决策"
type: index
status: accepted
phase: N/A
updated: 2026-11-04
summary: "仍约束行为的决定。新决定用主题文件名，不申请全局序号"
---

# 架构决策

状态：当前有效。

跨模块决定放在本目录。已采用的决定保留原文；要推翻时新建一篇，并在同一改动里更新仍写着旧结论的现行文档。

| 文件 | 编号 | 标题 | 状态 |
|:-----|:-----|:-----|:-----|
| [git-markdown-docs.md](git-markdown-docs.md) | 001 | 采用 Git + Markdown 文档系统 | 当前有效 |
| [projectwiki-shared-workflow.md](projectwiki-shared-workflow.md) |  | 文档流程改以 ProjectWiki 共享规则为准 | 当前有效 |
| [compose-ios-home.md](compose-ios-home.md) | 002 | 首页和设置改用 Compose | 当前有效 |
| [access-control-fallback.md](access-control-fallback.md) | 003 | 全局访问控制保留为默认回退 | 当前有效 |
| [commit-projection-session.md](commit-projection-session.md) | 004 | 用户数据提交、名单投影、编辑会话和失败隔离 | 当前有效 |
| [cross-client-webdav-sync.md](cross-client-webdav-sync.md) | 005 | 两端协同适配现有 WebDAV 配置协议 | 草案 |

新决定复制 [决策模板](../templates/decision-template.md)，文件名写成主题，例如 `commit-projection-session.md`。编号只留在旧文标题里，用来对照历史讨论。
