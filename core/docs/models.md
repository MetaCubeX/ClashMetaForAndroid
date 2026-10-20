---
title: "Kotlin 模型"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-15
summary: "model 包的公开类型：字段、枚举，以及 Clash 与 Go 各在桥的哪一侧使用"
---

# Kotlin 模型

> 适用模块：`core`
> JNI 分界：[bridge.md](bridge.md)
> Go 包：[native-layout.md](native-layout.md)
> 谁加载这条桥：[运行时](../../docs/systems/runtime/INDEX.md)

公开类型都在 `com.github.kr328.clash.core.model`。目录里是 12 个文件。嵌套类型写在所属文件里，没有额外文件。

过桥的调用在 `Clash`。JNI 方法名、回调和流量打包不在这里重复。Go 侧没有一份同名模型包，JSON 由 `native` 里的函数现编。

带 `@Serializable` 的是 `AgeKeyPair`、`ConfigurationOverride` 及其嵌套类型、`FetchStatus`、`LogMessage`（含 `Level`）、`Provider`、`Proxy`、`ProxyGroup`、`TunnelState`（含 `Mode`）、`UiConfiguration`。`FetchStatus.Action`、`Provider.Type`、`Provider.VehicleType` 没有这个注解。`ProxySort`、`Traffic`、`ProviderList`、`SliceProxyList` 不编 JSON。

`AgeKeyPair`、`ProxySort`、`Traffic` 不能 Parcel。其余顶层类型可以。`Clash` 自己不做 Parcel。

## 桥的两侧

| 类型 | Kotlin 侧 | Go 侧 |
|:-----|:----------|:------|
| `AgeKeyPair` | `genX25519KeyPair`、`genHybridKeyPair` 解码 | `package main` 的 `ageKeyPair` |
| `ConfigurationOverride` | `queryOverride`、`patchOverride` | `config.ReadOverride`、`WriteOverride` 存整段 JSON |
| `FetchStatus` | `fetchAndValid` 的 `report` | `config.Status` |
| `LogMessage` | `subscribeLogcat` | `log.go` 的 `message` |
| `Provider` | `queryProviders`、`updateProvider` | `tunnel.Provider` |
| `ProviderList` | 不进 JNI | 无 |
| `Proxy` | `queryGroup` 解出的元素 | `tunnel.Proxy` |
| `ProxyGroup` | `queryGroup` | `tunnel.ProxyGroup` |
| `ProxySort` | `queryGroup` 只传 `name` | 不接收这个枚举 |
| `Traffic` | `queryTrafficNow`、`queryTrafficTotal` | `tunnel.Now`、`tunnel.Total` |
| `TunnelState` | `queryTunnelState` | `queryTunnelState` 只写 `mode` |
| `UiConfiguration` | `queryConfiguration` | `queryConfiguration` 编码空对象 |

上表的 Kotlin 侧都是 `Clash` 的函数。过 JNI 的方式见 [bridge.md](bridge.md)。

## AgeKeyPair

一对 age 密钥。字段只有 `secretKey` 和 `publicKey`，都是 `String`。

只有生成密钥对的两条返回这个类型。校验和导出公钥走的是字符串，见 [bridge.md](bridge.md)。

## ConfigurationOverride

一份覆盖 JSON。属性名和 `@SerialName` 都抄自这个类。Go 不声明同名结构体，整段字符串放进覆盖槽。贴进 raw config 的顺序见 [native-layout.md](native-layout.md)。槽位序数、解码失败时退回空对象，见 [bridge.md](bridge.md)。

`Clash` 编解码它时用单独的 `Json`：`ignoreUnknownKeys = true`，`encodeDefaults = false`。

可空属性默认 `null`。`externalControllerCors`、`dns`、`app`、`sniffer`、`geoxurl` 不是可空。后四个是 `val`，构造时就是空对象。

