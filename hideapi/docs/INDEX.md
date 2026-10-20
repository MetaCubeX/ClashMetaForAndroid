---
title: "hideapi 编译期存根"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: "compileOnly 的 ActivityThread.currentProcessName 声明，方法体只抛 Stub"
---

# hideapi

这个模块让编译器能解析一个隐藏 API 调用。它不提供可用的运行时实现。

`hideapi/build.gradle.kts` 只应用 `com.android.library`，没有 `dependencies`。Manifest 是空的 `<manifest />`。`consumer-rules.pro` 是空文件。`proguard-rules.pro` 仍是模板，里面没有 `ActivityThread`。

根 `build.gradle.kts` 的 `subprojects` 对所有模块套用同一套 SDK 设置，并给非 `app` 模块加上库插件。按其中的 namespace 规则，本模块是 `com.github.kr328.clash.hideapi`。同一段配置在模块名是 `hideapi` 时关掉 dataBinding。Java 源文件的包名是 `android.app`，和这个 namespace 不是一回事。版本数字见 [架构概览](../../docs/architecture/overview.md)。

## 为什么单独成模块

`common` 在 API 28 以下要调用 `android.app.ActivityThread.currentProcessName()`。仓库把这个声明放在同包名的类里，而不是写进 `common`。方法体是：

```java
throw new IllegalArgumentException("Stub!");
```

所以这个类不能当作进程名的实现。

依赖写的是 `compileOnly`，不是 `implementation`：

| 模块 | `hideapi` |
|:-----|:----------|
| `common` | `compileOnly(project(":hideapi"))` |
| `app` | `compileOnly(project(":hideapi"))` |
| `core`、`service`、`design` | 构建脚本里没有这条依赖 |

`compileOnly` 不会把本模块当作 implementation 依赖传下去。

全仓库直接写 `android.app.ActivityThread` 的只有本文件和 `common` 的 `compat/App.kt`。`app` 虽然也 `compileOnly` 了本模块，它的源码没有引用这个类；`MainApplication` 用的是 `common` 的扩展属性 `currentProcessName`。

## 声明了什么

`hideapi/src/main/java/android/app/ActivityThread.java` 只有一个公开静态方法 `currentProcessName()`，返回类型 `String`，没有参数，没有其他字段或方法。

## 调用点怎样用它

`Application.currentProcessName` 在 `common/.../compat/App.kt`：

- `SDK_INT >= 28` 时调用 `Application.getProcessName()`，不经过这个类。
- 否则调用 `ActivityThread.currentProcessName()`。捕获任意 `Throwable`，用 `Log.w` 记下，然后返回 `packageName`。

因此存根若在运行时真的被执行，调用方得到的是 `packageName`，不是 `Stub!` 这个异常继续向外抛。

导航：[模块 README](../README.md)、[模块导航](../../docs/modules/hideapi/INDEX.md)、[common](../../common/docs/INDEX.md)。
