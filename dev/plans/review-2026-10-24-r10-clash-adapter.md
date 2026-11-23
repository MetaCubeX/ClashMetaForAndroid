---
title: "审查发现 r10 clash-adapter"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条，最严重的是 url-test 在订阅更新后仍拨号到已被替换的旧节点对象。"
---

# 审查发现 r10 clash-adapter

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖 core/src/foss/golang/clash/adapter。下面 5 条都来自这次读到的源码。

## r10-1
- 严重度：中
- 症状：url-test 在 provider 更新后，只要同名节点还在列表里且旧延迟仍落在 tolerance 内，新连接继续拨到更新前的那个节点对象。节点的服务器、端口或密码已经变了的话，流量仍走旧配置；旧对象的存活状态不会再被健康检查改写，拨号失败后也会一直选它。
- 代码：
```118:145:core/src/foss/golang/clash/adapter/outboundgroup/urltest.go
		fast := proxies[0]
		minDelay := fast.LastDelayForTestUrl(u.testUrl)
		fastNotExist := true
		if u.fastNode != nil && fast.Name() == u.fastNode.Name() {
			fastNotExist = false
		}

		for _, proxy := range proxies[1:] {
			if u.fastNode != nil && proxy.Name() == u.fastNode.Name() {
				fastNotExist = false
			}

			if !proxy.AliveForTestUrl(u.testUrl) {
				continue
			}

			delay := proxy.LastDelayForTestUrl(u.testUrl)
			if delay < minDelay {
				fast = proxy
				minDelay = delay
			}

		}
		// tolerance
		if u.fastNode == nil || fastNotExist || !u.fastNode.AliveForTestUrl(u.testUrl) || u.fastNode.LastDelayForTestUrl(u.testUrl) > fast.LastDelayForTestUrl(u.testUrl)+u.tolerance {
			u.fastNode = fast
		}
		return u.fastNode, nil
```
- 为什么是问题：`provider/provider.go` 的 `setProxies`（103–110 行）每次更新都换成新解析出来的代理对象，并增加 version。`GetProxies` 因此会交出新切片。`fast` 只按名字把 `fastNotExist` 设为 false，比较延迟后仍 `return u.fastNode`。同名新对象要接管，必须让旧对象的 `AliveForTestUrl` 变 false，或旧延迟大于新延迟加 tolerance。健康检查打在新对象上（`healthcheck.go` 的 `setProxies` 只替换 `hc.proxies`）。`URLTest.healthCheck`（97–100 行）只 `Reset` 了 `fastSingle`，不清 `fastNode`。拨号失败走 `onDialFailed`，同样只触发 provider 的健康检查。旧对象的延迟和 alive 停在更新前的成功记录上，同名节点配置变更后选路不再看新对象。`Selector.selectedProxy` 和 `Fallback.findAliveProxy` 每次都从当前列表按名字取对象，这条路径会取回当前实例。
- 方向：名字还在列表里时，把 `fastNode` 换成当前切片中的那个对象，再用它的延迟做 tolerance 比较。

## r10-2
- 严重度：中
- 症状：测速成功但耗时不足 1ms 的节点，延迟被记成 65535。url-test 会把它当成最差节点；若它不是列表第一项，而第一项已经判死，连接会落到这个已死节点上。
- 代码：
```118:132:core/src/foss/golang/clash/adapter/adapter.go
func (p *Proxy) LastDelayForTestUrl(url string) (delay uint16) {
	var maxDelay uint16 = 0xffff

	alive := false
	var history C.DelayHistory

	if state, ok := p.extra.Load(url); ok {
		alive = state.alive.Load()
		history = state.history.Last()
	}

	if !alive || history.Delay == 0 {
		return maxDelay
	}
	return history.Delay
}
```
- 为什么是问题：`URLTest` 在成功时把 `uint16(time.Since(start) / time.Millisecond)` 写入历史（`adapter.go` 176–177 行、280 行）。不足 1ms 时 `Delay` 为 0，同时 `alive` 为 true。129 行把「延迟为 0」和「没有样本 / 不存活」都收成 65535。注释只说明了不存活时返回 `uint16` 最大值；失败记录的 `Delay` 本来就是 0，`!alive` 已经能区分。url-test 的 `fast` 用 `proxies[0]` 当初始候选，而且不看它是否存活（`outboundgroup/urltest.go` 118–137 行），后面的节点只有 `delay < minDelay` 才会替换。死节点的 `LastDelayForTestUrl` 也是 65535，0ms 的活节点替换不进去。`fastNode` 为空或不存活时，142–143 行会把这个死节点存成 `fastNode` 并用于拨号。
- 方向：0ms 的成功样本要保留为可用延迟；初始候选先排除不存活的节点。

