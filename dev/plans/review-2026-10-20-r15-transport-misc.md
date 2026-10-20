---
title: "审查发现 r15 transport-misc"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "packet-up 关闭会丢掉已接收的上行；mekya 满队列和 gost 大 UDP 包会静默丢数据；带 seed 的 mKCP 包超出 MTU；simple-obfs TLS 读失败会空转。"
---

# 审查发现 r15 transport-misc

本路只读 `transport` 下 xhttp、tlsmirror、mkcp、trusttunnel、mekya、shadowtls、kcptun、simple-obfs、masque、jls、gun、v2ray-plugin、gost、restls。下面 5 条都来自这次打开的源码。

## r15-1

- 严重度：高
- 症状：xhttp packet-up 的 `Write` 已经返回成功，连接关闭时最后一段上行仍可能没有发出，`Close` 也返回成功。

```65:80:core/src/foss/golang/clash/transport/xhttp/client.go
	data := bytes.NewBuffer(b)
	for data.Len() > 0 {
		if c.timer == nil { // start a timer to flush the buffer
			c.timer = time.AfterFunc(time.Duration(c.scMinPostsIntervalMs.Rand())*time.Millisecond, c.flush)
		}

		c.buf = append(c.buf, data.Next(c.scMaxEachPostBytes-len(c.buf))...) // let buffer fill up to scMaxEachPostBytes

		if len(c.buf) >= c.scMaxEachPostBytes { // too much data in buffer, wait the flush complete
			c.writeCond.Wait()
			if err := c.flushErr; err != nil {
				return 0, err
			}
		}
	}
	return len(b), nil
}
```

```98:106:core/src/foss/golang/clash/transport/xhttp/client.go
	if len(c.buf) == 0 {
		return
	}
	_, err := c.write(c.buf)
	c.buf = c.buf[:0] // reset buffer
	if err != nil {
		c.flushErr = err
		return
	}
```

```143:155:core/src/foss/golang/clash/transport/xhttp/client.go
func (c *PacketUpWriter) Close() error {
	ch := make(chan struct{})
	go func() { // flush in the background
		defer close(ch)
		c.flush()
	}()
	select {
	case <-ch:
	case <-time.After(time.Second):
	}
	c.cancel()
	httputils.CloseTransport(c.transport)
	return nil
}
```

`Write` 把字节放进 `c.buf` 后就按全部写完返回。真正的 POST 在 `flush`。POST 失败时缓冲区先被清掉，错误只留在 `flushErr`。`Close` 最多等一秒，到点就 `cancel` 掉请求上下文，并且无论 POST 失败还是被取消，都返回 `nil`。调用方已经看到写入成功、关闭成功，这段上行不在缓冲区里，也不会重发。

方向：关闭时要等这次 flush 结束，把 `flushErr` 返回出去；POST 失败的字节不要先丢掉。

## r15-2

- 严重度：高
- 症状：mekya 服务端会话在发送队列满时丢掉这个包，同时告诉调用方全部写成功。

```281:290:core/src/foss/golang/clash/transport/mekya/server.go
func (s *serverSession) Write(p []byte) (int, error) {
	packet := append([]byte(nil), p...)
	select {
	case <-s.ctx.Done():
		return 0, s.ctx.Err()
	case s.writerChan <- packet:
		return len(p), nil
	default:
		return len(p), nil
	}
}
```

`writerChan` 的容量是 `PacketWritingBuffer`。通道接不下时走 `default`，复制出来的包没有任何去处。返回值仍是 `len(p), nil`。上层 mKCP 把这次 UDP 写出当成已经离开本机，不会因为这次调用失败而立刻重试。队列持续满时，之后的重传包同样在这里被丢掉。

方向：队列满时阻塞到上下文结束，或者返回错误，不要报告这次写入成功。

## r15-3

- 严重度：高
- 症状：gost relay 的 UDP 包比读取缓冲区大时，多出来的字节被丢掉，`ReadFrom` 仍返回成功。

