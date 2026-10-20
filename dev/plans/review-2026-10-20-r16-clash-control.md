---
title: "审查发现 r16 clash-control"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "Unix/管道外部控制与 debug 路由绕过 secret；LookupIP 会把空地址当成功，UDP fake-ip 缺失直接丢包，CMFA 下 UID 规则不命中。"
---

# 审查发现 r16 clash-control

审查范围是内核控制面活码：规则匹配、DNS 解析、配置加载、hub 路由鉴权、tunnel 连接选择。下面 5 条都来自这次读到的源码。

## r16-1

- 严重度：高
- 症状：配了 `secret` 之后，Unix 套接字和 Windows 命名管道上的外部控制接口仍然不校验口令；Unix 套接字文件还是 `0666`。能访问该路径的本机用户可以改配置、关连接、读日志。

```286:291:core/src/foss/golang/clash/hub/route/server.go
		_ = os.Chmod(addr, 0o666)
		log.Infoln("RESTful API unix listening at: %s", l.Addr().String())

		server := &http.Server{
			Handler: router(cfg.IsDebug, "", cfg.DohServer, cfg.Cors),
		}
```

```320:322:core/src/foss/golang/clash/hub/route/server.go
		server := &http.Server{
			Handler: router(cfg.IsDebug, "", cfg.DohServer, cfg.Cors),
		}
```

`router` 只在 `secret != ""` 时挂 `authentication`。这里第二个参数被写成空字符串，TCP 控制器上的 `cfg.Secret` 不会传到这两条监听。`0666` 让同机其他用户在目录可进入时直接连上套接字。

方向：这两条本地监听带上 `cfg.Secret`，Unix 套接字权限收到当前用户可读写。

## r16-2

- 严重度：高
- 症状：日志级别为 debug 时，`/debug/pprof` 和 `PUT /debug/gc` 不经过 secret。控制器可达时，未带口令的请求可以读进程画像、触发释放内存。

```77:77:core/src/foss/golang/clash/hub/hub.go
		IsDebug:        cfg.General.LogLevel == log.DEBUG,
```

```108:122:core/src/foss/golang/clash/hub/route/server.go
	if isDebug {
		r.Mount("/debug", func() http.Handler {
			r := chi.NewRouter()
			r.Put("/gc", func(w http.ResponseWriter, r *http.Request) {
				debug.FreeOSMemory()
			})
			handler := middleware.Profiler
			r.Mount("/", handler())
			return r
		}())
	}
	r.Group(func(r chi.Router) {
		if secret != "" {
			r.Use(authentication(secret))
		}
```

`/debug` 挂在鉴权分组前面。`log-level: debug` 且外部控制器带 secret 时，口令盖不住这组路由。pprof 能看到内存里的配置和连接。

方向：把 `/debug` 放进已有的 `authentication` 分组。

## r16-3

- 严重度：中
- 症状：域名只有 AAAA、或 A 先返回空/失败时，IPv6 答复只要晚于等待窗口，`LookupIP` 就返回空地址且错误为 nil。调用方会把「没解析出来」当成成功。

```89:107:core/src/foss/golang/clash/dns/resolver.go
	ips, err = r.lookupIP(ctx, host, D.TypeA)
	var waitIPv6 *time.Timer
	if r != nil && r.ipv6Timeout > 0 {
		waitIPv6 = time.NewTimer(r.ipv6Timeout)
	} else {
		waitIPv6 = time.NewTimer(100 * time.Millisecond)
	}
	defer waitIPv6.Stop()
	select {
	case ipv6s, open := <-ch:
		if !open && err != nil {
			return nil, resolver.ErrIPNotFound
		}
		ips = append(ips, ipv6s...)
	case <-waitIPv6.C:
		// wait ipv6 result
	}

	return ips, nil
```

```510:510:core/src/foss/golang/clash/config/config.go
			IPv6Timeout:    100,
```

A 查询失败时 `ips` 为空、`err != nil`。通道先关闭才会返回 `ErrIPNotFound`。计时器先到则走到最后的 `return ips, nil`，A 的错误被丢掉，随后到达的 AAAA 也不再交给调用方。默认 `ipv6-timeout` 是 100 毫秒，DoH/高延迟下 AAAA 经常更慢。

