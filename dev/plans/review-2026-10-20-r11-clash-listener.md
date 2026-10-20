---
title: "审查发现 r11 clash-listener"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "HTTP/SOCKS 入站鉴权可被绕过，监听失败后端口会停不掉或无法按原配置恢复。"
---

本路只读 `core/src/foss/golang/clash/listener`。确认 5 条。

## r11-1

**严重度：** 高

**症状：** HTTP 代理在同一条 keep-alive 连接上，只要有一次请求鉴权成功，后面不带凭证或凭证错误的请求仍会被转发，包括 CONNECT。

```45:74:core/src/foss/golang/clash/listener/http/proxy.go
	authenticator := store.Authenticator()
	trusted := authenticator == nil // disable authenticate if lru is nil
	lastUser := ""

	for {
		peekMutex.Lock()
		request, err := ReadRequest(conn.Reader())
		peekMutex.Unlock()
		if err != nil {
			break
		}

		request.RemoteAddr = conn.RemoteAddr().String()

		keepAlive := strings.TrimSpace(strings.ToLower(request.Header.Get("Proxy-Connection"))) == "keep-alive"

		resp, user := authenticate(request, authenticator) // always call authenticate function to get user
		if resp == nil {
			trusted = true
		}
		additions[inUserIdx] = inbound.WithInUser(user)

		if trusted {
			if request.Method == http.MethodConnect {
				// Manual writing to support CONNECT for http 1.0 (workaround for uplay client)
				if _, err = fmt.Fprintf(conn, "HTTP/%d.%d %03d %s\r\n\r\n", request.ProtoMajor, request.ProtoMinor, http.StatusOK, "Connection established"); err != nil {
					break // close connection
				}

				tunnel.HandleTCPConn(inbound.NewHTTPS(request, conn, additions...))
```

```131:147:core/src/foss/golang/clash/listener/http/proxy.go
		if !keepAlive {
			resp.Close = true // close connection if keep-alive is not set
		}
		if keepAlive && resp.ContentLength > 0 {
			resp.Close = false // don't need to close connection if content length is positive numbers
		}

		if !resp.Close {
			resp.Header.Set("Proxy-Connection", "keep-alive")
			resp.Header.Set("Connection", "keep-alive")
			resp.Header.Set("Keep-Alive", "timeout=4")
		}

		err = resp.Write(conn)
		if err != nil || resp.Close {
			break // close connection
		}
```

```153:165:core/src/foss/golang/clash/listener/http/proxy.go
func authenticate(request *http.Request, authenticator auth.Authenticator) (resp *http.Response, user string) {
	credential := parseBasicProxyAuthorization(request)
	if credential == "" && authenticator != nil {
		resp = responseWith(request, http.StatusProxyAuthRequired)
		resp.Header.Set("Proxy-Authenticate", "Basic")
		return
	}
	user, pass, err := decodeBasicProxyAuthorization(credential)
	authed := authenticator == nil || (err == nil && authenticator.Verify(user, pass))
	if !authed {
		log.Infoln("Auth failed from %s", request.RemoteAddr)
		return responseWith(request, http.StatusForbidden), user
	}
```

**为什么是问题：** `authenticate` 在缺少凭证时返回 407，在密码错误时返回 403。`trusted` 只在成功时被写成 true，循环里没有写回 false。客户端带了 `Proxy-Connection: keep-alive` 时，失败响应的 `Close` 保持默认 false，连接会留下。下一次请求即使再次得到 407/403，`if trusted` 仍成立，CONNECT 被直接劫持，普通请求则用 `client.Do` 的结果盖掉这份失败响应。一次合法的 HTTP 转发之后，这条连接上的后续请求不再受用户名密码约束。

**方向：** 每个请求都以本次 `authenticate` 的返回值为准，成功结果不要把整条连接标成免鉴权。

## r11-2

**严重度：** 高

**症状：** 给 SOCKS 入站配了用户之后，TCP 会验密码，UDP 仍然接受任意来源的数据包并送进隧道。

