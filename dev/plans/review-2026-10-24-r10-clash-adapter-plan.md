---
title: "审查方案 r10 clash-adapter"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 4、drop 0、weaken 1"
---

# 审查方案 r10 clash-adapter

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 `core/src/foss/golang/clash/adapter`。发现稿 5 条。

- hold：4（r10-1、r10-2、r10-3、r10-5）
- drop：0
- weaken：1（r10-4，由中降为低）

drop：无。

## r10-1

结论：hold。严重度仍是中。

`URLTest.fast` 里按名字保留旧对象的逻辑仍在 `outboundgroup/urltest.go` 118–145 行，与发现稿一致。同名只把 `fastNotExist` 设为 false，142–145 行仍在旧 `fastNode` 上查存活和延迟，然后 `return u.fastNode`。`NewProxiesParser`（`provider/provider.go` 449–455 行）每次 `adapter.ParseProxy`，`setProxies`（103–112 行；发现稿写到 110，替换、`version++` 和 `go check` 仍在 104–110）换成新对象。`HealthCheck.setProxies` 仍是 64–66 行，只替换 `hc.proxies`。`URLTest.healthCheck` 现在是 97–101 行，98 和 100 行只 `Reset` `fastSingle`，不清 `fastNode`。`GroupBase.onDialFailed`（`groupbase.go` 265–300 行）不改被拨号对象的 alive。`Selector.selectedProxy`（`selector.go` 103–112 行）和 `Fallback.findAliveProxy`（`fallback.go` 105–122 行）仍从当前列表按名字取对象。

另外，`fastSingle` 的等待是 10 秒（`urltest.go` 216 行）。订阅更新不调用 `URLTest.healthCheck`，缓存命中时 `fast` 直接返回上一次的对象（147–151 行），不会走进 118–145 行。这不推翻发现稿，但同一条拨号路径在 version 变化后还会再交出旧对象最多 10 秒。

问题类：选路对象身份。同名节点配置已换，新连接仍可能拨到更新前的那个对象。

不变量：`URLTest.fast` 交出去的对象必须属于这一次 `GetProxies` 的切片。名字还在列表里时，先把 `fastNode` 换成当前切片里的那个对象，再用这个当前对象的延迟做 tolerance 比较。provider version 变了之后，10 秒缓存不得继续返回已经不在当前切片里的对象。拨号失败不得靠旧对象身上冻结的 alive 把旧对象留在选路结果里。

要动的符号或文件：`URLTest.fast`、`URLTest.fastNode`、`URLTest.fastSingle`，文件 `outboundgroup/urltest.go`。

明确不改什么：不改 `baseProvider.setProxies` 每次换新对象的方式，不让健康检查继续测已经被换下的旧对象。不改 `Selector.selectedProxy` 和 `Fallback.findAliveProxy`。不改「延迟差在 tolerance 以内就留在当前名字」这件事，也不在每次 `healthCheck` 里清空 `fastNode` 来绕开 tolerance。不改 `singledo` 的实现，不把 10 秒改成别的时长。`selected` 非空时 106–114 行已经绑定当前对象，那条「必须存活才固定」的语义不动。

怎么本地验证：在 `core/src/foss/golang/clash` 用 `package outboundgroup` 的测试，替身实现 `Name`、`AliveForTestUrl`、`LastDelayForTestUrl`，provider 替身提供 `Version` 和 `Proxies`。准备同名的旧对象和新对象，旧延迟与新延迟之差不超过 tolerance，两者都存活。`fastSingle.Reset()` 后调用 `fast`，返回值必须是新对象。再测缓存：version 为 1 时 `fast` 得到旧对象，然后只增加 version 并换上新切片、不调用 `healthCheck`，立刻再 `fast`，返回值必须是新对象。命令：

`go test ./adapter/outboundgroup/ -count=1 -run TestURLTestUsesCurrentProxyAfterProviderUpdate`

不需要联网。

## r10-2

结论：hold。严重度仍是中。

`Proxy.LastDelayForTestUrl` 仍在 `adapter/adapter.go` 118–132 行。129 行在 `!alive` 或 `history.Delay == 0` 时返回 `0xffff`。成功路径仍把 `uint16(time.Since(start) / time.Millisecond)` 写入历史（280 行赋值，176–177 行在 `alive` 时写入 `record.Delay`）。不足 1ms 时 `Delay` 为 0 且 `alive` 为 true，于是被收成 65535。`URLTest.fast` 仍用 `proxies[0]` 做初始候选且不看存活（118–119 行），后面的节点只有 `delay < minDelay` 才替换（125–137 行）。死节点的 `LastDelayForTestUrl` 也是 65535，所以 0ms 的活节点替换不进去。`fastNode` 为空或不存活时，142–143 行会把这个初始候选存下来并在 145 行返回。

问题类：延迟哨兵。成功的 0ms 样本和失败被收成同一个最大值，url-test 因此可能拨到已死的第一项。