| 属性 | JSON | 类型 |
|:-----|:-----|:-----|
| `httpPort` | `port` | `Int?` |
| `socksPort` | `socks-port` | `Int?` |
| `redirectPort` | `redir-port` | `Int?` |
| `tproxyPort` | `tproxy-port` | `Int?` |
| `mixedPort` | `mixed-port` | `Int?` |
| `authentication` | `authentication` | `List<String>?` |
| `allowLan` | `allow-lan` | `Boolean?` |
| `bindAddress` | `bind-address` | `String?` |
| `mode` | `mode` | `TunnelState.Mode?` |
| `logLevel` | `log-level` | `LogMessage.Level?` |
| `ipv6` | `ipv6` | `Boolean?` |
| `externalController` | `external-controller` | `String?` |
| `externalControllerTLS` | `external-controller-tls` | `String?` |
| `externalControllerCors` | `external-controller-cors` | `ExternalControllerCors` |
| `secret` | `secret` | `String?` |
| `hosts` | `hosts` | `Map<String, String>?` |
| `unifiedDelay` | `unified-delay` | `Boolean?` |
| `geodataMode` | `geodata-mode` | `Boolean?` |
| `tcpConcurrent` | `tcp-concurrent` | `Boolean?` |
| `findProcessMode` | `find-process-mode` | `FindProcessMode?` |
| `dns` | `dns` | `Dns` |
| `app` | `clash-for-android` | `App` |
| `sniffer` | `sniffer` | `Sniffer` |
| `geoxurl` | `geox-url` | `GeoXUrl` |

### Dns

`fallbackFilter` 是 `val`，默认 `DnsFallbackFilter()`。其余属性是 `var`，默认可空。

| 属性 | JSON | 类型 |
|:-----|:-----|:-----|
| `enable` | `enable` | `Boolean?` |
| `preferH3` | `prefer-h3` | `Boolean?` |
| `listen` | `listen` | `String?` |
| `ipv6` | `ipv6` | `Boolean?` |
| `useHosts` | `use-hosts` | `Boolean?` |
| `enhancedMode` | `enhanced-mode` | `DnsEnhancedMode?` |
| `nameServer` | `nameserver` | `List<String>?` |
| `fallback` | `fallback` | `List<String>?` |
| `defaultServer` | `default-nameserver` | `List<String>?` |
| `fakeIpFilter` | `fake-ip-filter` | `List<String>?` |
| `fakeIPFilterMode` | `fake-ip-filter-mode` | `FilterMode?` |
| `fallbackFilter` | `fallback-filter` | `DnsFallbackFilter` |
| `nameserverPolicy` | `nameserver-policy` | `Map<String, String>?` |

### DnsFallbackFilter

| 属性 | JSON | 类型 |
|:-----|:-----|:-----|
| `geoIp` | `geoip` | `Boolean?` |
| `geoIpCode` | `geoip-code` | `String?` |
| `ipcidr` | `ipcidr` | `List<String>?` |
| `domain` | `domain` | `List<String>?` |

### App

只有 `appendSystemDns`（JSON `append-system-dns`，`Boolean?`）。

### FindProcessMode

| 常量 | JSON |
|:-----|:-----|
| `Off` | `off` |
| `Strict` | `strict` |
| `Always` | `always` |

### DnsEnhancedMode

`None` 的 JSON 名是 `normal`，不是 `none`。`Mapping` 是 `redir-host`。

| 常量 | JSON |
|:-----|:-----|
| `None` | `normal` |
| `Mapping` | `redir-host` |
| `FakeIp` | `fake-ip` |

### FilterMode

| 常量 | JSON |
|:-----|:-----|
| `BlackList` | `blacklist` |
| `WhiteList` | `whitelist` |
| `Rule` | `rule` |

### Sniffer

`sniff` 是 `val`，默认 `Sniff()`。

| 属性 | JSON | 类型 |
|:-----|:-----|:-----|
| `enable` | `enable` | `Boolean?` |
| `sniff` | `sniff` | `Sniff` |
| `forceDnsMapping` | `force-dns-mapping` | `Boolean?` |
| `parsePureIp` | `parse-pure-ip` | `Boolean?` |
| `overrideDestination` | `override-destination` | `Boolean?` |
| `forceDomain` | `force-domain` | `List<String>?` |
| `skipDomain` | `skip-domain` | `List<String>?` |
| `skipSrcAddress` | `skip-src-address` | `List<String>?` |
| `skipDstAddress` | `skip-dst-address` | `List<String>?` |

### Sniff

三个属性默认都是 `ProtocolConig()`。类名是 `ProtocolConig`，源码如此。

| 属性 | JSON | 类型 |
|:-----|:-----|:-----|
| `http` | `HTTP` | `ProtocolConig` |
| `tls` | `TLS` | `ProtocolConig` |
| `quic` | `QUIC` | `ProtocolConig` |

### ProtocolConig

| 属性 | JSON | 类型 |
|:-----|:-----|:-----|
| `ports` | `ports` | `List<String>?` |
| `overrideDestination` | `override-destination` | `Boolean?` |

