---
title: "审查发现 r16 clash-control"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条；最严重的是 CMFA 系统 DNS 列表为空时直连 114.114.114.114 与 8.8.8.8，以及测速接口清掉策略组固定选择。"
---

# 审查发现 r16 clash-control

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖上面列出的 clash 控制面路径。下面 5 条都来自这次读到的源码。

## r16-1
- 严重度：高
- 症状：`android && cmfa` 下，系统 DNS 列表还没写入或被清空时，走 `SystemResolver` 的查询会直连 `114.114.114.114:53` 和 `8.8.8.8:53`，不经过代理。
- 代码：
```32:42:core/src/foss/golang/clash/dns/system.go
func (c *systemClient) ExchangeContext(ctx context.Context, m *D.Msg) (msg *D.Msg, err error) {
	dnsClients, err := c.getDnsClients()
	if len(dnsClients) == 0 && len(c.defaultNS) > 0 {
		dnsClients = c.defaultNS
		err = nil
	}
	if err != nil {
		return
	}
	msg, _, err = batchExchange(ctx, dnsClients, m)
	return
}
```
```68:74:core/src/foss/golang/clash/dns/system.go
func init() {
	r := NewResolver(Config{})
	c := newSystemClient()
	c.defaultNS = transform([]NameServer{{Addr: "114.114.114.114:53"}, {Addr: "8.8.8.8:53"}}, nil)
	r.main = []dnsClient{c}
	resolver.SystemResolver = r
}
```
```16:31:core/src/foss/golang/clash/dns/patch_android.go
func UpdateSystemDNS(addr []string) {
	if len(addr) == 0 {
		systemResolver = nil
	}

	ns := make([]NameServer, 0, len(addr))
	for _, d := range addr {
		ns = append(ns, NameServer{Addr: d})
	}

	systemResolver = transform(ns, nil)
}

func (c *systemClient) getDnsClients() ([]dnsClient, error) {
	return systemResolver, nil
}
```
- 为什么是问题：`systemResolver` 初始是 nil，`getDnsClients` 返回空列表和 nil 错误。`SystemResolver` 上的客户端带有 `defaultNS`，于是空列表被换成这两个公共 DNS，`transform` 的 resolver 参数是 nil，查询直连出去。`UpdateSystemDNS` 在空切片时先赋 nil，随后仍执行 `transform`，空列表照样触发上述回退，清空系统 DNS 停不掉这条路径。
- 方向：系统 DNS 列表为空时应失败返回，不要回退到硬编码公共解析器。

## r16-2
- 严重度：高
- 症状：对非 Selector 的可选策略组请求延迟测试时，固定节点和 cache 里的选择会被清掉；`timeout` 非法导致 400 时，选择也已经丢了。
- 代码：
```53:74:core/src/foss/golang/clash/hub/route/groups.go
func getGroupDelay(w http.ResponseWriter, r *http.Request) {
	proxy := r.Context().Value(CtxKeyProxy).(C.Proxy)
	group, ok := proxy.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		render.Status(r, http.StatusNotFound)
		render.JSON(w, r, ErrNotFound)
		return
	}

	if selectAble, ok := proxy.Adapter().(outboundgroup.SelectAble); ok && proxy.Type() != C.Selector {
		selectAble.ForceSet("")
		cachefile.Cache().SetSelected(proxy.Name(), "")
	}

	query := r.URL.Query()
	url := query.Get("url")
	timeout, err := strconv.ParseInt(query.Get("timeout"), 10, 32)
	if err != nil {
		render.Status(r, http.StatusBadRequest)
		render.JSON(w, r, ErrBadRequest)
		return
	}
```
- 为什么是问题：`ForceSet("")` 取消当前固定出口，`SetSelected(name, "")` 把空选择写入 cache。这两步发生在 `timeout` 解析之前。请求因参数错误返回 400，或测速失败，都不会把原来的节点写回去。重启后 `patchSelectGroup` 读到的是被覆盖后的记录，用户钉住的节点回不来。
- 方向：测速不要在校验通过并真正开测之前改固定选择；需要临时放开时，结束后恢复原选择。

## r16-3
- 严重度：中
- 症状：hosts 把域名指到另一个域名时，DNS 应答里的地址不是 hosts 里最终的 IP，客户端按应答去连会绕开 hosts。
- 代码：
```39:53:core/src/foss/golang/clash/dns/middleware.go
			record, ok := resolver.DefaultHosts.Search(host, q.Qtype != D.TypeA && q.Qtype != D.TypeAAAA)
			if !ok {
				if record != nil && record.IsDomain {
					// replace request domain
					newR := r.Copy()
					newR.Question[0].Name = record.Domain + "."
					resp, err := next(ctx, newR)
					if err == nil {
						resp.Id = r.Id
						resp.Question = r.Question
						handleCName(resp, record.Domain)
					}
					return resp, err
				}
				return next(ctx, r)
			}
```
```233:248:core/src/foss/golang/clash/dns/middleware.go
func newHandler(resolver resolver.Resolver, mapper *ResolverEnhancer) handler {
	var middlewares []middleware

	if mapper.useHosts {
		middlewares = append(middlewares, withHosts(mapper.mapping))
	}

	if mapper.mode == C.DNSFakeIP {
		middlewares = append(middlewares, withFakeIP(mapper.fakeIPSkipper, mapper.fakeIPPool, mapper.fakeIPPool6, mapper.fakeIPTTL))
	}

	if mapper.mode != C.DNSNormal {
		middlewares = append(middlewares, withMapping(mapper.mapping))
	}

	return compose(middlewares, withResolver(resolver, mapper.ipv6))
}
```
```1168:1176:core/src/foss/golang/clash/config/config.go
			if value.IsDomain {
				node := tree.Search(value.Domain)
				for node != nil && node.Data().IsDomain {
					if node.Data().Domain == domain {
						return nil, fmt.Errorf("%s, there is a cycle in domain name mapping", domain)
					}
					node = tree.Search(node.Data().Domain)
				}
			}
```
- 为什么是问题：配置允许域名到域名的 hosts，并沿着别名查环。DNS 中间件遇到别名后改写问题名，交给后面的 fake-ip 或上游解析，不会再进 `withHosts`。别名目标若在 hosts 里有 IP，应答里也不会用那条记录。连接路径会在 `preHandleMetadata` 换成别名后再 `Search` 一次，拨号用的是 hosts IP。redir-host 下客户端采用应答里的地址，就会连到上游地址而不是 hosts。
- 方向：别名改写后的查询要重新经过 hosts，直到落到 IP 或确认 hosts 里没有下一条。

