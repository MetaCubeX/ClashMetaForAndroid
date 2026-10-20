---
title: "审查发现 r13 transport-heavy"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "Hysteria 分片重组会丢包或崩溃，重连会把旧连接的 UDP 送进新会话；TUIC 把域名地址当成 IP；读超时会拆掉 Hysteria UDP 会话。"
---

# 审查发现 r13 transport-heavy

本路只读了 `transport/sudoku`、`transport/tuic`、`transport/openvpn`、`transport/hysteria`。下面 5 条都来自这次打开的源码。openvpn 控制/数据信道在已读路径上没有达到同样确定的缺陷。

## r13-1

**严重度**：高

**症状**：同一条 Hysteria 连接上，两个分片 UDP 消息的数据报交错到达时，先到的那条消息被整段丢掉，发送端也不会重传。

**代码**：

```43:48:core/src/foss/golang/clash/transport/hysteria/core/frag.go
	if m.MsgID != d.msgID {
		// new message, clear previous state
		d.msgID = m.MsgID
		d.frags = make([]*udpMessage, m.FragCount)
		d.count = 1
		d.frags[m.FragID] = &m
```

```46:48:core/src/foss/golang/clash/transport/hysteria/core/client.go
	udpSessionMutex sync.RWMutex
	udpSessionMap   map[uint32]chan *udpMessage
	udpDefragger    defragger
```

```137:139:core/src/foss/golang/clash/transport/hysteria/core/client.go
		dfMsg := c.udpDefragger.Feed(udpMsg)
		if dfMsg == nil {
			continue
```

**为什么是问题**：整个 `Client` 只有一个 `defragger`，所有 UDP 会话共用这一格。`Feed` 看到不同的 `MsgID` 就丢掉尚未拼完的上一则。QUIC datagram 不保序，两个需要分片的 UDP 包可以以 A1、B1、A2、B2 的顺序到达；A 和 B 都会停在半包上，`handleMessage` 直接 `continue`。`WriteTo` 只在 `DatagramTooLargeError` 时发一次分片，没有按 `MsgID` 重传。

**方向**：按 `MsgID`（以及会话）保留未完成的分片，不要用后一则覆盖前一则。

## r13-2

**严重度**：中

**症状**：对端发来 `MsgID` 相同但 `FragCount` 变大的分片，或在还没有任何分片记录时发来 `MsgID == 0` 的分片，`Feed` 对切片越界，`handleMessage` 所在进程崩溃。

**代码**：

```35:50:core/src/foss/golang/clash/transport/hysteria/core/frag.go
func (d *defragger) Feed(m udpMessage) *udpMessage {
	if m.FragCount <= 1 {
		return &m
	}
	if m.FragID >= m.FragCount {
		// wtf is this?
		return nil
	}
	if m.MsgID != d.msgID {
		// new message, clear previous state
		d.msgID = m.MsgID
		d.frags = make([]*udpMessage, m.FragCount)
		d.count = 1
		d.frags[m.FragID] = &m
	} else if d.frags[m.FragID] == nil {
```

**为什么是问题**：进入 `else` 之前只比较了 `FragID` 和本包自己的 `FragCount`，没有比较 `len(d.frags)`。`defragger` 的零值是 `msgID == 0` 且 `frags == nil`，第一包若 `MsgID == 0` 会直接执行 `d.frags[m.FragID]`。若第一包已经按较小的 `FragCount` 建了切片，随后同 `MsgID` 的包带着更大的 `FragCount` 和仍小于该计数的 `FragID`，同样越界。这条路径没有 `recover`。

**方向**：索引前确认切片长度，不匹配或为空就丢弃这包或按新的 `FragCount` 重建。

## r13-3

**严重度**：高

**症状**：`OpenStream` 返回非临时错误后，客户端再拨一条 QUIC 连接，旧连接仍在收 UDP，并把数据报投进新会话表；旧的 `udpSessionMap` 赋值也不在 `udpSessionMutex` 里。

**代码**：

```97:100:core/src/foss/golang/clash/transport/hysteria/core/client.go
	// All good
	c.udpSessionMap = make(map[uint32]chan *udpMessage)
	go c.handleMessage(qs)
	c.quicSession = qs
```

```177:184:core/src/foss/golang/clash/transport/hysteria/core/client.go
	// Permanent error, need to reconnect
	if err := c.connectToServer(dialer); err != nil {
		// Still error, oops
		return nil, nil, err
	}
	// We are not going to try again even if it still fails the second time
	stream, err = c.quicSession.OpenStream()
	return c.quicSession, &wrappedQUICStream{stream}, err
```

```141:146:core/src/foss/golang/clash/transport/hysteria/core/client.go
		c.udpSessionMutex.RLock()
		ch, ok := c.udpSessionMap[dfMsg.SessionID]
		if ok {
			select {
			case ch <- dfMsg:
				// OK
			default:
```

