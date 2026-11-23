---
title: "审查发现 r12 clash-listener"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条；最严重的是 tunnel 重载用 / 拆监听键，代理名含 / 时会空指针崩溃并丢掉连接"
---

# 审查发现 r12 clash-listener

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖 core/src/foss/golang/clash/listener。下面 5 条都来自这次读到的源码。

## r12-1
- 严重度：高
- 症状：tunnel 的代理名或目标地址里含有 `/` 时，下一次 `PatchTunnel`（配置不变的重载也会走这里）按截断后的键调用 `Close`。映射里没有这个键，直接空指针崩溃，进程里的连接一起丢掉。截断结果若正好等于另一条隧道的键，则会关掉那一条，原来的监听留在 map 里。
- 代码：
```576:629:core/src/foss/golang/clash/listener/listener.go
	tcpOld := lo.Map(
		lo.Keys(tunnelTCPListeners),
		func(key string, _ int) addrProxy {
			parts := strings.Split(key, "/")
			return addrProxy{
				network: "tcp",
				addr:    parts[0],
				target:  parts[1],
				proxy:   parts[2],
			}
		},
	)
	udpOld := lo.Map(
		lo.Keys(tunnelUDPListeners),
		func(key string, _ int) addrProxy {
			parts := strings.Split(key, "/")
			return addrProxy{
				network: "udp",
				addr:    parts[0],
				target:  parts[1],
				proxy:   parts[2],
			}
		},
	)
	oldElm := lo.Union(tcpOld, udpOld)

	newElm := lo.FlatMap(
		tunnels,
		func(tunnel LC.Tunnel, _ int) []addrProxy {
			return lo.Map(
				tunnel.Network,
				func(network string, _ int) addrProxy {
					return addrProxy{
						network: network,
						addr:    tunnel.Address,
						target:  tunnel.Target,
						proxy:   tunnel.Proxy,
					}
				},
			)
		},
	)

	needClose, needCreate := lo.Difference(oldElm, newElm)

	for _, elm := range needClose {
		key := fmt.Sprintf("%s/%s/%s", elm.addr, elm.target, elm.proxy)
		if elm.network == "tcp" {
			tunnelTCPListeners[key].Close()
			delete(tunnelTCPListeners, key)
		} else {
			tunnelUDPListeners[key].Close()
			delete(tunnelUDPListeners, key)
		}
	}
```
- 为什么是问题：监听键在 634 行用 `addr/target/proxy` 拼出来，这里再用 `strings.Split(key, "/")` 拆开，代理名只取 `parts[2]`。代理名 `HK/01` 的键是 `127.0.0.1:9000/1.2.3.4:80/HK/01`，拆开后代理名变成 `HK`。旧列表和新配置的 `proxy` 对不上，`needClose` 重组出 `.../HK`，`tunnelTCPListeners[key]` 为 nil，624 行对 nil 调用 `Close`。
- 方向：监听键的分隔方式要能保留地址、目标和代理名里的 `/`，关闭前先确认键存在。

## r12-2
- 严重度：中
- 症状：只修改 `loopback-address`、`exclude-src-port`、`exclude-src-port-range`、`exclude-dst-port`、`exclude-dst-port-range` 时，正在运行的 TUN 不会按新配置重建，回环地址和端口排除仍是上一份。
- 代码：
```149:160:core/src/foss/golang/clash/listener/config/tun.go
	if t.AutoRedirectIPRoute2FallbackRuleIndex != other.AutoRedirectIPRoute2FallbackRuleIndex {
		return false
	}
	if !slices.Equal(t.RouteAddress, other.RouteAddress) {
		return false
	}
	if t.StrictRoute != other.StrictRoute {
		return false
	}
	if !slices.Equal(t.RouteAddress, other.RouteAddress) {
		return false
	}
```
- 为什么是问题：结构体 31 行的 `LoopbackAddress` 和 43–46 行的四个排除端口字段，在 `Equal`（99–246 行）里都没有比较；152 行和 158 行把 `RouteAddress` 比了两次。`ReCreateTun` 在 543–547 行看到相等就只调用 `OnReload` 并返回。`OnReload`（`sing_tun/server.go` 598–605 行）只补 routing mark 和默认网卡，不重建 TUN。530–532 行的 defer 仍把 `LastTunConf` 写成新配置，这次差异被记成已生效，之后只改这些字段也不会再重建。这些字段会进入 `sing_tun` 的 loopback 和 `ExcludeSrcPort`/`ExcludeDstPort`，所以漏掉的是实际转发规则。
- 方向：`Equal` 纳入上述五个字段，不相等时按现有路径重建 TUN。

