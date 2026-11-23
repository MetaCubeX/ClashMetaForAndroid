---
title: "审查发现 r04 hideapi"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "这次范围内没有可报告的缺陷"
---

# 审查发现 r04 hideapi

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖 hideapi/。

这次范围内没有可报告的缺陷。

已读源码是 `hideapi/src/main/java/android/app/ActivityThread.java`：`currentProcessName()` 无参、返回 `String`，方法体抛出 `IllegalArgumentException("Stub!")`。同目录的构建脚本、空 manifest、空 consumer rules 与模板 ProGuard 没有可观察的行为。这是编译期声明，不是进程名的运行时实现。
