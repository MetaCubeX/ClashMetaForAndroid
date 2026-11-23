---
title: "审查方案 r16 clash-control"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 3、drop 0、weaken 2"
---

# 审查方案 r16 clash-control

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 `core/src/foss/golang/clash/` 下的 rules、constant、config、context、dns、hub、log、ntp、tunnel、test、docker，以及 `main.go`、`android_tz.go`。发现稿 5 条引用的文件都重新打开过。rules、context、log、ntp、test、docker、`main.go`、`android_tz.go` 没有被这 5 条引用。

hold 3、drop 0、weaken 2。

drop 无。

## r16-1

- 判定：hold。严重度仍为高。
- 现在行号：`(*systemClient).ExchangeContext` 在 `dns/system.go` 第 32–43 行，空列表改用 `defaultNS` 并清掉错误在第 34–37 行。`Address` 的同一回退在第 48–52 行。`init` 在第 68–74 行，第 71 行把 `defaultNS` 设成 `114.114.114.114:53` 和 `8.8.8.8:53`，`transform` 的 resolver 参数是 nil。`dns/patch_android.go` 第 1 行构建标签是 `android && cmfa`。`UpdateSystemDNS` 在第 16–27 行：空切片先把 `systemResolver` 置 nil，随后仍 `transform` 并写回。`getDnsClients` 在第 29–31 行返回 `systemResolver, nil`。`transform` 对空列表返回长度为 0 的切片（`dns/util.go` 第 116–117 行）。`dns/system_common.go` 第 1 行是相反的构建标签。UDP 且 `proxyAdapter == nil` 时，`tunnel/dns_dialer.go` 第 109–110 行走 `dialer.DialContext`，不走 `proxyAdapter.ListenPacketContext`。默认 nameserver 只填了 `Addr`，`newClient`（`dns/client.go` 第 91–102 行）得到的拨号器没有代理。
- 问题类：系统 DNS 客户端列表为空时，查询被换成两个硬编码公共解析器，并从本机拨号器直接发到它们的 53 端口。
- 不变量：客户端列表长度为 0 时，`ExchangeContext` 返回错误，不把错误改成 nil，不向 `114.114.114.114:53` 或 `8.8.8.8:53` 发包。`UpdateSystemDNS(nil)` 和 `UpdateSystemDNS` 空切片都走这条失败路径。`UpdateSystemDNS` 写入非空地址后，查询使用这份列表，`Address()` 不含 `[defaultNS]`。这个不变量落在共享的 `ExchangeContext` 上，因此非 android 在系统 DNS 一个都不可用时也不再改用 `defaultNS`，而是保留 `system_common.go` 第 70 行已经返回的错误。
- 要动的符号或文件：`dns/system.go` 的 `ExchangeContext`、`Address`、`init` 里对 `defaultNS` 的赋值；`dns/patch_android.go` 的 `UpdateSystemDNS`。空列表不能再依赖「先赋 nil」：第 26 行会覆盖它，而且即便保持 nil，第 34–37 行仍会回退。
- 明确不改什么：非空系统 DNS 的 `transform` 和 `batchExchange`。`system_common.go` 的刷新间隔、黑名单和 `disableTimes`。`dns/dialer.go` 把拨号委托给 `tunnel.DNSDialer` 这件事。主配置里的 nameserver 列表。
- 怎么本地验证：`dns` 下没有测试。用 `android && cmfa` 标签调用 `UpdateSystemDNS(nil)` 和空切片，再对这个 `systemClient` 调 `ExchangeContext`，断言返回错误，且 `Address()` 不含 `[defaultNS]`、`114.114.114.114`、`8.8.8.8`。再传入 `1.1.1.1:53`，断言 `Address()` 含该地址且不含 `[defaultNS]`。验收时不要真的向这两个公共地址发包。

## r16-2