## r10-3
- 严重度：中
- 症状：load-balance 的 sticky-sessions 在 TCP 上最多随机试 4 个节点。都不可用时固定返回并缓存下标 0，即使 0 号已经死亡、列表里还有存活节点。接下来约 10 分钟的同一会话键都会先撞上这个死节点。
- 代码：
```253:277:core/src/foss/golang/clash/adapter/outboundgroup/loadbalance.go
		nowIdx := idx
		for i := 1; i < maxRetry; i++ {
			proxy := proxies[nowIdx]
			if proxy.AliveForTestUrl(url) && (!udp || proxy.SupportUDP()) {
				if !udp && (!has || nowIdx != idx) {
					lruCache.Set(key, nowIdx)
				}

				return proxy
			} else {
				nowIdx = int(jumpHash(key+uint64(time.Now().UnixNano()), int32(length)))
			}
		}

		if udp {
			for _, proxy := range proxies {
				if proxy.AliveForTestUrl(url) && proxy.SupportUDP() {
					return proxy
				}
			}
			return nil
		}

		lruCache.Set(key, 0)
		return proxies[0]
```
- 为什么是问题：`maxRetry` 是 5，循环从 `i = 1` 开始，TCP 实际只探 4 次，而且重新哈希用的是 `time.Now().UnixNano()`，不是按列表顺序找下一个存活节点。探不中就 `lruCache.Set(key, 0)` 并返回 `proxies[0]`，返回前没有再判断 `AliveForTestUrl`。缓存 TTL 是 10 分钟（239–244 行）。节点一多、存活比例一低，4 次随机会漏掉存活节点，当前连接和后续命中该键的连接都拨向已死的 0 号。同函数的 UDP 分支以及上面的 `strategyConsistentHashing`（225–235 行）都会先扫完整张表，找到存活节点再考虑退回。
- 方向：TCP 在退回并缓存某个下标之前，先在当前列表里找一个对该测试 URL 存活的节点。

## r10-4
- 严重度：中
- 症状：订阅在健康检查进行中更新时，健康检查 goroutine 可能直接 panic，进程退出；侥幸不崩时，这一轮也可能扫到撕裂后的节点切片，存活结果写到错误的对象上。
- 代码：
```64:66:core/src/foss/golang/clash/adapter/provider/healthcheck.go
func (hc *HealthCheck) setProxies(proxies []C.Proxy) {
	hc.proxies = proxies
}
```
- 为什么是问题：`check`（129–132 行）和 `execute`（177–193 行）在没有锁的情况下读取并 `range hc.proxies`。`hc.mu` 只包住 `registerHealthCheckTask` 里对 `extra` 的修改。更新路径在 `provider/provider.go` 103–110 行持有 `bp.mutex` 时调用 `setProxies`，并 `go bp.healthCheck.check()`；`process` 的 ticker（44–53 行）也会调用 `check`。读切片的 goroutine 不拿 `bp.mutex`。切片头是指针加长度，并发替换可以撕开，`range` 用新指针配旧长度，或旧指针配新长度，都会越界 panic。这条 goroutine 没有 recover，panic 会带走整个进程。`singleDo` 只合并并发的 `check` 调用，不保护 `hc.proxies` 本身。
- 方向：替换和遍历 `hc.proxies` 使用同一把锁，检查循环里用锁内拷贝出来的切片。

## r10-5
- 严重度：低
- 症状：sticky-sessions 把源地址和目的键直接拼在一起。不同的「源 IP + 目的 IP」会得到同一个会话键，于是共用一个出口，并且一方的失败重选会改掉另一方缓存的下标。
- 代码：
```66:74:core/src/foss/golang/clash/adapter/outboundgroup/loadbalance.go
func getKeyWithSrcAndDst(metadata *C.Metadata) string {
	dst := getKey(metadata)
	src := ""
	if metadata != nil {
		src = metadata.SrcIP.String()
	}

	return fmt.Sprintf("%s%s", src, dst)
}
```
- 为什么是问题：`NewLoadBalance` 在 strategy 为 `sticky-sessions` 时用这个函数做默认键（333 行）。`getKey` 对 IP 主机返回 `metadata.Host` 或 `DstIP.String()`（43–63 行），和 `SrcIP.String()` 一样不补零。`192.168.1.1` 访问 `10.0.0.1` 与 `192.168.1.11` 访问 `0.0.0.1` 都拼成 `192.168.1.110.0.0.1`。`strategyStickySessions` 用这个字符串的哈希做 LRU 键（246 行），两个互不相关的流会钉在同一个下标上。
- 方向：源和目的之间放一个不会出现在地址里的分隔符，再拿去哈希。