```180:194:core/src/foss/golang/clash/listener/socks/tcp.go
func HandleSocks5(conn net.Conn, tunnel C.Tunnel, store auth.AuthStore, additions ...inbound.Addition) {
	authenticator := store.Authenticator()
	target, command, user, err := socks5.ServerHandshake(conn, authenticator)
	if err != nil {
		conn.Close()
		return
	}
	if command == socks5.CmdUDPAssociate {
		defer conn.Close()
		io.Copy(io.Discard, conn)
		return
	}
	additions = append(additions, inbound.WithInUser(user))
	tunnel.HandleTCPConn(inbound.NewSocket(target, conn, C.SOCKS5, additions...))
}
```

```42:80:core/src/foss/golang/clash/listener/socks/udp.go
func NewUDPWithConfig(config LC.AuthServer, lc C.InboundListenConfig, tunnel C.Tunnel, additions ...inbound.Addition) (*UDPListener, error) {
	if len(additions) == 0 {
		additions = []inbound.Addition{
			inbound.WithInName("DEFAULT-SOCKS"),
			inbound.WithSpecialRules(""),
		}
	}
	l, err := lc.ListenPacket(context.Background(), "udp", config.Listen)
	if err != nil {
		return nil, err
	}

	if err := sockopt.UDPReuseaddr(l); err != nil {
		log.Warnln("Failed to Reuse UDP Address: %s", err)
	}

	sl := &UDPListener{
		packetConn: l,
		addr:       config.Listen,
	}
	conn := N.NewEnhancePacketConn(l)
	go func() {
		for {
			data, put, remoteAddr, err := conn.WaitReadFrom()
			if err != nil {
				if put != nil {
					put()
				}
				if sl.closed {
					break
				}
				continue
			}
			handleSocksUDP(l, tunnel, data, put, remoteAddr, additions...)
		}
	}()

	return sl, nil
}
```

```82:98:core/src/foss/golang/clash/listener/socks/udp.go
func handleSocksUDP(pc net.PacketConn, tunnel C.Tunnel, buf []byte, put func(), addr net.Addr, additions ...inbound.Addition) {
	target, payload, err := socks5.DecodeUDPPacket(buf)
	if err != nil {
		// Unresolved UDP packet, return buffer to the pool
		if put != nil {
			put()
		}
		return
	}
	packet := &packet{
		pc:      pc,
		rAddr:   addr,
		payload: payload,
		put:     put,
	}
	tunnel.HandleUDPPacket(inbound.NewPacket(target, packet, C.SOCKS5, additions...))
}
```

**为什么是问题：** TCP 握手把 `AuthStore` 交给 `ServerHandshake`。UDP ASSOCIATE 成功后只把 TCP 连接读到结束，没有记录客户端地址。`NewUDPWithConfig` 拿到的 `config` 里带有 `AuthStore`，读包循环完全不用它。`handleSocksUDP` 只要能解出 SOCKS5 UDP 包就调用 `HandleUDPPacket`。能把 UDP 发到这个套接字的进程不需要密码，也不需要先做 ASSOCIATE。`parse.go` 里 socks/mixed 的 `UDP` 默认是 true，入站用户列表会走到这条路径。

**方向：** UDP 只接收已经完成鉴权的 ASSOCIATE 所登记的来源。

## r11-3

**严重度：** 中

**症状：** 入站热更新失败后，原来正在听的端口被关掉且不会按原配置再启动；新配置里已经 bind 成功的地址继续 Accept，配置层却没有句柄可以关掉它。

```631:644:core/src/foss/golang/clash/listener/listener.go
	for name, newListener := range newListenerMap {
		if oldListener, ok := inboundListeners[name]; ok {
			if !oldListener.Config().Equal(newListener.Config()) {
				_ = oldListener.Close()
			} else {
				continue
			}
		}
		if err := newListener.Listen(tunnel); err != nil {
			log.Errorln("Listener %s listen err: %s", name, err.Error())
			continue
		}
		inboundListeners[name] = newListener
	}
```

```65:75:core/src/foss/golang/clash/listener/inbound/base.go
func (b *Base) RawAddress() string {
	if len(b.ports) == 0 {
		return net.JoinHostPort(b.listenAddr.String(), "0")
	}
	address := make([]string, 0, len(b.ports))
	b.ports.Range(func(port uint16) bool {
		address = append(address, net.JoinHostPort(b.listenAddr.String(), strconv.Itoa(int(port))))
		return true
	})
	return strings.Join(address, ",")
}
```