- 判定：weaken。原严重度高，降为中。
- 现在行号：`getGroupDelay` 在 `hub/route/groups.go` 第 53–94 行。`ForceSet("")` 和 `SetSelected(name, "")` 在第 63–64 行，`ParseInt` 在第 69 行，timeout 的 400 在第 70–73 行，expected 的 400 在第 76–80 行，`URLTest` 失败在第 87–90 行返回，成功在第 93 行返回。这几条返回前都没有写回。`patchSelectGroup` 在 `hub/executor/executor.go` 第 448–466 行，只在 `updateProfile` 第 443–445 行 `StoreSelected` 为真时调用。
- 仍成立：非 Selector 且实现 `SelectAble` 的组，延迟请求会在参数接受之前取消内存里的固定选择，并把空选择写入 cache。timeout 或 expected 不合法、以及 `URLTest` 返回错误时，原来的固定节点都不会写回去。`StoreSelected` 为真时，下次 `patchSelectGroup` 应用的是这次留下的 cache 记录。
- 反证：`DELETE` 的 `unfixedProxy`（`hub/route/proxies.go` 第 150–156 行）使用同一条件 `SelectAble && Type != Selector`，并执行同样的 `ForceSet("")` 和 `SetSelected(name, "")`。取消固定是单独接口里的既有行为。`getProxyDelay`（同文件第 106–147 行）测单个节点时不清选择，组延迟才清。因此「一次参数合法且测速成功的组延迟请求会取消固定」与现有取消固定接口一致，不能单独当成测速误伤。
- 问题类：组延迟测试在参数尚未接受时就取消固定选择；测速失败也不恢复。
- 不变量：timeout 或 expected 解析失败时，不调用 `ForceSet`，不调用 `SetSelected`。内存中的固定选择和 cache 与进入 `getGroupDelay` 前一致。`URLTest` 返回错误时，两者也回到进入函数前。timeout 与 expected 都合法且 `URLTest` 成功时，仍取消固定，cache 仍写成空选择。
- 要动的符号或文件：`hub/route/groups.go` 的 `getGroupDelay`。把第 62–65 行挪到第 81 行校验通过之后、`group.URLTest` 之前。调用 `ForceSet("")` 之前，用 `cachefile.Cache().SelectedMap()` 记下该组键是否存在以及原值；`URLTest` 失败时按这份快照写回 cache，并 `ForceSet` 回调用前的固定名。内存中的旧名字要用 `outboundgroup.SelectAble` 上已有的读取方法，在 `ForceSet("")` 之前取到。这次核对没有打开 `adapter`，方案不新写一个读取符号。
- 明确不改什么：`unfixedProxy`。`getProxyDelay`。Selector 不进入这段清空。成功返回时不补写新的选中节点，也不把原节点写回去。`patchSelectGroup` 的匹配规则和 `StoreSelected` 开关。
- 怎么本地验证：`hub` 下没有测试。应新增断言意图：非 Selector 的 `SelectAble` 上，timeout 非数字或 expected 非法时，`ForceSet` 与 `SetSelected` 都没有被调用。两者都合法但 `URLTest` 返回错误时，内存固定名和 `SelectedMap` 与调用前相同。两者都合法且 `URLTest` 成功时，固定名和 cache 仍是空选择。Selector 组测速不调用 `ForceSet("")`。

## r16-3

