---
title: "配置"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: "配置的导入、当前选择，以及 service 进程中的存储。生命周期文档写从导入到提交的过程"
---

# 配置

使用者在 `ProfilesActivity` 管理配置。导入可以来自文件，或来自 `clash://install-config` / `clashmeta://install-config`，由 `ExternalControlActivity` 接收。

持久化与更新调度在 `service`：`ProfileManager`、`ProfileProcessor`、`ProfileWorker`，Room 数据库在 `service/.../data/`。当前选中的配置标识存在 `ServiceStore`。

本地覆盖由覆盖设置界面编辑，经 `core` 的配置覆盖模型进内核。地理数据文件的下载不在配置库里，见 [构建指南](../../guides/getting-started/build.md)。

WebDAV 对配置的拉取和推送见 [WebDAV](../webdav/INDEX.md)。拉取会应用到本机。推送不会把远端配置写回本机已导入的内容。

从导入到提交、定时更新，以及当前配置怎样进内核，见 [配置生命周期](lifecycle.md)。

模块入口：[app](../../modules/app/INDEX.md)、[service](../../modules/service/INDEX.md)。