## r16-4
- 严重度：中
- 症状：同一本地 UDP 源下，多个目标解析到同一个真实 IP 时，回包被写回第一个目标的地址，其它目标收不到对应回包。
- 代码：
```42:62:core/src/foss/golang/clash/tunnel/connection.go
func (s *packetSender) AddMapping(originMetadata *C.Metadata, metadata *C.Metadata) {
	s.mappingMutex.Lock()
	defer s.mappingMutex.Unlock()
	originKey := originMetadata.String()
	originAddr := originMetadata.DstIP
	targetAddr := metadata.DstIP
	if addr := s.originToTarget[originKey]; !addr.IsValid() { // overwrite only if the record is illegal
		s.originToTarget[originKey] = targetAddr
	}
	if addr := s.targetToOrigin[targetAddr]; !addr.IsValid() { // overwrite only if the record is illegal
		s.targetToOrigin[targetAddr] = originAddr
	}
}

func (s *packetSender) RestoreReadFrom(addr netip.Addr) netip.Addr {
	s.mappingMutex.RLock()
	defer s.mappingMutex.RUnlock()
	if originAddr := s.targetToOrigin[addr]; originAddr.IsValid() {
		return originAddr
	}
	return addr
}
```
```192:200:core/src/foss/golang/clash/tunnel/connection.go
		fromAddrPort := fromUDPAddr.AddrPort()
		fromAddr := fromAddrPort.Addr().Unmap()

		// restore DestinationNAT
		fromAddr = sender.RestoreReadFrom(fromAddr).Unmap()

		fromAddrPort = netip.AddrPortFrom(fromAddr, fromAddrPort.Port())

		_, err = writeBack.WriteBack(data, net.UDPAddrFromAddrPort(fromAddrPort))
```
```303:312:core/src/foss/golang/clash/constant/adapters.go
func (s *packetAdapter) Key() string {
	return s.key
}

func NewPacketAdapter(packet UDPPacket, metadata *Metadata) PacketAdapter {
	return &packetAdapter{
		packet,
		metadata,
		packet.LocalAddr().String(),
	}
}
```
- 为什么是问题：NAT 键只有本地地址，同一源套接字的所有目标共用一个 `packetSender`。反向表只按解析后的真实 IP 记录原始目标，已有记录就不覆盖。fake-ip 下原始目标是各域名自己的 fake-ip，两个域名解析到同一真实 IP 时，回包全部恢复成第一个 fake-ip 再 `WriteBack`。后一个目标的 UDP 回包被标成错误来源。
- 方向：反向映射要能区分同一真实 IP 上的不同原始目标，不能只留第一条。

## r16-5
- 严重度：中
- 症状：nameserver 列表里 `rcode://` 不是第一个时，它会改掉正在被其它上游发送的同一条 DNS 报文，前面的查询和返回给客户端的结果都会错。
- 代码：
```42:46:core/src/foss/golang/clash/dns/rcode.go
func (r rcodeClient) ExchangeContext(ctx context.Context, m *D.Msg) (*D.Msg, error) {
	m.Response = true
	m.Rcode = r.rcode
	return m, nil
}
```
```421:429:core/src/foss/golang/clash/dns/util.go
	for _, client := range clients {
		if _, isRCodeClient := client.(rcodeClient); isRCodeClient {
			msg, err = client.ExchangeContext(ctx, m)
			return msg, false, err
		}
		client := client // shadow define client to ensure the value captured by the closure will not be changed in the next loop
		fast.Go(func() (*D.Msg, error) {
			log.Debugln("[DNS] resolve %s %s from %s", domain, qTypeStr, client.Address())
			m, err := client.ExchangeContext(ctx, m)
```
- 为什么是问题：`batchExchange` 把同一个 `*D.Msg` 交给每个上游。循环遇到 `rcode` 之前，前面的客户端已经在 goroutine 里发送这份报文。`rcode` 直接把 `Response` 和 `Rcode` 写进这份报文并立刻返回，飞行中的查询会被改成响应，调用方拿到的也是被改过的原请求，而不是一份独立应答。
- 方向：`rcode` 返回副本；多个上游不要并发读写同一条 `dns.Msg`。
