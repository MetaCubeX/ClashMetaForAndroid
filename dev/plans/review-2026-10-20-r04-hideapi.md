---
title: "审查发现 r04 hideapi"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hideapi 仅有编译期存根，未发现缺陷"
---

# 审查发现 r04 hideapi

审查范围是 `hideapi` 的活码：`src/main/java/android/app/ActivityThread.java`、`src/main/AndroidManifest.xml`、`build.gradle.kts`、`consumer-rules.pro`、`proguard-rules.pro`。

## 结论

0 条。

`ActivityThread` 只声明 `currentProcessName()`，方法体固定抛出 `IllegalArgumentException("Stub!")`，与模块自己的说明一致：这是给编译器看的隐藏 API 声明，不是进程名实现。空的 `<manifest />`、空的 `consumer-rules.pro`、未引用该类的 ProGuard 模板，都不构成数据丢失、越权、用户可见行为错误，或会触发的边界错误。
