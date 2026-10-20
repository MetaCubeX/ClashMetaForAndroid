---
title: "golang/native 包布局"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-15
summary: "Android 桥的 Go 包：app、config、tun、tunnel、platform，不是 mihomo 本体"
---

# golang/native 包布局

> 适用模块：`core`
> JNI 与 `Clash`：[bridge.md](bridge.md)
> 运行时角色：[运行时](../../docs/systems/runtime/INDEX.md)

`core/src/main/golang/native` 是 Android 桥。它把 cgo 导出收成对 mihomo 的少量调用：加载配置、TUN fd、查询代理和流量、HTTP 入站、平台回调。协议实现在子模块 `core/src/foss/golang/clash`，这里不按协议罗列。

`go.mod` 的 module 是 `cfa`。`github.com/metacubex/mihomo` 被 replace 到 `../../foss/golang/clash`。同一文件还有一行把 `google.golang.org/protobuf` replace 到 `github.com/metacubex/protobuf-go`。版本 pin、构建 tag 和命令见 [构建指南](../../docs/guides/getting-started/build.md)。`core/build.gradle.kts` 把这个目录的包名设为 `cfa/native`，alpha 与 meta 的 tags 都是 `foss`、`with_gvisor`、`cmfa`。

根目录的 Go 文件是 `package main`。`main` 直接 `panic("Stub!")`，真正入口是 `//export`。

## 目录

| 路径 | 包 | 职责 |
|:-----|:---|:-----|
| `native/*.go` | `main` | cgo 导出，把 C 字符串转给子包 |
| `native/bridge.c`、`bridge.h` | C | 函数指针，见 [bridge.md](bridge.md) |
| `native/trace.c`、`trace.h` | C | `ENABLE_TRACE` 为 0 |
| `native/app` | `app` | 版本、DNS、时区、已装应用、套接字回调、`content:` fd、副标题正则 |
| `native/config` | `config` | 拉取、修补、校验、加载配置；覆盖槽；age 密钥 |
| `native/tun` | `tun` | 用已有 fd 启动 sing-tun |
| `native/tunnel` | `tunnel` | 模式、流量、代理组、健康检查、provider、连接 |
| `native/proxy` | `proxy` | 本机 HTTP 入站 |
| `native/platform` | `platform` | Linux/Android：fd 上限、旧系统的 `/proc/net` UID |
| `native/delegate` | `delegate` | `Init`：home、版本号、包名解析、拨号 hook |
| `native/common` | `common` | 把 provider 路径收成相对根的形式 |
| `native/all` | `all` | 空白导入；`native/` 内没有其他文件引用它 |
| `native/debug.go` | `main` | 仅 `// +build debug`；默认 tags 不含 `debug` |

`package main` 直接导入 `app`、`config`、`delegate`、`proxy`、`tun`、`tunnel`。`platform` 由 `delegate` 和 `app` 导入，`common` 由 `config` 导入。

## package main

| 文件 | 导出 | 转到 |
|:-----|:-----|:-----|
| `main.go` | `coreInit`、`reset`、`forceGc` | `delegate.Init`；`config.LoadDefault`、清统计、关连接；异步 GC |
| `app.go` | `notifyDnsChanged`、`notifyInstalledAppsChanged`、`notifyTimeZoneChanged`、`queryConfiguration` | `app`；最后一项编码空结构体 |
| `config.go` | `fetchAndValid`、`load`、覆盖槽读写、age 密钥 | `config`，异步完成回调 |
| `tun.go` | `startTun`、`stopTun` | `tun.Start`，并把 mark / UID 回调装进 `app` |
| `tunnel.go` | 状态、流量、组、健康检查、selector、provider、`suspend` | `tunnel` |
| `proxy.go` | `startHttp`、`stopHttp` | `proxy` |
| `log.go` | `subscribeLogcat` | mihomo `log.Subscribe` |
| `utils.go` | 无导出 | `marshalJson`、`marshalString`（`error` 取 `Error()`，`nil` 得到 C NULL） |

`log.go` 的 `init` 另起一个永远不退的订阅，把 mihomo 日志送到 `log_info` 等 Android log。`subscribeLogcat` 再开一条订阅：低于当前日志级别的行丢掉，除非正文以 `[APP]` 开头。JSON 字段是 `level`、`message`、`time`。`time` 是 `UnixNano / 1000 / 1000`，即毫秒。

`debug.go` 在 `debug` tag 下监听 `0.0.0.0:8888` 的 pprof。当前 flavor tags 不会编进它。

`startTun` 用权重为 4 的信号量包住回调，避免 `close` 与仍在进行的 mark / UID 查询交叉。`portal` 只出现在调试日志里，`tun.Start` 没有使用它。失败返回 1，JNI 不把这个整数交回 Kotlin。

`queryGroup` 只把 `"Title"`、`"Delay"` 映射成排序模式。`healthCheck` 在 goroutine 里同步等健康检查结束后 `complete`。`updateProvider` 把错误字符串交给 `complete`。

## app

