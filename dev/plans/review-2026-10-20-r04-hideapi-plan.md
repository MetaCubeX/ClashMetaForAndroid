---
title: "审查方案 r04 hideapi"
type: plan
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 0、drop 0、weaken 0"
---

# 审查方案 r04 hideapi

发现稿没有编号条目。本次只读 `hideapi` 活码，结论是 hold 0、drop 0、weaken 0。无保留项。不改代码。

打开过的文件与读到的内容：

- `hideapi/src/main/java/android/app/ActivityThread.java`：仅 `currentProcessName()`，第 4–6 行方法体抛出 `IllegalArgumentException("Stub!")`。
- `hideapi/src/main/AndroidManifest.xml`：第 2 行是空的 `<manifest />`。
- `hideapi/consumer-rules.pro`：空文件。
- `hideapi/proguard-rules.pro`：注释模板，全文没有 `ActivityThread`。
- `hideapi/build.gradle.kts`：只应用 `com.android.library`。

这些与模块说明一致，是编译期声明，不是要修的缺陷。没有可降级或可落地的修改项。
