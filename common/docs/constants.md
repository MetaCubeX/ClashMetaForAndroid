---
title: "common 常量"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-25
summary: "Intents、Components、Permissions、Authorities、Metadata 实际拼出的字符串"
---

# common 常量

包是 `com.github.kr328.clash.common.constants`。除下面标明的 `const` 和类名外，字符串都是运行时 `packageName` 加上固定后缀。

`packageName` 定义在 `util/Global.kt`，第一次读取时等于 `Global.application.packageName`。这些 `object` 的 `val` 在该 `object` 第一次初始化时求值，所以要发生在 `Global.init` 之后。它读的是进程里的应用包名，不是本模块的 namespace，也不是写死的 flavor。包名和 namespace 的差别见 [术语表](../../docs/glossary.md)。

动作字符串怎样和导出组件的 Manifest 对齐，见 [运行时](../../docs/systems/runtime/INDEX.md)。下文只列出常量本身。

## Intents

源码把前四个标为 Public，其余标为 Self。Public 的后缀是 `.action.`，Self 的后缀是 `.intent.action.`。Kotlin 名字和后缀并不总是同一段文字：`ACTION_PROFILE_REQUEST_UPDATE` 的后缀是 `REQUEST_UPDATE`，不是 `PROFILE_REQUEST_UPDATE`。

| 常量 | 值 |
|:-----|:---|
| `ACTION_PROVIDE_URL` | `$packageName.action.PROVIDE_URL` |
| `ACTION_START_CLASH` | `$packageName.action.START_CLASH` |
| `ACTION_STOP_CLASH` | `$packageName.action.STOP_CLASH` |
| `ACTION_TOGGLE_CLASH` | `$packageName.action.TOGGLE_CLASH` |
| `ACTION_SERVICE_RECREATED` | `$packageName.intent.action.CLASH_RECREATED` |
| `ACTION_CLASH_STARTED` | `$packageName.intent.action.CLASH_STARTED` |
| `ACTION_CLASH_STOPPED` | `$packageName.intent.action.CLASH_STOPPED` |
| `ACTION_CLASH_REQUEST_STOP` | `$packageName.intent.action.CLASH_REQUEST_STOP` |
| `ACTION_CLASH_REQUEST_RELOAD` | `$packageName.intent.action.CLASH_REQUEST_RELOAD` |
| `ACTION_PROFILE_CHANGED` | `$packageName.intent.action.PROFILE_CHANGED` |
| `ACTION_PROFILE_UPDATE_COMPLETED` | `$packageName.intent.action.PROFILE_UPDATE_COMPLETED` |
| `ACTION_PROFILE_UPDATE_FAILED` | `$packageName.intent.action.PROFILE_UPDATE_FAILED` |
| `ACTION_PROFILE_REQUEST_UPDATE` | `$packageName.intent.action.REQUEST_UPDATE` |
| `ACTION_PROFILE_SCHEDULE_UPDATES` | `$packageName.intent.action.SCHEDULE_UPDATES` |
| `ACTION_PROFILE_LOADED` | `$packageName.intent.action.PROFILE_LOADED` |
| `ACTION_OVERRIDE_CHANGED` | `$packageName.intent.action.OVERRIDE_CHANGED` |
| `ACTION_APP_LANGUAGE_CHANGED` | `$packageName.intent.action.APP_LANGUAGE_CHANGED` |

Extra 键是 `const`，不加包名前缀：

| 常量 | 值 |
|:-----|:---|
| `EXTRA_NAME` | `name` |
| `EXTRA_STOP_REASON` | `stop_reason` |
| `EXTRA_APP_LANGUAGE` | `app_language` |
| `EXTRA_UUID` | `uuid` |
| `EXTRA_FAIL_REASON` | `fail_reason` |

## Components

类名包是源码里的私有常量 `com.github.kr328.clash`。`ComponentName` 的第一个参数才是运行时 `packageName`。

| 常量 | 类名 |
|:-----|:-----|
| `MAIN_ACTIVITY` | `com.github.kr328.clash.MainActivity` |
| `PROPERTIES_ACTIVITY` | `com.github.kr328.clash.PropertiesActivity` |

## Permissions

`RECEIVE_SELF_BROADCASTS` 是 `$packageName.permission.RECEIVE_BROADCASTS`。

同一字符串由本模块 Manifest 声明：`${applicationId}.permission.RECEIVE_BROADCASTS`，`protectionLevel` 为 `privileged|signature`，同时有一条 `uses-permission`。label 是 `@string/receive_clash_broadcasts`，description 是 `@string/receive_broadcasts_of_clash`。这两条字符串在 `common/src/main/res/` 的 `values`、`values-zh`、`values-zh-rTW`、`values-ru`。

## Authorities

| 常量 | 值 |
|:-----|:---|
| `STATUS_PROVIDER` | `$packageName.status` |
| `SETTINGS_PROVIDER` | `$packageName.settings` |
| `FILES_PROVIDER` | `$packageName.files` |

`service/src/main/AndroidManifest.xml` 里 `StatusProvider`、`PreferenceProvider`、`FilesProvider` 的 `android:authorities` 分别是 `${applicationId}.status`、`${applicationId}.settings`、`${applicationId}.files`。

## Metadata

`GEOIP_FILE_NAME` 是 `$packageName.GEOIP_FILE_NAME`。在本仓库的 Kotlin、Java 和 XML 里，只有这一处定义，没有别的引用。

返回：[common 设计索引](INDEX.md)。