### GeoXUrl

| 属性 | JSON | 类型 |
|:-----|:-----|:-----|
| `geoip` | `geoip` | `String?` |
| `mmdb` | `mmdb` | `String?` |
| `geosite` | `geosite` | `String?` |

### ExternalControllerCors

| 属性 | JSON | 类型 |
|:-----|:-----|:-----|
| `allowOrigins` | `allow-origins` | `List<String>?` |
| `allowPrivateNetwork` | `allow-private-network` | `Boolean?` |

## FetchStatus

拉取配置时的一条进度。Go 侧字段同名，定义是 `config.Status`。什么时候上报哪个 `action`，见 [native-layout.md](native-layout.md)。

| 属性 | 类型 |
|:-----|:-----|
| `action` | `Action` |
| `args` | `List<String>` |
| `progress` | `Int` |
| `max` | `Int` |
| `subUpload` | `Long?` |
| `subDownload` | `Long?` |
| `subTotal` | `Long?` |
| `subExpire` | `Long?` |
| `subUpdateInterval` | `Long?` |

订阅相关的五个 `Long?` 默认 `null`。

`Action`：`FetchConfiguration`、`FetchProviders`、`SubscriptionInfo`、`Verifying`。没有 `@SerialName`，JSON 用枚举名。

## LogMessage

一条核心日志。字段是 `level`、`message`、`time`。`time` 的类型是 `Date`。毫秒怎么读写见 [bridge.md](bridge.md)。

`Level` 的 JSON 名：

| 常量 | JSON |
|:-----|:-----|
| `Debug` | `debug` |
| `Info` | `info` |
| `Warning` | `warning` |
| `Error` | `error` |
| `Silent` | `silent` |
| `Unknown` | `unknown` |

## Provider

一个 proxy 或 rule provider。`compareTo` 先比 `type`，再比 `name`。

| 属性 | 类型 |
|:-----|:-----|
| `name` | `String` |
| `type` | `Type` |
| `vehicleType` | `VehicleType` |
| `updatedAt` | `Long` |

`updatedAt` 的单位见 [bridge.md](bridge.md)。

`Type`：`Proxy`、`Rule`。`updateProvider` 把 `Type.toString()` 送过桥。Go 只认这两个名字，见 [bridge.md](bridge.md)。

`VehicleType`：`HTTP`、`File`、`Inline`、`Compatible`。两个枚举都没有 `@SerialName`。列出时跳过 `Compatible`，见 [native-layout.md](native-layout.md)。

## ProviderList

`List<Provider>` 的 Parcel 包装。不参与 JNI。`Clash.queryProviders` 返回的是普通列表。

从 Parcel 恢复时按块读，块大小是 20。

## Proxy

组里的一个节点。

| 属性 | 类型 |
|:-----|:-----|
| `name` | `String` |
| `title` | `String` |
| `subtitle` | `String` |
| `type` | `String` |
| `delay` | `Int` |
| `isGroup` | `Boolean` |

`isGroup` 是 `var`。其余是 `val`。

## ProxyGroup

一个代理组。JSON 字段是 `type`、`proxies`、`now`。`proxies` 的元素是 `Proxy`。

`queryGroup` 得到 null 时，`Clash` 返回的占位值见 [bridge.md](bridge.md)。

`SliceProxyList` 只用于 Parcel，不进 JSON。它按块读 `Proxy`，块大小是 50。`writeToParcel` 先写 `type`，再写这个切片，再写 `now`。

## ProxySort

只在 Kotlin 侧。常量是 `Default`、`Title`、`Delay`。

`queryGroup` 把 `name` 传过桥。Go 怎么把字符串收成排序，见 [bridge.md](bridge.md)。

## Traffic

`typealias Traffic = Long`。没有自己的字段。

高低位怎么装进这一个 `Long`，见 [bridge.md](bridge.md)。

## TunnelState

当前隧道模式。只有字段 `mode`。

Go 写入 `tunnel.Mode().String()`。Kotlin 按下面的 JSON 名解码。

| 常量 | JSON |
|:-----|:-----|
| `Direct` | `direct` |
| `Global` | `global` |
| `Rule` | `rule` |
| `Script` | `script` |

## UiConfiguration

没有属性。`queryConfiguration` 解出来的是空对象，不是当前已加载的配置。见 [bridge.md](bridge.md)。