```80:89:core/src/foss/golang/clash/listener/inbound/mixed.go
		l, err := mixed.NewWithConfig(config, lc, tunnel, m.Additions()...)
		if err != nil {
			return err
		}
		m.l = append(m.l, l)
		if m.udp {
			lUDP, err := socks.NewUDPWithConfig(config, lc, tunnel, m.Additions()...)
			if err != nil {
				return err
			}
```

```122:131:core/src/foss/golang/clash/listener/mixed/mixed.go
	go func() {
		for {
			c, err := ml.listener.Accept()
			if err != nil {
				if ml.closed {
					break
				}
				continue
			}
			store := config.AuthStore
```

**为什么是问题：** 配置不相等时先 `Close` 旧监听，此时表里仍是这个已关闭对象。`Listen` 失败就 `continue`，新对象既不写入表，也不 `Close`。mixed 的 `Listen` 按 `RawAddress` 的逗号分隔地址逐个启动，TCP `NewWithConfig` 返回前已经在 Accept；同一地址的 UDP 失败，或后面的端口失败时，前面的监听留在对象里并继续收连接。下一次如果再应用关闭前的那份配置，`Equal` 为真，直接 `continue`，关闭掉的监听不会再 `Listen`。`inbound/http.go` 和 `inbound/socks.go` 的 `Listen` 也是先 append 再在后续地址失败时返回错误，调用方同样不回收。

**方向：** `Listen` 失败时关掉新对象上已经启动的套接字，并把已关闭的旧对象从表里去掉，让相同配置会重新绑定。

## r11-4

**严重度：** 中

**症状：** Redir 或 TProxy 的 UDP 一旦启动失败，只要 TCP 还停在同一地址，之后的重建会直接返回，UDP 不再尝试。

```213:241:core/src/foss/golang/clash/listener/listener.go
	if redirListener != nil {
		if redirListener.RawAddress() == addr {
			return
		}
		redirListener.Close()
		redirListener = nil
	}

	if redirUDPListener != nil {
		if redirUDPListener.RawAddress() == addr {
			return
		}
		redirUDPListener.Close()
		redirUDPListener = nil
	}

	if portIsZero(addr) {
		return
	}

	redirListener, err = redir.New(addr, tunnel)
	if err != nil {
		return
	}

	redirUDPListener, err = tproxy.NewUDP(addr, tunnel)
	if err != nil {
		log.Warnln("Failed to start Redir UDP Listener: %s", err)
	}
```

```410:438:core/src/foss/golang/clash/listener/listener.go
	if tproxyListener != nil {
		if tproxyListener.RawAddress() == addr {
			return
		}
		tproxyListener.Close()
		tproxyListener = nil
	}

	if tproxyUDPListener != nil {
		if tproxyUDPListener.RawAddress() == addr {
			return
		}
		tproxyUDPListener.Close()
		tproxyUDPListener = nil
	}

	if portIsZero(addr) {
		return
	}

	tproxyListener, err = tproxy.New(addr, tunnel)
	if err != nil {
		return
	}

	tproxyUDPListener, err = tproxy.NewUDP(addr, tunnel)
	if err != nil {
		log.Warnln("Failed to start TProxy UDP Listener: %s", err)
	}
```

**为什么是问题：** UDP 失败时只打日志，`redirUDPListener` / `tproxyUDPListener` 保持 nil，TCP 监听已经写入全局变量。下一次用同一端口调用时，函数在 TCP 地址相等处 `return`，走不到后面的 `NewUDP`。端口被占用、transparent 套接字选项失败这类错误消失之后，重载同一配置也不会把 UDP 拉起来。

**方向：** TCP 和 UDP 各自判断是否已经在听；UDP 当前是 nil 或上次失败时，即使 TCP 地址没变也要重新 `NewUDP`。

## r11-5

**严重度：** 中

**症状：** 多地址监听在中途失败时返回 nil，已经启动的 Accept / UDP 读循环没有 `Close`，调用方也拿不到对象去关。

