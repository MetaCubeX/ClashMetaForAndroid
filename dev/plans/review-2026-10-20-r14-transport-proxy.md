---
title: "审查发现 r14 transport-proxy"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "VLESS UDP 短读会把后续数据报错位；SOCKS5 客户端把失败应答当成成功；SSR 两处混淆把一次 Read 当成完整握手；Shadowsocks AEAD 写失败仍计入未发出的字节。"
---

# 审查发现 r14 transport-proxy

本路只读 `transport` 下 ssr、vless、anytls、shadowquic、vmess、shadowsocks、snell、trojan、socks4、socks5。下面 5 条都是这次读到的实现缺陷。

## r14-1

- 严重度：高
- 症状：VLESS UDP 载荷比调用方缓冲区长时，这一包读失败，同一条流上之后的数据报被当成新的长度字段，后续 UDP 内容丢失或错乱。

```25:35:core/src/foss/golang/clash/transport/vless/packet.go
func (c *PacketConn) ReadFrom(b []byte) (int, net.Addr, error) {
	var length uint16
	err := binary.Read(c.Conn, binary.BigEndian, &length)
	if err != nil {
		return 0, nil, err
	}
	if len(b) < int(length) {
		return 0, nil, io.ErrShortBuffer
	}
	n, err := io.ReadFull(c.Conn, b[:length])
	return n, c.rAddr, err
}
```

长度前缀已经从流里取走，缓冲区不够时直接返回，载荷仍留在连接上。下一次 `ReadFrom` 会把载荷字节读成下一个 `uint16` 长度。方向：缓冲区不足时先按长度把这一包读完并丢掉，再返回错误，不要把流停在包的中间。

## r14-2

- 严重度：中
- 症状：上游 SOCKS5 拒绝连接（主机不可达、连接被拒绝等）时，客户端握手仍返回成功，调用方会往一条已经失败的连接上写数据。

```269:274:core/src/foss/golang/clash/transport/socks5/socks5.go
	// VER, REP, RSV
	if _, err := io.ReadFull(rw, buf[:3]); err != nil {
		return nil, err
	}

	return ReadAddr(rw, buf)
```

这里读了 VER、REP、RSV，但没有看 `buf[1]`。RFC 1928 里只有 REP 为 `0x00` 才是成功；非 0 时后面仍会跟着绑定地址，`ReadAddr` 能正常返回，于是整个握手变成 nil error。方向：`ReadAddr` 之前若 REP 不是成功，按该应答码返回错误。

## r14-3

- 严重度：中
- 症状：SSR `http_simple` 的响应头如果没有装进第一次 `Read`，连接被当成对端关闭；已经读到的头字节被丢掉，后面的分段也无法再拼成合法头。

```56:65:core/src/foss/golang/clash/transport/ssr/obfs/http_simple.go
	buf := pool.Get(pool.RelayBufferSize)
	defer pool.Put(buf)
	n, err := c.Conn.Read(buf)
	if err != nil {
		return 0, err
	}
	pos := bytes.Index(buf[:n], []byte("\r\n\r\n"))
	if pos == -1 {
		return 0, io.EOF
	}
```

TCP 不保证一次 `Read` 拿到完整的 `\r\n\r\n`。找不到分隔符时既没有把 `buf[:n]` 留着，也没有继续读，而是返回 EOF。`hasRecvHeader` 仍为 false，已消费的字节无法参与下一次拼接。方向：把已读字节留在连接缓冲里，循环读到空行或真正的连接错误再解析正文。

## r14-4

- 严重度：中
- 症状：SSR `tls1.2_ticket_auth` 在服务端握手没有恰好落在一次 `Read` 里时校验失败；和后续数据粘在同一次读取里时，握手后的应用数据被整段丢掉。

```54:57:core/src/foss/golang/clash/transport/ssr/obfs/tls1.2_ticket_auth.go
	n, err := c.Conn.Read(buf)
	if err != nil {
		return 0, err
	}
```

```77:86:core/src/foss/golang/clash/transport/ssr/obfs/tls1.2_ticket_auth.go
	if n < 11+32+1+32 {
		return 0, errTLS12TicketAuthTooShortData
	}

	if !hmac.Equal(buf[33:43], c.hmacSHA1(buf[11:33])[:10]) || !hmac.Equal(buf[n-10:n], c.hmacSHA1(buf[:n-10])[:10]) {
		return 0, errTLS12TicketAuthHMACError
	}

	c.Write(nil)
	return 0, nil
```

握手还没完成时（`handshakeStatus != 8`），这一次 `Read` 的全部字节被当成一条消息：短于 76 字节就失败，且不保留已读内容；HMAC 覆盖 `buf[:n]`。分段时校验失败，和后续数据粘在同一次读取里时，多出来的字节既参与校验，成功路径上也不进入 `decoded`。方向：按握手消息的长度重组，校验只覆盖握手本身，同一读里多出来的字节留到握手完成后的记录解析。

## r14-5

- 严重度：中
- 症状：Shadowsocks AEAD 写出失败时，返回的字节数包含没有写到连接上的那一块；同一连接上若按返回值续写，nonce 已经前进，后续密文无法被对端解密。

```48:61:core/src/foss/golang/clash/transport/shadowsocks/shadowaead/stream.go
	for nr := 0; n < len(p) && err == nil; n += nr {
		nr = payloadSizeMask
		if n+nr > len(p) {
			nr = len(p) - n
		}
		buf = buf[:off+nr+tag]
		buf[0], buf[1] = byte(nr>>8), byte(nr) // big-endian payload size
		w.Seal(buf[:0], nonce, buf[:2], nil)
		increment(nonce)
		w.Seal(buf[:off], nonce, p[n:n+nr], nil)
		increment(nonce)
		_, err = w.Writer.Write(buf)
	}
	return
```

`n += nr` 在循环后置语句里执行，底层 `Write` 返回错误后仍会加上这一块的明文长度。两次 `increment` 也发生在写出成功之前。调用方看到的 `n` 可以等于 `len(p)`，同时 `err != nil`。方向：只把底层实际写出的明文长度计入 `n`；写出失败时不要再前进这段对应的 nonce。