**为什么是问题**：重连调用 `connectToServer` 时没有关闭原来的 `c.quicSession`。旧的 `handleMessage` 还堵在 `ReceiveDatagram` 上，查的却是字段 `c.udpSessionMap`。该字段在第 98 行被换成新 map，旧连接上的数据报会按会话号写进新 map。服务端会话号经常从小数重新开始，新 UDP 会话会收到旧连接的载荷。第 98 行的写入没有拿 `udpSessionMutex`，和 `handleMessage`、`DialUDP` 对这个 map 的读写并发。`udpDefragger` 也留在客户端上，不随连接重置。

**方向**：换上新连接之前关掉旧 QUIC 会话并停掉它的 `handleMessage`，在同一把锁里替换会话表，同时清掉分片状态。

## r13-4

**严重度**：中

**症状**：TUIC v4/v5 收到地址类型为域名的 UDP 包时，`ReadFrom` 交给上层的对端地址不是这个域名。

**代码**：

```518:528:core/src/foss/golang/clash/transport/tuic/v4/protocol.go
	case AtypDomainName:
		var addrLen byte
		addrLen, err = reader.ReadByte()
		if err != nil {
			return
		}
		c.ADDR = make([]byte, addrLen+1)
		c.ADDR[0] = addrLen
		_, err = io.ReadFull(reader, c.ADDR[1:])
		if err != nil {
			return
		}
```

```581:586:core/src/foss/golang/clash/transport/tuic/v4/protocol.go
func (c Address) UDPAddr() *net.UDPAddr {
	return &net.UDPAddr{
		IP:   c.ADDR,
		Port: int(c.PORT),
		Zone: "",
	}
}
```

```101:102:core/src/foss/golang/clash/transport/tuic/v4/packet.go
		n = copy(p, packet.DATA)
		addr = packet.ADDR.UDPAddr()
```

```497:508:core/src/foss/golang/clash/transport/tuic/v5/protocol.go
	case AtypDomainName:
		var addrLen byte
		addrLen, err = reader.ReadByte()
		if err != nil {
			return
		}
		c.ADDR = make([]byte, addrLen+1)
		c.ADDR[0] = addrLen
		_, err = io.ReadFull(reader, c.ADDR[1:])
		if err != nil {
			return
		}
```

```566:571:core/src/foss/golang/clash/transport/tuic/v5/protocol.go
func (c Address) UDPAddr() *net.UDPAddr {
	return &net.UDPAddr{
		IP:   c.ADDR,
		Port: int(c.PORT),
		Zone: "",
	}
}
```

```106:108:core/src/foss/golang/clash/transport/tuic/v5/packet.go
			if packetPtr := q.deFragger.Feed(&packet); packetPtr != nil {
				n = copy(p, packetPtr.DATA)
				addr = packetPtr.ADDR.UDPAddr()
```

**为什么是问题**：域名的 `ADDR` 是「长度字节 + 域名」，`String()` 会跳过长度字节。`UDPAddr` 把这整段塞进 `net.UDPAddr.IP`。v4 和 v5 的 `ReadFrom` 都把这个值当成 UDP 对端返回。上层按该地址回包时，目标不是原来的域名。

**方向**：域名类型走主机名，不要把带长度前缀的 `ADDR` 当作 IP。

## r13-5

**严重度**：中

**症状**：对 Hysteria UDP 关联调用 `SetReadDeadline` 后，这条关联被关掉，`ReadFrom` 返回 `ErrClosed`，读超时没有作用在数据报上。

**代码**：

```360:369:core/src/foss/golang/clash/transport/hysteria/core/client.go
func (c *quicPktConn) Hold() {
	// Hold the stream until it's closed
	buf := make([]byte, 1024)
	for {
		_, err := c.Stream.Read(buf)
		if err != nil {
			break
		}
	}
	_ = c.Close()
}
```

```372:376:core/src/foss/golang/clash/transport/hysteria/core/client.go
func (c *quicPktConn) ReadFrom() ([]byte, string, error) {
	msg := <-c.MsgCh
	if msg == nil {
		// Closed
		return nil, "", ErrClosed
```

```417:419:core/src/foss/golang/clash/transport/hysteria/core/client.go
func (c *quicPktConn) Close() error {
	c.CloseFunc()
	return c.Stream.Close()
}
```

```430:432:core/src/foss/golang/clash/transport/hysteria/core/client.go
func (c *quicPktConn) SetReadDeadline(t time.Time) error {
	return c.Stream.SetReadDeadline(t)
}
```

**为什么是问题**：UDP 载荷在 `MsgCh` 上，`ReadFrom` 只等这个 channel。`SetReadDeadline` 改的是用来占住 QUIC 流的 `Hold` 读。截止时间一到，`Stream.Read` 失败，`Hold` 调用 `Close`，`CloseFunc` 关掉 channel 并从会话表删掉该项。一次读超时就把整条 UDP 会话拆掉。

**方向**：让读截止时间只解除当前的 `ReadFrom`，不要让 `Hold` 因这个截止时间关闭会话。
