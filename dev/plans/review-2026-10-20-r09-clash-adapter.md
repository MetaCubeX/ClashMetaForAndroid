---
title: "审查发现 r09 clash-adapter"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "fallback 会清掉固定节点并可能选中死代理，url-test 容差在首节点失效，分组 expected-status 与全局存活标记不一致，load-balance 的 UDP 选路不看成员能力。"
---

# 审查发现 r09 clash-adapter

范围只包括 `core/src/foss/golang/clash/adapter`。下面 5 条都来自这次读到的源码。

## r09-1

- 编号：r09-1
- 严重度：中
- 症状：fallback 手动固定的节点一旦探活失败，这次查找会清掉固定选择；排在它前面的存活节点被跳过，流量可能落到已经死亡的第一个节点。

```105:123:core/src/foss/golang/clash/adapter/outboundgroup/fallback.go
func (f *Fallback) findAliveProxy(touch bool) C.Proxy {
	proxies := f.GetProxies(touch)
	for _, proxy := range proxies {
		if len(f.selected) == 0 {
			if proxy.AliveForTestUrl(f.testUrl) {
				return proxy
			}
		} else {
			if proxy.Name() == f.selected {
				if proxy.AliveForTestUrl(f.testUrl) {
					return proxy
				} else {
					f.selected = ""
				}
			}
		}
	}

	return proxies[0]
}
```

```86:92:core/src/foss/golang/clash/adapter/outboundgroup/fallback.go
	return json.Marshal(map[string]any{
		"type":           f.Type().String(),
		"now":            f.Now(),
		"all":            all,
		"testUrl":        f.testUrl,
		"expectedStatus": f.expectedStatus,
		"fixed":          f.selected,
```

`Now`、`DialContext`、`ListenPacketContext`、`SupportUDP`、`Unwrap` 都走 `findAliveProxy`。固定节点不存活时，循环把 `f.selected` 置空，但不会从头再扫。列表是 `[A 死亡, B 存活, C 被固定且死亡]` 时，A 和 B 都因为当时 `selected` 还是 C 被跳过，C 被清掉之后循环结束，返回 A。B 可用，实际拨出去的是死节点。`MarshalJSON` 先调用 `Now()` 再读 `fixed`，一次状态查询就会把用户的固定选择清掉，接口里的 `fixed` 同时变成空。节点恢复后也不会再回到原来的固定项。

方向：固定节点失败时保留 `selected`，并从列表开头重找第一个存活代理。

## r09-2

- 编号：r09-2
- 严重度：中
- 症状：url-test 当前节点位于列表第一位时，`tolerance` 不生效，后面只要有延迟更低的节点就会立刻换出去。

```118:141:core/src/foss/golang/clash/adapter/outboundgroup/urltest.go
		fast := proxies[0]
		minDelay := fast.LastDelayForTestUrl(u.testUrl)
		fastNotExist := true

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
```

`fastNotExist` 只在 `proxies[1:]` 里查找当前节点。当前最快节点就是 `proxies[0]` 时，它一直保持 `true`，容差比较被短路。例如当前节点延迟 100、下一名 90、容差 50，差值在容差内仍会切到下一名。`fastSingle` 把这个结果缓存 10 秒，切换会保持到缓存失效或健康检查重置。

方向：确认当前节点是否还在列表中时包含 `proxies[0]`，未超过容差就继续用它。

## r09-3

- 编号：r09-3
- 严重度：中
- 症状：分组配置了 `expected-status`，但探活仍按提供者自己的期望状态码判断，不符合分组状态码的节点会继续被当成存活。

```138:151:core/src/foss/golang/clash/adapter/outboundgroup/parser.go
		// if test URL is empty, use the first health check URL of providers
		if groupOption.URL == "" {
			for _, pd := range PDs {
				if pd.HealthCheckURL() != "" {
					groupOption.URL = pd.HealthCheckURL()
					break
				}
			}
			if groupOption.URL == "" {
				groupOption.URL = C.DefaultTestURL
			}
		} else {
			addTestUrlToProviders(PDs, groupOption.URL, expectedStatus, groupOption.Filter, uint(groupOption.Interval))
		}
```

```68:72:core/src/foss/golang/clash/adapter/provider/healthcheck.go
func (hc *HealthCheck) registerHealthCheckTask(url string, expectedStatus utils.IntRanges[uint16], filter string, interval uint) {
	url = strings.TrimSpace(url)
	if len(url) == 0 || url == hc.url {
		log.Debugln("ignore invalid health check url: %s", url)
		return
	}
```

```134:136:core/src/foss/golang/clash/adapter/provider/healthcheck.go
		// execute default health check
		option := &extraOption{filters: nil, expectedStatus: hc.expectedStatus}
		hc.execute(b, hc.url, id, option)
```

