---
title: "审查方案 r12 clash-listener"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 4、drop 1、weaken 0"
---

# 审查方案 r12 clash-listener

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 `core/src/foss/golang/clash/listener`。发现稿 5 条的引用都在这个目录里重新打开过。

hold 4、drop 1、weaken 0。

drop：r12-5。`tproxy/udp_linux.go` 第 49 行确实只设置 `SOL_IP`/`IP_TRANSPARENT`，监听路径 `setsockopt_linux.go` 第 27–28 行仍额外设置 `IPV6_TRANSPARENT`。Linux 6.8 的 `ipv6_can_nonlocal_bind` 只看 `INET_FLAGS_TRANSPARENT`；`IP_TRANSPARENT` 与 `IPV6_TRANSPARENT` 写的是同一个位。对 `AF_INET6` 的 `SOCK_DGRAM` 调用 `setsockopt(SOL_IP, IP_TRANSPARENT)` 返回 `EPERM`，与 `IPV6_TRANSPARENT` 相同，是能力检查，不是协议不支持。`dialUDP` 在 `Bind` 之前已经设置这个位。缺的那次调用不会让回包绑不上原始目的地址。不是缺陷。

## r12-1

- 判定：hold。严重度仍为高。
- 现在行号：`PatchTunnel` 在 `listener/listener.go` 第 565–653 行。发现稿引用的第 576–629 行仍是按 `/` 拆键、再按三段拼回并 `Close`。拆分在第 579–584 行和第 591–596 行，`parts[2]` 仍是代理名。关闭在第 622–628 行，其中第 624 行对 TCP、第 627 行对 UDP。建监听的键在第 634 行，格式仍是 `addr/target/proxy`。`tunnelTCPListeners` 第 42 行是 `map[string]*tunnel.Listener`，`tunnelUDPListeners` 第 43 行是 `map[string]*tunnel.PacketConn`。`(*tunnel.Listener).Close` 在 `tunnel/tcp.go` 第 32–34 行写 `l.closed`，`(*tunnel.PacketConn).Close` 在 `tunnel/udp.go` 第 33–35 行写 `l.closed`。映射里没有的键取到 nil，再调用 `Close` 会空指针崩溃。
- 问题类：监听键用 `/` 拼接，又用 `strings.Split` 只取前三段。代理名或目标里的 `/` 回不来。下一次 `PatchTunnel` 即使隧道列表没变，解析出的旧元素也和新元素不相等，`needClose` 用截断后的字段拼出一个映射里没有的键。该键若碰巧等于另一条隧道，会关掉那一条，原来的监听留在 map 里。
- 不变量：同一条隧道的地址、目标、代理名拆开再拼回，必须等于当初放进 `tunnelTCPListeners` 或 `tunnelUDPListeners` 的键。`Close` 只作用于映射里存在的键。代理名 `HK/01`、目标 `example.com/foo:80` 在两次相同的 `PatchTunnel` 之后，原来的监听仍在，进程不因这次 diff 崩溃。截断结果不得关闭另一条键。
- 要动的符号或文件：`listener.go` 的 `PatchTunnel`，以及它写入和读出 `tunnelTCPListeners`、`tunnelUDPListeners` 的键。键要能保留地址、目标和代理名中的 `/`。第 621–630 行关闭前先判断键存在；不存在则不能调用 `Close`。TCP 和 UDP 两条路径一起改。
- 明确不改什么：`inbound/tunnel.go` 的入站隧道不使用这对 map，不改。`tunnel.New`、`tunnel.NewUDP` 的转发和 `socks5.ParseAddr` 校验不改。`needClose` / `needCreate` 仍由新旧隧道集合的差决定，不改成每次全关全开。
- 怎么本地验证：在 `listener` 包里准备代理名为 `HK/01` 的隧道，地址和目标都不含 `/`。第一次 `PatchTunnel` 后映射里有完整键。用同一列表再调用一次，不得 panic，完整键仍在，监听地址不变。再准备一条代理名恰好等于前一条被 `/` 截断后的名字、地址和目标相同的隧道，两次 `PatchTunnel` 后两条都还在，不得只剩截断键。目标用 `example.com/foo:80`（`net.SplitHostPort` 能通过）重复上述往返。没有 `/` 的旧用例仍只在配置真正变化时关闭。

## r12-2