```143:208:core/src/foss/golang/clash/listener/sing_shadowsocks/server.go
		if config.Udp {
			//UDP
			ul, err := lc.ListenPacket(context.Background(), "udp", addr)
			if err != nil {
				return nil, err
			}

			if err := sockopt.UDPReuseaddr(ul); err != nil {
				log.Warnln("Failed to Reuse UDP Address: %s", err)
			}

			sl.udpListeners = append(sl.udpListeners, ul)

			if kcptunServer != nil {
				go kcptunServer.Serve(ul, func(c net.Conn) {
					sl.HandleConn(c, tunnel)
				})

				continue // skip tcp listener
			}

			go func() {
				conn := bufio.NewPacketConn(ul)
				rwOptions := network.NewReadWaitOptions(conn, sl.service)
				readWaiter, isReadWaiter := bufio.CreatePacketReadWaiter(conn)
				if isReadWaiter {
					readWaiter.InitializeReadWaiter(rwOptions)
				}
				for {
					var (
						buff *buf.Buffer
						dest M.Socksaddr
						err  error
					)
					buff = nil // clear last loop status, avoid repeat release
					if isReadWaiter {
						buff, dest, err = readWaiter.WaitReadPacket()
					} else {
						buff = rwOptions.NewPacketBuffer()
						dest, err = conn.ReadPacket(buff)
						if buff != nil {
							rwOptions.PostReturn(buff)
						}
					}
					if err != nil {
						buff.Release()
						if sl.closed {
							break
						}
						continue
					}
					ctx := context.TODO()
					ctx = sing.WithInAddr(ctx, ul.LocalAddr())
					_ = sl.service.NewPacket(ctx, conn, buff, M.Metadata{
						Protocol: "shadowsocks",
						Source:   dest,
					})
				}
			}()
		}

		//TCP
		l, err := lc.Listen(context.Background(), "tcp", addr)
		if err != nil {
			return nil, err
		}
```

```149:164:core/src/foss/golang/clash/listener/anytls/server.go
		l, err := lc.Listen(context.Background(), "tcp", addr)
		if err != nil {
			return nil, err
		}
		if shadowTLSBuilder != nil {
			l = shadowTLSBuilder.NewListener(l)
		} else if restlsBuilder != nil {
			l = restlsBuilder.NewListener(l)
		} else if jlsBuilder != nil {
			l = jlsBuilder.NewListener(l)
		} else if tlsConfig.GetCertificate != nil {
			l = tls.NewListener(l, tlsConfig)
		} else if !config.AllowInsecure {
			return nil, errors.New("disallow using AnyTLS without certificates/shadow-tls/res-tls/jls/allow-insecure config")
		}
		sl.listeners = append(sl.listeners, l)
```

```44:64:core/src/foss/golang/clash/listener/tproxy/udp.go
	l, err := net.ListenPacket("udp", addr)
	if err != nil {
		return nil, err
	}

	rl := &UDPListener{
		packetConn: l,
		addr:       addr,
	}

	c := l.(*net.UDPConn)

	rc, err := c.SyscallConn()
	if err != nil {
		return nil, err
	}

	err = setsockopt(rc, addr)
	if err != nil {
		return nil, err
	}
```

**为什么是问题：** Shadowsocks 在 UDP 读循环开始之后，如果同一地址的 TCP `Listen` 失败，函数 `return nil, err`。读循环持有 `sl` 和 `ul`，没有任何 `defer` 去 `Close`。AnyTLS 在前面的地址已经 `append` 并开始 Accept 之后，后面的地址 `Listen` 失败同样返回 nil；无证书且未开 `allow-insecure` 时，刚刚 `Listen` 成功的套接字在 `append` 之前就返回，同样没有 `Close`。TProxy UDP 在 `ListenPacket` 成功后，`SyscallConn` 或 `setsockopt` 失败也直接返回 nil，`packetConn` 没有关闭。调用方只看到错误，关不掉这些已经绑定的端口，随后的重建会撞上地址仍被占用。`trojan/server.go` 在 `Listen` 成功后，若没有证书、reality、shadowtls、restls、jls 或 ss，且未开 allow-insecure，会在 `append` 之前返回错误，这个套接字同样没有 `Close`。

**方向：** 构造函数在返回错误前关掉本次已经 bind 的监听，并把已启动的部分放进可被 `Close` 的对象交给调用方。