不变量：该测试 URL 上最后一条样本是成功且 `Delay` 为 0 时，`LastDelayForTestUrl` 返回 0。没有该 URL 的样本，或不存活，仍返回 65535。`URLTest.fast` 的初始候选若对该 URL 不存活，则不能压过后面第一个存活节点。

要动的符号或文件：`Proxy.LastDelayForTestUrl`，文件 `adapter/adapter.go`；`URLTest.fast` 的初始候选，文件 `outboundgroup/urltest.go`。

明确不改什么：不改失败样本「`alive == false` 且 `Delay == 0`」的记法，不改 65535 作为不存活或无样本的哨兵，不改历史队列长度，不改 `URLTest` 的计时方式（含 unified delay）。不在本条改 `fastNode` 的对象身份，那是 r10-1。不改 tolerance 公式。

怎么本地验证：在 `package adapter` 里直接写入 `extra`：存活且最后一条 `Delay` 为 0 时期望 0；不存活且 `Delay` 为 0 时期望 65535；没有该 URL 的状态时期望 65535。在 `package outboundgroup` 里用替身：第一项不存活且延迟 65535，第二项存活且延迟 0，`fastNode` 为空，`fast` 必须返回第二项。命令：

`go test ./adapter/ -count=1 -run TestLastDelayForTestUrlKeepsZeroSuccess`

`go test ./adapter/outboundgroup/ -count=1 -run TestURLTestSkipsDeadInitialCandidate`

不需要联网。

## r10-3

结论：hold。严重度仍是中。

`strategyStickySessions` 的 TCP 退回仍在 `outboundgroup/loadbalance.go` 253–277 行。`maxRetry` 仍是 5（241 行），循环从 `i := 1` 开始，实际 4 次。失败后仍用 `time.Now().UnixNano()` 重新 `jumpHash`（263 行），不是按顺序找下一个存活节点。TCP 在 276–277 行 `lruCache.Set(key, 0)` 并返回 `proxies[0]`，返回前不看 `AliveForTestUrl`。缓存仍是 10 分钟（239–244 行，`WithAge` 用秒，且 `Get` 默认不续期）。同一次失败退回里的 `Set` 会把过期时间重新写成现在加 10 分钟。UDP 分支（267–273 行）和 `strategyConsistentHashing`（225–235 行）在放弃前会扫过当前列表。后续命中该键时，246–255 行会先试缓存下标，再做随机重试；「先撞上 0 号」仍在。若某次重试找到存活节点，257–258 行会改掉缓存，所以不是之后每一次连接都只拨 0 号。发现稿症状里的「先撞上」与这段一致。

问题类：退回选路。TCP sticky-sessions 在随机试探失败后缓存并返回已死下标。

不变量：TCP 上，只要当前列表里有对该测试 URL 存活的节点，`strategyStickySessions` 就不得返回不存活的节点，也不得把这样一个下标写入 LRU。没有任何存活节点时可以退回 `proxies[0]`。已经选中的存活下标仍按现有 10 分钟过期。失败退回不得反复 `Set` 一条指向已死下标的缓存。

要动的符号或文件：`strategyStickySessions`，文件 `outboundgroup/loadbalance.go`。

明确不改什么：不改 UDP 分支「扫完再返回 nil」。不改 `strategyConsistentHashing` 和 `strategyRoundRobin`。不改 `jumpHash`。不改 LRU 容量 1000 和成功钉住后的 10 分钟。不改 `getKeyWithSrcAndDst`，那是 r10-5。不要求把 4 次随机试探改成 5 次。

怎么本地验证：在 `package outboundgroup` 调用 `strategyStickySessions`。至少 8 个替身，只有最后一项 `AliveForTestUrl` 为真，`udp` 为 false，同一 metadata 连续调用不少于 30 次，每次返回值都必须是那一项。再测 `udp` 为 true 且没有任何节点同时存活并 `SupportUDP` 时，返回值仍是 nil。命令：

`go test ./adapter/outboundgroup/ -count=1 -run 'TestStickySessionsTCPFallsBackToAlive|TestLoadBalance'`

不需要联网。已有的 `TestLoadBalanceHashKey*` 应保持通过。

## r10-4

结论：weaken。严重度由中降为低。

无锁替换仍然成立。`HealthCheck.setProxies` 仍是 `provider/healthcheck.go` 64–66 行，直接写 `hc.proxies`。`check` 现在 129–132 行只读 `len(hc.proxies)`，真正的 `range` 在 `execute` 177–193 行。两处都不拿锁。`hc.mu` 仍只包住 `registerHealthCheckTask` 里对 `extra` 的修改（81–82 行）。`baseProvider.setProxies` 在持有 `bp.mutex` 时调用 `setProxies` 并 `go check()`（103–110 行），`check` 不拿 `bp.mutex`。`process` 的 ticker 仍会调用 `check`（44–53 行）。`singleDo` 的等待是 1 秒（221 行），只合并并发的 `check`，不拷贝 `hc.proxies`。`healthcheck.go` 里没有 `recover`。`errgroup.Group.Go`（`golang.org/x/sync` v0.11.0）也不 recover。切片头的并发读写是数据竞争；长度对不上时可以越界或在 nil 接口上 panic，进程会退出。