- 判定：hold。严重度仍为中。
- 现在行号：`LoopbackAddress` 在 `listener/config/tun.go` 第 31 行。`ExcludeSrcPort`、`ExcludeSrcPortRange`、`ExcludeDstPort`、`ExcludeDstPortRange` 在第 43–46 行。`Equal` 在第 99–247 行，这五个字段都没有比较。第 152–154 行和第 158–160 行把 `RouteAddress` 比了两次，中间第 155–157 行是 `StrictRoute`。`Sort` 在第 72–97 行，也不排序这五个字段。`ReCreateTun` 在 `listener.go` 第 526–563 行；第 530–533 行的 defer 在返回前把 `LastTunConf` 写成这份新配置；第 543–547 行相等时只调用 `OnReload` 并返回。`(*sing_tun.Listener).OnReload` 在 `sing_tun/server.go` 第 598–605 行，只补 routing mark 和默认网卡。创建路径第 244–258 行把排除端口解析进 `excludeSrcPort` / `excludeDstPort`，第 399–400 行和第 410–411 行写入 `tun.Options` 的 loopback 与 `ExcludeSrcPort` / `ExcludeDstPort`。
- 问题类：只改回环地址或四个排除端口时，`Equal` 仍为真。正在运行的 TUN 不重建，`OnReload` 也不把这五个字段送进已创建的栈。defer 却把新值记入 `LastTunConf`，之后仍只改这些字段也不会重建。
- 不变量：`ReCreateTun` 先 `Sort` 再 `Equal`。两份配置只有这五个字段之一不同时，`Equal` 为 false，走关闭并 `sing_tun.New` 的路径。这五个字段在 `Sort` 之后相同、其余字段也相同，`Equal` 为 true。`RouteAddress` 只比较一次，且仍能单独引起重建。
- 要动的符号或文件：`config/tun.go` 的 `Sort` 和 `Equal`。`Equal` 在 `ExcludeUIDRange` 之后、`IncludeAndroidUser` 之前比较四个端口字段；在现在第一处 `RouteAddress` 的位置比较 `LoopbackAddress`，删掉这次重复，保留第 158 行那次 `RouteAddress`。`Sort` 为 `[]netip.Addr` 使用 `Addr.Compare`，端口号和端口范围字符串使用已有的 `slices.Sort`。
- 明确不改什么：`OnReload` 仍只恢复 routing mark 和默认网卡。`ReCreateTun` 相等时提前返回、defer 写 `LastTunConf` 的顺序不改。`inbound/tun.go` 的 `TunOption.Equal` 已按整份配置字符串比较，不改。`sing_tun.New` 里组装 `tun.Options` 的方式不改。
- 怎么本地验证：在 `listener/config` 包构造两份只差一个字段的 `Tun`，字段依次为 `LoopbackAddress`、`ExcludeSrcPort`、`ExcludeSrcPortRange`、`ExcludeDstPort`、`ExcludeDstPortRange`。各自 `Sort` 后 `Equal` 为 false。五字段相同、仅顺序不同，`Sort` 后 `Equal` 为 true。只改 `RouteAddress` 仍为 false。不需要创建 TUN 设备。

## r12-3