分组没写 `url` 时会抄提供者的探活 URL，并且不会调用 `addTestUrlToProviders`，已经解析好的 `expectedStatus` 被丢掉。分组写了同一个 URL 时，`registerHealthCheckTask` 在入口直接返回，分组的期望状态码同样不会进 `extra`。真正执行的默认探活用的是 `hc.expectedStatus`。url-test 和 fallback 用这个 URL 的 `AliveForTestUrl` 做选择，于是提供者接受、分组拒绝的状态码仍会让节点保持被选中。

方向：有效探活 URL 与提供者 URL 相同时，仍把分组的 `expected-status` 用于这次探活。

## r09-4

- 编号：r09-4
- 严重度：中
- 症状：探活收到了 HTTP 响应，但状态码不在 `expected-status` 里时，默认存活标记和测速结果仍是成功。

```169:198:core/src/foss/golang/clash/adapter/adapter.go
	defer func() {
		alive := err == nil
		record := C.DelayHistory{Time: time.Now()}
		if alive {
			record.Delay = t
		}

		p.alive.Store(alive)
		p.history.Put(record)
		if p.history.Len() > defaultHistoriesNum {
			p.history.Pop()
		}

		state, _ := p.extra.LoadOrStoreFn(url, func() *internalProxyState {
			return &internalProxyState{
				history: queue.New[C.DelayHistory](defaultHistoriesNum),
				alive:   atomic.NewBool(true),
			}
		})

		if !satisfied {
			record.Delay = 0
			alive = false
		}

		state.alive.Store(alive)
		state.history.Put(record)
		if state.history.Len() > defaultHistoriesNum {
			state.history.Pop()
		}

	}()
```

```276:278:core/src/foss/golang/clash/adapter/adapter.go
	satisfied = resp != nil && (expectedStatus == nil || expectedStatus.Check(uint16(resp.StatusCode)))
	t = uint16(time.Since(start) / time.Millisecond)
	return
```

```246:250:core/src/foss/golang/clash/adapter/outboundgroup/groupbase.go
			delay, err := proxy.URLTest(ctx, url, expectedStatus)
			if err == nil {
				lock.Lock()
				mp[proxy.Name()] = delay
				lock.Unlock()
			}
```

状态码不匹配时 `satisfied` 为 false，但函数仍以 `err == nil` 返回测得延迟。defer 在检查 `satisfied` 之前已经把 `p.alive` 写成 true，并把带延迟的记录放进 `p.history`；之后的修改只作用于按 URL 保存的那份状态。`MarshalJSON` 的 `alive` 和 `history` 因此显示可用。`GroupBase.URLTest` 只看 error，会把这个节点放进成功延迟表。同一 URL 的 `AliveForTestUrl` 读的是 extra，选择结果是死亡，和接口上的存活显示相反。

方向：状态码不符合时先把本次测试判为失败，再写 `alive`、`history` 和返回的 error。

## r09-5

- 编号：r09-5
- 严重度：中
- 症状：load-balance 在未设置 `disable-udp` 时接受 UDP，但选路只看 HTTP 探活，数据包会被交给 `udp: false` 的成员并失败。

```146:160:core/src/foss/golang/clash/adapter/outboundgroup/loadbalance.go
func (lb *LoadBalance) ListenPacketContext(ctx context.Context, metadata *C.Metadata) (pc C.PacketConn, err error) {
	defer func() {
		if err == nil {
			pc.AppendToChains(lb)
		}
	}()

	proxy := lb.Unwrap(metadata, true)
	return proxy.ListenPacketContext(ctx, metadata)
}

// SupportUDP implements C.ProxyAdapter
func (lb *LoadBalance) SupportUDP() bool {
	return !lb.disableUDP
}
```

```183:192:core/src/foss/golang/clash/adapter/outboundgroup/loadbalance.go
		for ; i < length; i++ {
			id := (idx + i) % length
			proxy := proxies[id]
			if proxy.AliveForTestUrl(url) {
				i++
				return proxy
			}
		}

		return proxies[0]
```

`ListenPacketContext` 经 `Unwrap` 进入策略函数。round-robin、consistent-hashing（`loadbalance.go` 204 行）和 sticky-sessions（`loadbalance.go` 237 行）都只调用 `AliveForTestUrl`，`metadata` 只参与哈希，不参与 UDP 能力判断。HTTP 探活成功且 `udp` 关闭的成员仍会被选中；这次监听失败后也不会改选下一个成员。同目录的 url-test、fallback、selector 都会把 `SupportUDP` 交给当前实际成员。

方向：UDP 选路时跳过 `SupportUDP` 为 false 的成员。
