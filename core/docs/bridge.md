---
title: "core 桥接边界"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-15
summary: "Clash 与 Bridge 暴露的调用，以及 JNI 如何接到 Go native"
---

# core 桥接边界

> 适用模块：`core`
> Go 包职责：[native-layout.md](native-layout.md)
> 运行时角色：[运行时](../../docs/systems/runtime/INDEX.md)

`core` 对上层只暴露两个 Kotlin 单例。`Clash` 把参数收成模型或回调。`Bridge` 是 JNI。C 侧把 Java 调用转成 cgo 导出函数，再把 Go 需要的回调收成函数指针。

## 两个本地库

`Bridge` 的 `init` 调用 `System.loadLibrary("bridge")`。CMake 目标 `bridge` 由 `main.c`、`jni_helper.c`、`bridge_helper.c` 组成，并链接 `log` 和 `clash`（`core/src/main/cpp/CMakeLists.txt`）。`core/build.gradle.kts` 把 Go 产物文件名设为 `libclash.so`，包名 `cfa/native`。`main.c` 包含构建产出的 `libclash.h`，调用的是 Go `//export` 函数，而不是在 C 里实现核心逻辑。

怎么编、子模块 pin 见 [构建指南](../../docs/guides/getting-started/build.md)。

## 调用方向

```
Clash / 直接调用 Bridge
        │  external fun
        ▼
libbridge.so   JNI：Java_com_github_kr328_clash_core_bridge_Bridge_native*
        │  coreInit / reset / startTun / …
        ▼
libclash.so    package main 的 //export
        │
        ▼
cfa/native/*   再调用 mihomo
```

反向回调不走新的 JNI 方法名。`JNI_OnLoad` 缓存 `TunInterface`、`FetchCallback`、`LogcatInterface`、`CompletableDeferred`、`ClashException`、`Content.open` 的 method ID，并把实现赋给 `native/bridge.h` 里的函数指针。Go 通过 cgo 调用 `mark_socket`、`query_socket_uid`、`complete`、`fetch_report`、`fetch_complete`、`logcat_received`、`open_content`、`release_object`。这些符号的 C 实现在 `native/bridge.c`，里面再调用 `JNI_OnLoad` 装上的函数指针。`jni_helper.h` 的 `ATTACH_JNI` 在当前线程没有 JNIEnv 时 `AttachCurrentThread`，离开作用域再按需 detach。

`bridge.h` 里还有 `log_info` / `log_error` / `log_warn` / `log_debug` / `log_verbose`，转到 Android log，标签 `ClashMetaForAndroid`。

字符串用 `String.getBytes()` 和 `String(byte[])` 传递，没有在 C 里指定 charset。`scoped_string` 在作用域结束时 `free`。Go 回调带上的 C 字符串由 `bridge.c` 在调用函数指针之后释放。

`trace.h` 里 `ENABLE_TRACE` 为 0，`TRACE_METHOD` 是空宏。

## 初始化

`Bridge` 加载库之后做三件事：

1. 以只读方式打开 `packageCodePath`，`detachFd()` 的返回值被丢掉。
2. 把 `filesDir/clash` 建出来，作为 home。
3. 调用 `nativeInit(home, versionName, SDK_INT)`。`versionName` 缺失时用 `"unknown"`。

`nativeInit` 另外把 CMake 生成的 `GIT_VERSION` 传给 `coreInit`。`version.h` 来自 `version.h.in`。CMake 用子模块的 `git log -1` 取 7 位提交、`git branch -r --contains` 取远程分支名，再拼上 `%y%m%d`，中间用 `_` 连接并去掉空格。检出约束见构建指南里的 CMake 说明，这里不重复。

`nativeCoreVersion` 直接返回同一条 `GIT_VERSION`，不经过 Go。`Clash` 没有包装它；`app` 的 `MainActivity` 直接调 `Bridge.nativeCoreVersion()`。

`coreInit` 进入 `delegate.Init` 后立刻 `reset()`。`reset` 加载空配置、清流量统计、关掉全部连接，并做一次 GC。细节见 [native-layout.md](native-layout.md)。

## Clash 对上层暴露什么

`Clash` 是普通 Kotlin 调用。跨边界的结构化数据几乎都是 JSON 字符串。流量是例外，见下一节。

| 分组 | `Clash` | 过 JNI 之后 |
|:-----|:--------|:------------|
| 生命周期 | `reset`、`forceGc`、`suspendCore` | `reset`、`forceGc`、`suspend` |
| 状态 | `queryTunnelState`、`queryTrafficNow`、`queryTrafficTotal` | JSON `mode`；流量是两个 `uint64` |
| 通知 | `notifyDnsChanged`、`notifyTimeZoneChanged`、`notifyInstalledAppsChanged` | 字符串或 `(name, offset)` |
| 隧道与 HTTP | `startTun`、`stopTun`、`startHttp`、`stopHttp` | fd、栈名、地址串；HTTP 返回实际监听地址或 null |
| 代理组 | `queryGroupNames`、`queryGroup`、`healthCheck`、`healthCheckAll`、`patchSelector` | 组名 JSON；排序用 `ProxySort.name` |
| 配置 | `fetchAndValid`、`load`、`queryProviders`、`updateProvider` | 路径、URL、provider 类型名 |
| 覆盖 | `queryOverride`、`patchOverride`、`clearOverride` | slot 用枚举 `ordinal` |
| 界面配置 | `queryConfiguration` | 见下面的空对象 |
| 日志 | `subscribeLogcat` | 返回 `Channel<LogMessage>(32)` |
| age | `setAgeSecretKey`、`genX25519KeyPair`、`genHybridKeyPair`、`veritySecretKeys`、`toPublicKeys`、`verityPublicKeys` | 密钥字符串或 JSON |