```299:309:core/src/foss/golang/clash/transport/gost/relay.go
	packetLen := int(binary.BigEndian.Uint16(header[:]))
	if packetLen <= len(p) {
		n, err := io.ReadFull(c.conn, p[:packetLen])
		return n, c.raddr, err
	}

	buf := make([]byte, packetLen)
	if _, err := io.ReadFull(c.conn, buf); err != nil {
		return 0, nil, err
	}
	return copy(p, buf), c.raddr, nil
```

长度字段允许到 65535。缓冲区更短时，函数先把整包从流里读完，再只拷贝 `len(p)` 字节，返回值没有错误。调用方拿到的是被截短的数据报，并会把它当作完整 UDP 包继续转发。流本身已经消费了全长，所以后续包的边界还在，丢的是这一包的尾部。

方向：缓冲区放不下整包时返回错误，不要把截短的前缀当作成功的完整数据报。

## r15-4

- 严重度：中
- 症状：mKCP 配置了 `Seed` 时，每个加密 UDP 包都比配置的 MTU 多出 GCM nonce 的长度。

```87:96:core/src/foss/golang/clash/transport/mkcp/config.go
func (c Config) security() (cipher.AEAD, error) {
	if c.Seed == "" {
		return newSimpleAuthenticator(), nil
	}
	hashedSeed := sha256.Sum256([]byte(c.Seed))
	block, err := aes.NewCipher(hashedSeed[:16])
	if err != nil {
		return nil, err
	}
	return cipher.NewGCM(block)
}
```

```52:63:core/src/foss/golang/clash/transport/mkcp/packet.go
func (w packetWriter) overhead() int {
	if w.security == nil {
		if w.header == nil {
			return 0
		}
		return w.header.size()
	}
	overhead := w.security.Overhead()
	if w.header != nil {
		overhead += w.header.size()
	}
	return overhead
}
```

```119:119:core/src/foss/golang/clash/transport/mkcp/conn.go
		mss:           int(cfg.mtu()) - writer.overhead() - dataSegmentOverhead,
```

```80:87:core/src/foss/golang/clash/transport/mkcp/packet.go
	if w.security != nil {
		nonceSize := w.security.NonceSize()
		out = out[:headerSize+nonceSize]
		if nonceSize > 0 {
			_, _ = rand.Read(out[headerSize : headerSize+nonceSize])
		}
		sealed := w.security.Seal(out[headerSize+nonceSize:headerSize+nonceSize], out[headerSize:headerSize+nonceSize], payload, nil)
		out = append(out[:headerSize+nonceSize], sealed...)
```

`Seed` 非空时用的是 AES-GCM。MSS 只用了 `Overhead()`（认证标签）和可选包头，写出时却另外放了 `NonceSize()` 字节。标准 GCM 的 nonce 是 12 字节，所以线上包长是 `mtu() + 12`。路径按配置的 MTU 丢弃或无法重组这个 UDP 包时，带 seed 的会话发不出去。

方向：计算 MSS 时把 `NonceSize()` 算进开销。

## r15-5

- 严重度：中
- 症状：simple-obfs 的 TLS 客户端和伪装服务端在读记录长度失败时返回 `(0, nil)`，读循环停不下来。

```35:39:core/src/foss/golang/clash/transport/simple-obfs/tls.go
	sizeBuf := make([]byte, 2)
	_, err = io.ReadFull(to.Conn, sizeBuf)
	if err != nil {
		return 0, nil
	}
```

```34:38:core/src/foss/golang/clash/transport/simple-obfs/tls_server.go
	sizeBuf := make([]byte, 2)
	_, err = io.ReadFull(tos.Conn, sizeBuf)
	if err != nil {
		return 0, nil
	}
```

两边的后续数据帧都先丢弃固定头，再读 2 字节长度。对端关闭、读超时或底层出错时，错误被换成 `nil`。`Read` 返回 0 且没有错误表示这次没有进展，`io.Copy` 会立刻再读。连接已经坏了，之后每次长度读取都失败，拷贝循环不会结束。

方向：把 `ReadFull` 的错误原样返回。