- 判定：hold。严重度仍为中。
- 现在行号：`portIsZero` 在 `listener.go` 第 731–737 行，`SplitHostPort` 任何错误都返回 true。`genAddr` 在第 739–748 行；`allowLan` 且 host 不是 `*` 时仍是 `fmt.Sprintf("%s:%d", host, port)`。调用点：`ReCreateHTTP` 第 118 行生成、第 120–126 行地址变化就 `Close`、第 128–129 行当端口 0 返回；`ReCreateSocks` 第 152、157–173、179–180 行；`ReCreateRedir` 第 212、217–233、239–240 行；`ReCreateTProxy` 第 422、427–443、449–450 行；`ReCreateMixed` 第 481、486–501、507–508 行。这些函数的 defer 只在 `err != nil` 时打日志，这条返回路径没有给 `err` 赋值。`SetBindAddress` 第 103–105 行原样保存 host，目录内没有补方括号。
- 问题类：`allow-lan` 打开且 bind host 是未加方括号的 IPv6 时，拼出来的 `:::7890`、`::1:7890`、`2001:db8::1:7890`、`fe80::1%eth0:7890` 被 `SplitHostPort` 判成 too many colons。`portIsZero` 把解析失败当成端口 0，五个重建函数在 `Listen` 前返回，不打错误日志。旧监听的地址字符串与这个非法地址不同，会先被关掉。
- 不变量：`allowLan` 为真且 host 不是 `*` 时，`::`、`::1`、全球单播、带 zone 的链路本地地址生成的字符串能被 `SplitHostPort` 解出原来的端口。host 为 `*` 时结果仍是 `:%d`，监听所有接口，不能变成 `*:port`。host 已经是 `[::]` 这种带方括号的形式时，不能再套一层方括号。端口 0 或空端口仍表示不监听。解析失败必须留下启动错误，不能走端口 0 的静默返回。
- 要动的符号或文件：`listener.go` 的 `genAddr` 和 `portIsZero`，以及 `ReCreateHTTP`、`ReCreateSocks`、`ReCreateRedir`、`ReCreateTProxy`、`ReCreateMixed` 里调用 `portIsZero` 后直接 return 的路径。非 `*` 的 host 用 `net.JoinHostPort` 前先去掉已经存在的一层方括号。`portIsZero` 只把端口 `"0"` 和空端口当成不监听；`SplitHostPort` 失败时由这五个函数记录错误并返回。
- 明确不改什么：`allowLan` 为 false 时仍固定 `127.0.0.1:port`。地址字符串变化后先关旧监听、再听新地址的顺序不改。端口 0 表示关闭该入站。`SetBindAddress` 不改写用户写入的 host。Shadowsocks、VMess、TUIC 不走 `genAddr`，不改。
- 怎么本地验证：在 `listener` 包对 `genAddr` 加表驱动测试。`allowLan=true` 时，`::`、`::1`、`2001:db8::1`、`fe80::1%eth0`、`[::]` 与端口 7890 的结果都能被 `SplitHostPort` 解出 `7890`，且 `portIsZero` 为 false。`*` 仍得到 `:7890`。`0.0.0.0` 与 `127.0.0.1` 保持 `host:7890`。端口 0 仍被 `portIsZero` 判为 true。再给一个 `SplitHostPort` 无法解析的地址，断言它不会被当成端口 0，并且五个重建函数的错误路径会留下非 nil 错误。测试不需要真的绑 IPv6 端口。

## r12-4

- 判定：hold。严重度仍为中。
- 现在行号：`ShouldHijackDns` 在 `sing_tun/dns.go` 第 21–27 行。未指定地址的分支仍是 `targetAddr.Port() == 53`，没有比较 `addrPort.Port()`。`sing_tun/server.go` 第 279–290 行把 `dns-hijack` 去 scheme、把首个 `any` 换成 `0.0.0.0` 后 `netip.ParseAddrPort`，端口留在 `DnsAddrPorts`。第 293–301 行另外把 TUN 地址的下一个地址配上端口 53 放进同一切片，那是具体地址，不是这条未指定地址分支。
- 问题类：`0.0.0.0:5353`、`any:5353`、`[::]:5353` 解析后地址是未指定、端口不是 53。命中条件却劫持所有目的端口 53；配置里的 5353 只有目的地址正好等于该未指定地址加 5353 才命中。默认 `0.0.0.0:53` 因为端口碰巧是 53，行为仍对。
- 不变量：`DnsAddrPorts` 里地址未指定时，只劫持目的端口等于该条目端口的流量。地址不是未指定时，仍要整个 `AddrPort` 相等。`0.0.0.0:53` 和 `[::]:53` 继续劫持任意地址的端口 53。`0.0.0.0:5353` 劫持任意地址的端口 5353，不劫持端口 53。
- 要动的符号或文件：`sing_tun/dns.go` 的 `ShouldHijackDns`。未指定分支改为比较 `addrPort.Port()` 与 `targetAddr.Port()`。
- 明确不改什么：`server.go` 第 279–301 行的解析、`any` 替换，以及把接口地址下一个地址配成 `:53` 的条目。TCP、UDP 两个调用点仍只调用 `ShouldHijackDns`，不各写一套端口判断。
- 怎么本地验证：在 `sing_tun` 包构造 `ListenerHandler`，`DnsAddrPorts` 只放 `0.0.0.0:5353`。目的地 `1.2.3.4:5353` 和 `[2001:db8::1]:5353` 为真；`1.2.3.4:53`、`8.8.8.8:53` 为假；`0.0.0.0:5353` 为真。再只放 `0.0.0.0:53`，任意地址的端口 53 为真，端口 5353 为假。再只放 `[::]:5353`，任意地址的端口 5353 为真，端口 53 为假。具体地址 `172.19.0.2:53` 只在目的地完全相等时为真。