通知的编码：

- DNS：去重后用逗号拼接。
- 已安装应用：`uid:包名`，逗号拼接。
- 覆盖槽：`OverrideSlot.Persist`、`Session` 的序数与 Go `OverrideSlotPersist`、`OverrideSlotSession` 的 iota 对齐，都是 0 和 1。
- Provider 更新：`Provider.Type.toString()`，Go 只认 `"Proxy"` 和 `"Rule"`。
- 组排序：`ProxySort` 的 `Title`、`Delay`；其他字符串在 Go 里落到 `Default`。

`startTun` 把 `markSocket` 和 `querySocketUid` 收成 `TunInterface`。UID 查询的地址是字符串，`Clash` 用 `parseInetSocketAddress` 转成 `InetSocketAddress`（按 `https://<地址>` 解析 host 和 port）。

异步完成：

- `load`、`healthCheck`、`updateProvider` 新建 `CompletableDeferred<Unit>` 交给 native。成功走 `complete(Unit)`，失败走 `completeExceptionally(ClashException)`。
- `fetchAndValid` 用 `FetchCallback`：`report` 是 `FetchStatus` JSON，`complete` 的 error 为 null 表示成功。
- `subscribeLogcat` 把 `received` 的 JSON 解成 `LogMessage` 再 `trySend`。

`queryGroup` 得到 null 时，`Clash` 返回 `ProxyGroup("Unknown", emptyList(), "")`。`queryOverride` 解码失败时返回空的 `ConfigurationOverride()`。

`queryConfiguration` 在 Go 里编码一个空结构体，也就是 `{}`。`UiConfiguration` 没有任何属性，解码结果是空对象，不是当前已加载的配置。

`veritySecretKeys`、`verityPublicKeys`、`toPublicKeys` 的 Kotlin 签名是 `vararg`，但只把第一个字符串传过 JNI；没有参数时传空串。这三处以及 Go、JNI 的名字都是 `verity`，不是 `verify`。`setAgeSecretKey(null)` 在 JNI 里把 NULL 传给 Go，Go 调用不带参数的 `SetGlobalSecretKeys`。

## 流量字

`nativeQueryTrafficNow` 和 `nativeQueryTrafficTotal` 返回一个 `jlong`。高 32 位是上传，低 32 位是下载。每一侧先经过 `bridge_helper.c` 的 `down_scale_traffic`：数值放在低 30 位，单位放在 bit 30–31（0 字节、1 KiB、2 MiB、3 GiB），超过字节时存的是百分之一单位。Kotlin 的 `Traffic` 是 `Long` 的 typealias。`util/Traffic.kt` 按这个布局还原后再格式化。

## 模型

`model/` 里和这条桥直接对应的 JSON 类型：

| 类型 | 方向 |
|:-----|:-----|
| `TunnelState` | `mode`：`direct` / `global` / `rule` / `script` |
| `Proxy`、`ProxyGroup` | 查询组 |
| `ProxySort` | 只在 Kotlin 侧，过桥时变成枚举名 |
| `Provider` | `name`、`type`、`vehicleType`、`updatedAt`（毫秒） |
| `FetchStatus` | `FetchConfiguration`、`FetchProviders`、`SubscriptionInfo`、`Verifying` |
| `LogMessage` | `level`、`message`、`time`；`time` 经 `DateSerializer` 按 `java.util.Date` 的毫秒读 |
| `ConfigurationOverride` | 覆盖 JSON，字段名对齐 mihomo raw config |
| `AgeKeyPair` | `secretKey`、`publicKey` |
| `UiConfiguration` | 空类型，见上 |

`ProviderList` 和 `ProxyGroup.SliceProxyList` 只是 Parcelable 切片，不参与 JNI。`Clash` 自己不做 Parcel。

`core` 的 manifest 只声明 `INTERNET`。

## 边界上容易看错的地方

- `startTun` 的 Go 返回值是 `C.int`（失败为 1）。JNI 声明是 `void`，不把这个值返回给 Kotlin。失败时 Go 会关掉刚建的回调。
- `load`、`fetchAndValid`、`updateProvider` 完成后调用 `release_object` 删掉 global ref。`healthCheck` 只 `complete`，源码里没有 `release_object`。`subscribeLogcat` 仅在 `logcat_received` 返回非 0（JNI 抛异常）时释放并退出订阅循环。
- `suspendCore` 会进到 Go，但 `tunnel.Suspend` 的函数体是空的，注释写明不要调用 mihomo 的 `OnSuspend` / `OnRunning`。
- `Content.open` 只接受 `content:` URI，把只读 fd 交给 Go。其他 scheme 在 Kotlin 抛 `UnsupportedOperationException`。
- `Bridge`、`TunInterface`、`FetchCallback`、`LogcatInterface`、`ClashException`、`Content` 都标了 `@Keep`。
