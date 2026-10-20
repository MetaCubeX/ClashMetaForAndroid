---
title: "Store 与 StoreProvider"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-15
summary: "Store 用属性委托按给定的键读写 StoreProvider，本模块只有 SharedPreferences 这一份实现"
---

# Store 与 StoreProvider

`common` 的 `store` 包有三个类型：`StoreProvider`、`Store`、`SharedPreferenceProvider`。这里没有缓存，没有变更监听，也没有跨进程逻辑。每次读和写都进到当时的 `provider`。

## StoreProvider

接口按键提供五种值，读都带默认值：

| 方法 | 值 |
|:-----|:---|
| `getInt` / `setInt` | `Int` |
| `getLong` / `setLong` | `Long` |
| `getString` / `setString` | `String` |
| `getStringSet` / `setStringSet` | `Set<String>` |
| `getBoolean` / `setBoolean` | `Boolean` |

本模块里唯一的实现是 `SharedPreferenceProvider`。`SharedPreferences.asStoreProvider()` 返回它。写入使用 `androidx.core.content.edit` 的 `preferences.edit { }`，调用处没有传 `commit = true`。`getString` 和 `getStringSet` 对平台返回值做了 `!!`，平台给出 `null` 时会在这里抛空指针。

## Store

`Store` 只保存构造时传入的 `StoreProvider`。每种工厂方法返回 `Store.Delegate<T>`，实现 `getValue` / `setValue`，所以调用方写成 `var field by store.xxx(...)`。

键是工厂参数里的字符串，不是 Kotlin 属性名。委托不读 `KProperty.name`。

| 工厂 | 存储 | 读到不能用的值时 |
|:-----|:-----|:-----------------|
| `int` / `long` / `boolean` | 同类型 | 键不存在时用传入的默认值 |
| `string` / `stringSet` | 同类型 | 同上；经过上面的 `!!` |
| `enum` | `value.name` 字符串 | `getString` 的默认值是 `defaultValue.name`。`values` 里没有同名项时返回 `defaultValue` |
| `typedString` | 字符串，类型是 `T?` | 读：`from(provider.getString(key, to(null)))`。写：`setString(key, to(value))`。缺键时没有单独的 `T` 默认值，而是先 `to(null)` 再 `from` |

`enum` 的 `values` 由调用方传入，通常是某个枚举的 `values()`。

## 谁在用

本模块不定义具体偏好项。下面这些类自己 `new Store(...)`，字段和默认值留在各类里：

| 类 | `StoreProvider` 从哪来 |
|:---|:------------------------|
| `app` 的 `AppStore` | `getSharedPreferences("app", MODE_PRIVATE)` |
| `app` 的 `TipsStore` | `getSharedPreferences("tips", MODE_PRIVATE)` |
| `design` 的 `UiStore` | `getSharedPreferences("ui", MODE_PRIVATE)` |
| `service` 的 `ServiceStore` | `PreferenceProvider.createSharedPreferencesFromContext(context)`，再 `asStoreProvider()` |

`ServiceStore` 里还有不经过 `Store` 的字段，直接写 `settings` 目录下的文件。那部分不属于本模块。

返回：[common 设计索引](INDEX.md)。
