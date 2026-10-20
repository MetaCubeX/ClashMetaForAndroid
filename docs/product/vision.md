---
title: "产品愿景"
type: concept
status: accepted
phase: N/A
updated: 2026-10-15
summary: "在 Android 上提供 mihomo 的图形界面，并保留本 fork 的分流与同步"
---

# 愿景

ClashMetaX 让使用者在手机上运行 mihomo，导入配置，选择节点，并按应用决定谁走隧道。

本 fork 在此之上保留三件使用者能区分的事：多份命名的应用分流策略、访问控制列表上的系统/国内应用集合，以及把配置和策略同步到 WebDAV。

## 边界

- 内核行为以 mihomo 子模块为准。本仓库描述的是 Android 壳、进程和上述 fork 行为。
- 文档不记录某台手机的序列号、签名指纹或密钥口令。
- 默认 WebDAV 地址与用户名以 `service` 源码中的常量为准，产品文档不另维护一份。

需求条目见 [requirements/](requirements/INDEX.md)。