没有包注释。状态是包级变量。

| 文件 | 行为 |
|:-----|:-----|
| `app.go` | 保存 versionName 和 SDK 整数。`NotifyInstallAppsChanged` 解析 `uid:包名`。`NotifyTimeZoneChanged` 把 `time.Local` 设成 `FixedZone` |
| `dns.go` | `NotifyDnsChanged` 把逗号分隔的地址交给 `dns.UpdateSystemDNS`，再 `FlushCacheWithDefaultResolver` |
| `tun.go` | `ApplyTunContext` 安装 mark 和 UID 回调；未安装时 mark 为空函数，UID 返回 -1。`QuerySocketUid` 按源地址网络选 TCP 或 UDP。SDK 低于 29 时改走 `platform.QuerySocketUidFromProcFs`，不调用 Java |
| `content.go` | `OpenContent` 经注入的函数拿 fd，设非阻塞后包成 `*os.File`。默认实现返回 `"not implement"`。`app.go` 的 `init` 注入 `openRemoteContent`，后者调用 C 的 `open_content` |
| `ui.go` | 编译 `ui-subtitle-pattern`。失败则清掉正则并打警告 |

## config

加载一条配置要经过固定修补链。`process` 按这个顺序调用，注释要求 `patchExternalController` 在 `patchOverride` 之前，这样只有覆盖 JSON 里的外部控制器会留下：

| 顺序 | 函数 | 对 raw config 做什么 |
|:-----|:-----|:---------------------|
| 1 | `patchExternalController` | 清空 `ExternalController` 和 `ExternalControllerTLS` |
| 2 | `patchOverride` | 先解码持久覆盖，再解码会话覆盖。失败只打日志 |
| 3 | `patchGeneral` | 清空 `Interface`，`RoutingMark` 置 0。若外部控制器非空，`ExternalUI` 设为 `<profile>/ui` |
| 4 | `patchProfile` | `StoreSelected = false`，`StoreFakeIP = true` |
| 5 | `patchDns` | DNS 未启用时换成默认 DNS 并打开 fake-ip，同时 `AppendSystemDNS = true`。需要时在 nameserver 末尾加 `system://` |
| 6 | `patchTun` | `Enable`、`AutoRoute`、`AutoDetectInterface` 都设为 false |
| 7 | `patchListeners` | 丢掉 `tproxy`、`redir`、`tun` |
| 8 | `patchProviders` | 路径收到 `<profile>/providers/` 下。无 path 但有 url 时用 url 的哈希做文件名；两者都空则当作内联 provider 跳过 |
| 9 | `validConfig` | 必须有 `proxies` 或 `proxy-providers`。`ui-subtitle-pattern` 必须能编译 |

DNS 未启用时写入的 nameserver 是 `223.5.5.5`、`119.29.29.29`、`8.8.4.4`、`1.0.0.1`，fake-ip 范围是 `28.0.0.0/8`，过滤器是 `defaults.go` 里的那组域名。

| 文件 | 行为 |
|:-----|:-----|
| `load.go` | 读 `<profile>/config.yaml`，修补后 `ParseRawConfig`，`hub.ApplyConfig`，再把副标题正则交给 `app`。`LoadDefault` 用空字节解析并应用 |
| `fetch.go` | `http`/`https` 用 mihomo HTTP 客户端，User-Agent 为 `ClashMetaForAndroid/` 加 versionName，超时 60 秒。`content` 走 `app.OpenContent`。本地已有 `config.yaml` 且 `force` 为 false 时不重新下载。接着按 provider 拉缺失文件，规则 provider 若带 `path-in-bundle` 则用 `rules/bundle.Open` 写出。最后解析并 `Close` provider，不 `ApplyConfig`。进度 JSON 的 `action` 为 `FetchConfiguration`、`FetchProviders`、`SubscriptionInfo`、`Verifying` |
| `override.go` | 持久槽是 home 下的 `override.json`（`constant.Path.Resolve`），模式 `0600`。会话槽是内存字符串。缺省都是 `{}` |
| `age.go` | 转到 `mihomo/component/age` 的同名函数 |
| `provider.go` | 遍历 proxy / rule provider；`destroyProviders` 关闭实现了 `io.Closer` 的 provider |

`FetchAndValid` 在验证阶段把进度写成 `0xffff`。订阅头 `subscription-userinfo` 和 `profile-update-interval` 放进 `SubscriptionInfo`。更新间隔按小时解析，再换成毫秒；大于 0 且小于 15 分钟时抬到 15 分钟。

## tunnel

查询和改选择都走 mihomo 当前隧道里的代理表，不自己保存一份。