## r12-3
- 严重度：中
- 症状：`allow-lan` 打开且 `bind-address` 为 IPv6（`::`、`::1`、全球单播或带 zone 的链路本地地址）时，HTTP、SOCKS、Mixed、Redir、TProxy 的端口监听被当成端口 0 直接返回，不打错误日志；若该端口上已有监听，会先关掉，然后不再起来。
- 代码：
```731:748:core/src/foss/golang/clash/listener/listener.go
func portIsZero(addr string) bool {
	_, port, err := net.SplitHostPort(addr)
	if port == "0" || port == "" || err != nil {
		return true
	}
	return false
}

func genAddr(host string, port int, allowLan bool) string {
	if allowLan {
		if host == "*" {
			return fmt.Sprintf(":%d", port)
		}
		return fmt.Sprintf("%s:%d", host, port)
	}

	return fmt.Sprintf("127.0.0.1:%d", port)
}
```
- 为什么是问题：`allowLan` 为真且 host 不是 `*` 时，用 `%s:%d` 拼出 `:::7890`、`::1:7890` 这种没有方括号的地址。`net.SplitHostPort` 对它们返回 “too many colons”，`portIsZero` 把任何解析错误都当成端口 0。`ReCreateHTTP` 等调用方在真正 `Listen` 之前 return，`err` 仍是 nil，defer 不会报错。
- 方向：用 `net.JoinHostPort` 生成地址，解析失败要作为启动错误留下，不要当成端口 0。

## r12-4
- 严重度：中
- 症状：`dns-hijack` 使用未指定地址和非 53 端口（如 `0.0.0.0:5353` 或 `any:5353`）时，劫持的是所有目的端口 53；配置里的 5353 只有目的地址恰好等于 `0.0.0.0:5353` 才会命中。
- 代码：
```21:27:core/src/foss/golang/clash/listener/sing_tun/dns.go
func (h *ListenerHandler) ShouldHijackDns(targetAddr netip.AddrPort) bool {
	for _, addrPort := range h.DnsAddrPorts {
		if addrPort == targetAddr || (addrPort.Addr().IsUnspecified() && targetAddr.Port() == 53) {
			return true
		}
	}
	return false
}
```
- 为什么是问题：未指定地址这条分支把端口写成字面量 53，没有比较 `addrPort.Port()`。`sing_tun/server.go` 279–289 行已经把 `dns-hijack` 解析成 `netip.AddrPort` 放进 `DnsAddrPorts`，端口在这里被丢掉。默认值 `0.0.0.0:53` 碰巧仍是 53，所以只有把未指定地址的端口改成别的值时，劫持目标才会错。
- 方向：未指定地址应按配置里的端口匹配，不要固定写成 53。

## r12-5
- 严重度：中
- 症状：TProxy 转发 IPv6 UDP 时，回包套接字绑不到原始目的地址，回程写不回去，IPv6 UDP 会话没有回包。
- 代码：
```34:51:core/src/foss/golang/clash/listener/tproxy/udp_linux.go
	fd, err := syscall.Socket(udpAddrFamily(network, lAddr, rAddr), syscall.SOCK_DGRAM, 0)
	if err != nil {
		return nil, err
	}

	defer func() {
		if err != nil {
			syscall.Close(fd)
		}
	}()

	if err = syscall.SetsockoptInt(fd, syscall.SOL_SOCKET, syscall.SO_REUSEADDR, 1); err != nil {
		return nil, err
	}

	if err = syscall.SetsockoptInt(fd, syscall.SOL_IP, syscall.IP_TRANSPARENT, 1); err != nil {
		return nil, err
	}
```
- 为什么是问题：`udpAddrFamily`（85–96 行）在地址是 IPv6 时创建 `AF_INET6` 套接字，随后只设置 `SOL_IP`/`IP_TRANSPARENT`。同文件 17 行定义了 `IPV6_TRANSPARENT`，监听套接字在 `setsockopt_linux.go` 27–28 行对 IPv6 会再设置 `SOL_IPV6`/`IPV6_TRANSPARENT`，回包这条 `dialUDP` 没有。`listenLocalConn` 用它 Bind 非本机的原始目的地址，IPv6 透明选项缺失时绑定失败，`WriteBack` 丢弃回包。
- 方向：IPv6 回包套接字与监听套接字一样设置 `IPV6_TRANSPARENT`。