反证：不是两种撕裂都一定会越界。切片头是指针、len、cap。只有读到的 len 大于该底层数组的 cap 时，`range` 才会越界。两次更新的节点数相同，且读到的头落在 cap 以内时，不会因为长度错配 panic。`NewProxiesParser` 从空切片 `append`（`provider.go` 396 行起），cap 里多出来的槽是 nil，不是另一个活代理。撕裂后若 len 仍在 cap 内，接着对 nil 调 `Name` 或 `URLTest` 会 panic，不是把存活结果写进另一个活节点。`range` 只有一个数组指针：没有撕裂时，检查的是整份旧切片或整份新切片，结果写在那份切片自己的对象上。因此「可能因数据竞争 panic」成立，「不崩则把存活写到错误的活对象上」对不上当前的分配方式。

问题类：数据竞争。健康检查替换节点切片和遍历切片没有同一把锁。

不变量：`setProxies` 写入 `hc.proxies` 与检查循环读取它必须使用同一把锁。检查循环使用锁内拷贝出来的切片，`URLTest` 在锁外执行。修复之后，并发 `setProxies` 与 `check` 不得在 `hc.proxies` 上形成数据竞争，也不得因此 panic。

要动的符号或文件：`HealthCheck.setProxies`、`HealthCheck.check`、`HealthCheck.execute`、`hc.proxies`、`hc.mu`，文件 `provider/healthcheck.go`。

明确不改什么：不靠 `recover` 掩盖竞争。不改 `singleDo` 的 1 秒合并窗口，不改检查 URL、过滤器和超时。不把 `bp.mutex` 一直持有到 `URLTest` 结束。锁内只做替换和拷贝。不把「写到错误的活对象」当成必须复现的失败形态。不在本条改 `check` 对 `hc.extra` 的无锁遍历（145–148 行）。

怎么本地验证：在 `package provider` 里并发调用 `setProxies`（交替换成不同长度的切片）和 `check`。替身的 `URLTest` 立刻返回，避免打网。修复后运行：

`go test -race ./adapter/provider/ -count=1 -run TestHealthCheckSetProxiesDuringCheck`

`-race` 不得报告 `hc.proxies` 上的数据竞争，测试不得 panic。

## r10-5

结论：hold。严重度仍是低。

`getKeyWithSrcAndDst` 仍在 `outboundgroup/loadbalance.go` 66–74 行，`fmt.Sprintf("%s%s", src, dst)`，中间没有分隔符。`getKey` 仍在 43–63 行：Host 是 IP 时返回 `metadata.Host`，否则返回 `DstIP.String()`。`netip.Addr.String()` 不补零。已用 `netip.MustParseAddr` 核对：`192.168.1.1` 拼 `10.0.0.1` 与 `192.168.1.11` 拼 `0.0.0.1` 都是 `192.168.1.110.0.0.1`。`NewLoadBalance` 在 `sticky-sessions` 时仍把 `withKey(getKeyWithSrcAndDst)` 交给 `strategyStickySessions`（333 行）。`hash-key` 为空时 `withKey` 是恒等函数；`in-user` 只在 `InUser` 非空时换键，未认证请求仍落到这个拼接。`strategyStickySessions` 246 行对这个字符串做 `utils.MapHash`，相同字符串共用同一个 LRU 下标，一侧 `Set` 会改掉另一侧的缓存。

问题类：会话键。不同的源地址和目的地址拼出同一个 sticky-sessions 键。

不变量：`getKeyWithSrcAndDst` 对上述两对地址必须得到不同字符串，从而 `MapHash` 不同。源和目的之间要有一个不会出现在 `netip.Addr.String()` 或 `getKey` 所返回域名中的分隔符，然后再哈希。

要动的符号或文件：`getKeyWithSrcAndDst`，文件 `outboundgroup/loadbalance.go`。

明确不改什么：不改 `getKey` 的 IP、eTLD+1 和 `DstIP.String()` 规则。不改 `getKeyWithInUser` 和 `hash-key` 的取值。不改 `strategyConsistentHashing` 使用的 `getKey`。不改 `utils.MapHash`。不把端口编进键。

怎么本地验证：在 `package outboundgroup` 构造两份 metadata，一对是源 `192.168.1.1`、目的 `10.0.0.1`，另一对是源 `192.168.1.11`、目的 `0.0.0.1`，断言 `getKeyWithSrcAndDst` 的结果不同。命令：

`go test ./adapter/outboundgroup/ -count=1 -run 'TestStickySessionKeySeparatesAmbiguousIPs|TestLoadBalance'`

不需要联网。已有的 `TestLoadBalanceHashKey*` 应保持通过。