- 判定：weaken。原严重度中，降为低。
- 现在行号：`withHosts` 的别名分支在 `dns/middleware.go` 第 39–53 行，与发现稿一致。`newHandler` 在第 233–248 行：`useHosts` 时 `withHosts` 在最外层，其后才是 fake-ip、mapping 和 `withResolver`。`parseHosts` 的域名链与环检测在 `config/config.go` 第 1168–1176 行，与发现稿一致。`preHandleMetadata` 在 `tunnel/tunnel.go` 第 289–314 行；redir-host 替换 `DstIP` 在第 300–302 行。`resolveMetadata` 在第 331–333 行再次 `Search`。`handleTCPConn` 在第 575–579 行拨号前再次 `Search`。`constant/dns.go` 第 45–46 行把 `DNSMapping` 显示为 `redir-host`。
- 仍成立：A/AAAA 查询命中「`Search` 未 ok 且 `record.IsDomain`」时，`withHosts` 改写问题名后调用 `next`。`withHosts` 只包一层，改写后的名字不会再进 hosts。别名目标在 hosts 里已有 IP 时，这次应答不用那条 IP，而会进入后面的 fake-ip 或上游。配置允许域名到域名，并沿 `IsDomain` 查环。
- 反证：redir-host 下，映射由应答里的 IP 找回域名、且 `Search(host, false)` 命中 IP 记录时，`preHandleMetadata` 第 300–302 行把 `DstIP` 写成 hosts 的 IP，注释写的是 redir-host should lookup the hosts。`resolveMetadata` 第 331–333 行和 `handleTCPConn` 第 575–579 行会再取一次 hosts IP 用于拨号。发现稿写的「redir-host 下客户端采用应答里的地址，就会连到上游地址」与这三段拨号改写不符。
- 问题类：DNS 应答在 hosts 别名处只改写一次问题名，没有沿 hosts 走到 IP。
- 不变量：`useHosts` 时，A/AAAA 查询若沿 hosts 域名链能够到达 IP 记录，应答里的地址是这条链的 IP。链的末端没有下一条 hosts 记录时，才把这个末端名字交给现有的 `next`。同一域名不重复展开。`parseHosts` 仍拒绝域名环。fake-ip 与 redir-host 的拨号改写保持现在的结果。
- 要动的符号或文件：`dns/middleware.go` 的 `withHosts`。第 41–51 行在调用 `next` 之前继续用 hosts 解析新的问题名，直到 IP 记录或 hosts 里没有下一条。`newHandler` 的中间件顺序保持 `withHosts` 在 fake-ip 之前，避免别名目标已有 hosts IP 时先被 `withFakeIP` 换成 fake-ip。
- 明确不改什么：`preHandleMetadata`、`resolveMetadata`、`handleTCPConn` 里的 hosts 查找。`parseHosts` 的环检测和 `localhost` 默认项。`withMapping` 的安装条件。不把 redir-host 的拨号目标改成应答里的上游地址。
- 怎么本地验证：`dns` 下没有测试。应新增断言意图：hosts 中 A 指向域名 B、B 指向一个 IP 时，对 A 的 A 查询应答包含该 IP，且上游解析器没有收到 B。B 不在 hosts 中时，交给 `next` 的问题名是 B。不验收「redir-host 拨号仍去上游 IP」。

## r16-4

- 判定：hold。严重度仍为中。
- 现在行号：`AddMapping` 与 `RestoreReadFrom` 在 `tunnel/connection.go` 第 42–63 行。发现稿引用到第 62 行，第 63 行是 `RestoreReadFrom` 的结束括号，行为相同。反向表是第 23 行的 `map[netip.Addr]netip.Addr`，第 51–52 行只在该地址尚无有效记录时写入。回包恢复在第 192–200 行，与发现稿一致。`NewPacketAdapter` 在 `constant/adapters.go` 第 307–312 行把 key 设为 `packet.LocalAddr().String()`，`Key` 在第 303–305 行。`handleUDPConn` 在 `tunnel/tunnel.go` 第 447–449 行用这个 key `GetOrCreate` 同一个 `packetSender`。第一次拨号把 `originMetadata` 和解析后的 `dialMetadata` 交给 `AddMapping`（第 494 行）。
- 问题类：同一个本地 UDP 源共用一个 `packetSender`，反向 NAT 只按解析后的 IP 保留第一条原始目标。
- 不变量：同一 `LocalAddr` 上，两个原始 `DstIP` 解析到同一个真实 IP 时，各自回包 `WriteBack` 使用的地址是自己的原始 `DstIP`。后一个目标的回包不再被写成第一个原始地址。不同真实 IP 之间仍按各自的原始目标恢复。
- 要动的符号或文件：`tunnel/connection.go` 的 `targetToOrigin`、`AddMapping`、`RestoreReadFrom`，以及 `constant/adapters.go` 的 `NewPacketAdapter` 或 `tunnel/tunnel.go` 的 NAT 键。回包只带真实地址，单个 `targetToOrigin[真实IP]` 无法同时代表两个原始目标。不同原始目标不要共用同一个 `packetSender`：把原始目标算进 `packetAdapter.key`，或让 `handleUDPConn` 按原始目标分开 `GetOrCreate`。每个 sender 只恢复自己的原始 `DstIP`。
- 明确不改什么：TCP 的 `handleTCPConn`。`originToTarget` 对同一原始键「已有有效地址则不覆盖」。嗅探开启时用 `UDPSniff` 包装 sender。`handleUDPToLocal` 在 `from` 不是 `*net.UDPAddr` 或为 nil 时退回 `oAddrPort`。`Metadata.String` 仍先返回 Host。
- 怎么本地验证：`tunnel` 下没有测试。应新增断言意图：两个包的 `LocalAddr` 相同，原始 `DstIP` 分别是两个不同地址，解析后的 `DstIP` 相同。两个回包经过各自的 `RestoreReadFrom` 后，`WriteBack` 的地址分别是这两个原始地址。只构造一个原始目标时，回包仍恢复成那一个原始地址。