方向：A 失败时继续等 AAAA；窗口结束仍然没有地址时返回 `ErrIPNotFound`。

## r16-4

- 严重度：中
- 症状：fake-ip 映射已经不在时，UDP 包在嗅探之前被丢掉。同样开启嗅探时，TCP 还能从载荷里找回域名并继续拨号。

```306:307:core/src/foss/golang/clash/tunnel/tunnel.go
		} else if resolver.IsFakeIP(metadata.DstIP) {
			return fmt.Errorf("fake DNS record %s missing", metadata.DstIP)
```

```434:444:core/src/foss/golang/clash/tunnel/tunnel.go
	if err := preHandleMetadata(metadata.Clone()); err != nil { // precheck without modify metadata
		packet.Drop()
		log.Debugln("[Metadata PreHandle] error: %s", err)
		return
	}

	key := packet.Key()
	sender, loaded := natTable.GetOrCreate(key, func() C.PacketSender {
		sender := newPacketSender()
		if sniffingEnable && snifferDispatcher.Enable() {
			return snifferDispatcher.UDPSniff(packet, sender)
```

```527:532:core/src/foss/golang/clash/tunnel/tunnel.go
	if sniffingEnable && snifferDispatcher.Enable() {
		// Try to sniff a domain when `preHandleMetadata` failed, this is usually
		// caused by a "Fake DNS record missing" error when enhanced-mode is fake-ip.
		if snifferDispatcher.TCPSniff(conn, metadata) {
			// we now have a domain name
			preHandleFailed = false
```

UDP 的 `UDPSniff` 包在预检成功之后才装上。预检克隆失败就 `Drop` 并返回，QUIC 这类还能从首包看出域名的 UDP 流会一直失败。TCP 注释写明这条错误通常就是 fake-ip 记录缺失，并且嗅探成功会把失败清掉。

方向：嗅探开启时，UDP 先 `DoSniff`，再对更新后的元数据做一次 `preHandleMetadata`。

## r16-5

- 严重度：中
- 症状：CMFA 构建下 UID 规则全部不命中，并且每条连接打一条「could not get uid」。非 CMFA 上 uid 0（root）同样永远匹配不上。

```354:375:core/src/foss/golang/clash/tunnel/tunnel.go
				if !features.CMFA {
					// normal check for process
					uid, path, err := process.FindProcessName(metadata.NetWork.String(), metadata.SrcIP, int(metadata.SrcPort))
					if err != nil {
						log.Debugln("[Process] find process error for %s: %v", metadata.String(), err)
					} else {
						metadata.Process = filepath.Base(path)
						metadata.ProcessPath = path
						metadata.Uid = uid

						if pkg, err := process.FindPackageName(metadata); err == nil { // for android (not CMFA) package names
							metadata.Process = pkg
						}
					}
				} else {
					// check package names
					pkg, err := process.FindPackageName(metadata)
					if err != nil {
						log.Debugln("[Process] find process error for %s: %v", metadata.String(), err)
					} else {
						metadata.Process = pkg
					}
				}
```

```44:54:core/src/foss/golang/clash/rules/common/uid.go
func (u *Uid) Match(metadata *C.Metadata, helper C.RuleMatchHelper) (bool, string) {
	if helper.FindProcess != nil {
		helper.FindProcess()
	}
	if metadata.Uid != 0 {
		if u.uids.Check(metadata.Uid) {
			return true, u.adapter
		}
	}
	log.Warnln("[UID] could not get uid from %s", metadata.String())
	return false, ""
}
```

CMFA 分支只写包名，不写 `metadata.Uid`，字段保持 0。`Match` 把 0 排除在比较之外，于是 `UID` 规则在本仓库的 CMFA 构建上不会选中任何连接。uid 已经取到但只是不在范围内时，同样打「could not get uid」。

方向：CMFA 的进程查找写入真实 uid；`Match` 在查找成功后用范围比较，0 是合法 uid，未命中不要报取不到。