| 文件 | 行为 |
|:-----|:-----|
| `state.go` | `QueryMode` 返回 `tunnel.Mode().String()` |
| `statistic.go` | `Now`、`Total`、`ResetStatistic` 转到 `statistic.DefaultManager` |
| `suspend.go` | 空函数。注释写明调用方来自亮屏/灭屏，但不能调用核心的 `OnSuspend` / `OnRunning`，否则锁屏后不再处理新连接 |
| `conn.go` | `CloseAllConnections` 关闭统计管理器里的连接。`closeConnByGroup` 关掉链上出现该名字的连接 |
| `connectivity.go` | 对一个代理组的每个 provider 做 `HealthCheck` 并等待。`HealthCheckAll` 为 `QueryProxyGroupNames(false)` 的每个名字各起一个 goroutine |
| `providers.go` | 列出规则和代理 provider，跳过 `Compatible`。`UpdatedAt` 是毫秒。更新只接受类型名 `Rule` 或 `Proxy` |
| `proxies.go` | 组列表与排序，见下 |
| `loopback.go` | 只声明 `127.0.0.1`。包内没有引用 |

`QueryProxyGroupNames`：模式是 direct 时返回空切片。从名为 `GLOBAL` 的组的第一个 provider 取代理，只保留代理组，并跳过 `Hidden()`。`excludeNotSelectable` 为真时再要求类型是 `Selector`。模式是 global 时在列表前加 `"GLOBAL"`。

`QueryProxyGroup` 用该组的 `Proxies()`，不是 `collectProviders`（那次调用在源码里被注释掉）。`Title` 按标题字符串排序，`Delay` 按延迟排序。非组节点若匹配副标题正则，匹配段变成 `subtitle`，其余变成 `title`。延迟取该节点 `ExtraDelayHistories` 里第一个非空测试 URL，没有则用 `https://www.gstatic.com/generate_204`。

`PatchSelector` 要求目标实现 `SelectAble`。`Set` 失败只打日志，函数仍然返回 true，并按组名关连接。找不到或类型不对才返回 false。

## tun

`tun.Start` 把栈名用 `C.StackTypeMapping` 转成 mihomo 栈，未知名字用 `C.TunSystem`。`gateway` 是逗号分隔的前缀（注释示例 `172.19.0.1/30` 或再加一个 IPv6 前缀），按地址族放进 `Inet4Address` / `Inet6Address`。`dns` 是逗号分隔的地址，每个加上端口 53 放进 `DNSHijack`。

监听选项里 `Enable` 为 true，`Device` 为 `sing_tun.InterfaceName`，`FileDescriptor` 为传入的 fd，`MTU` 为 9000。`AutoRoute` 和 `AutoDetectInterface` 为 false；源码注释分别指向 `TunService.kt` 里设置的路由和 `VpnService` 的 protect。然后 `sing_tun.New(options, tunnel.Tunnel)`。

这与 `config.patchTun` 不冲突：配置文件里的 tun 段被关掉，运行中的 TUN 只来自这条 fd。

## proxy

`proxy.Start` 在互斥锁里先停掉旧的 `http.Listener`，再 `http.NewWithAuthenticate(listen, tunnel.Tunnel, false)`。成功时返回 `listener.Address()`。`Stop` 关闭并清空监听器。

## platform

两个文件都带 `// +build linux`。

`limit.go` 打开 `/dev/null`，把 `RLIMIT_NOFILE` 的软限制乘 3/4 当作 `maxFdCount`（读不到限制时用 1024）。`ShouldBlockConnection` 对这个 fd 做 `Dup`：dup 失败，或新 fd 大于 `maxFdCount`，则返回 true。dup 出的 fd 会关掉。

`procfs.go` 读 `/proc/net/tcp` 的表头，记下 `local_address` 和 `uid` 的列号（表头若同时有 `tx_queue` 与 `rx_queue`，或同时有 `tr` 与 `tm->when`，后面的列号减一）。查询时按源地址在 `/proc/net/<tcp|udp>6` 和 IPv4 文件里找 UID，找不到返回 -1。

## delegate

`Init` 是进程内只从 `coreInit` 进来的装配点：

- `constant.SetHomeDir(home)`。
- `gitVersion` 被 `_` 分成三段时，`constant.Version` 设为 `<分支小写>-<7位哈希>-CMFA-<versionName 小写>`，`BuildTime` 为第三段。否则 `Version` 就是原字符串。最后再整体转成小写。
- 把 versionName 和 SDK 整数交给 `app`。
- `process.DefaultPackageNameResolver` 用连接的源、目标地址查 UID，再查包名。地址缺失时返回 `process.ErrInvalidNetwork`。
- `dialer.DefaultSocketHook` 先问 `platform.ShouldBlockConnection`，为真则返回 `"blocked"`。否则在 `Control` 里 `app.MarkSocket`。

## common 与 all

`common.ResolveAsRoot` 按 `/` 拆开路径，丢掉空段和 `.`，`..` 弹出上一段，再拼回去。`patchProviders` 用它处理配置里已有的 path。

`all/imports.go` 空白导入 `app`、`common`、`config`、`delegate`、`platform`、`proxy`、`tun`、`tunnel`，以及 `golang.org/x/sync/semaphore` 和 `mihomo/log`。`native/` 下没有文件导入 `cfa/native/all`。上面各包的 `init` 是因为 `package main` 的导入链才会运行。