## r16-5

- 判定：hold。严重度仍为中。
- 现在行号：`rcodeClient.ExchangeContext` 在 `dns/rcode.go` 第 42–46 行，就地写 `m.Response` 和 `m.Rcode` 并返回同一个指针。`batchExchange` 在 `dns/util.go` 第 415–449 行。第 422–424 行对具体类型 `rcodeClient` 立刻用外层 `m` 交换并返回。第 426–429 行在此之前已经把同一个 `m` 交给前面的 goroutine。`(*client).ExchangeContext` 第 61 行和 `dns/dot.go` 第 88 行把该指针直接交给 `ExchangeWithConn`。`dns/doh.go` 第 115 行和 `dns/doq.go` 第 97 行会先 `Copy`。无 `ecs`、无 disable 参数时，`rewrapClient`（`dns/util.go` 第 148–159 行，第 237–240 行和第 290 行）保持具体类型 `rcodeClient`。
- 问题类：多个上游并发使用同一条 `*dns.Msg`，不是第一个的 `rcode` 客户端会改这条正在被发送的报文，并把它当作应答返回。
- 不变量：`rcodeClient.ExchangeContext` 不修改调用方传入的报文；返回的是副本，副本的 `Response` 为真且 `Rcode` 为该客户端的值。`batchExchange` 里每个已经启动的上游与 `rcode` 分支不读写同一个报文对象。`rcode` 位于列表第一个时，仍立刻返回对应 rcode，不把后面的上游当作结果。无额外参数的 `rcode://` 仍走这条提前返回。
- 要动的符号或文件：`dns/rcode.go` 的 `ExchangeContext`；`dns/util.go` 的 `batchExchange`。`rcode` 返回 `m.Copy()` 之后的修改。每个 `fast.Go` 传入的报文使用调用前的副本。提前返回的那次 `ExchangeContext` 也使用副本。被 `clientWithEdns0Subnet` 或 `clientWithDisableTypes` 包住时，第 422 行的具体类型断言认不出 `rcode`；提前返回要认得出包装里面的 `rcode` 客户端，或者让包装后的 `ExchangeContext` 同样只改副本。
- 明确不改什么：`rcode` 提前返回时第二个结果仍是 `false`（第 424 行），不等待其它上游的结果。`newRCodeClient` 接受的 rcode 名字。`config/config.go` 第 1269–1281 行对 `rcode` 方案的解析。DoH、DoQ 自己的 `Copy`。`clientWithEdns0Subnet` 已有的副本行为。
- 怎么本地验证：`dns` 下没有测试。应新增断言意图：第一个客户端记录它所看到的 `Response`，第二个是 `rcode`。第一个看到的 `Response` 仍是调用前的值，返回报文的 `Rcode` 是该 rcode 客户端的值，调用方原报文的 `Response` 和 `Rcode` 不变。只有 `rcode` 时，返回对应 rcode，且原报文不变。`rcode` 在第一个时，后面的客户端不被调用。
