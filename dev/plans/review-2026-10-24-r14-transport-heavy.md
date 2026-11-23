---
title: "审查发现 r14 transport-heavy"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条；最严重的是 TUIC v4 在转发返回后才写 Connect 响应，非 FastOpen 的 TCP 会卡住。"
---

# 审查发现 r14 transport-heavy

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖 transport 下 sudoku、tuic、openvpn、hysteria。下面 5 条都来自这次读到的源码。

## r14-1
- 严重度：高
- 症状：TUIC v4 不开 FastOpen 时，TCP 连接停在读响应；处理函数若先往同一条流写了目标数据，客户端会把那些字节当成 Connect 响应。
- 代码：
```118:129:core/src/foss/golang/clash/transport/tuic/v4/server.go
	err = s.HandleTcpFn(conn, connect.ADDR.SocksAddr())
	if err != nil {
		err = NewResponseFailed().WriteTo(buf)
		defer conn.Close()
	} else {
		err = NewResponseSucceed().WriteTo(buf)
	}
	if err != nil {
		_ = conn.Close()
		return err
	}
	_, err = buf.WriteTo(conn)
```
- 为什么是问题：响应写在 `HandleTcpFn` 返回之后，和这条 QUIC 流上的应用数据共用字节序。客户端在 `FastOpen` 为 false 时，`DialContext` 先执行 `response()` 里的 `ReadResponse`（`v4/client.go` 311-333 行），再把连接交给调用方。处理函数拿到的就是这条流，读客户端后续字节时客户端还停在等 REP；处理函数若已经把目标返回写进流里，`ReadResponse` 读到的前 3 字节（版本、类型、REP）就是目标数据，真正的成功或失败响应被接在流尾。
- 方向：在把流交给转发之前写完 Connect 响应。

## r14-2
- 严重度：中
- 症状：TUIC v5 native UDP 在贴近 datagram 上限时发送失败，分片重试也失败，大包发不出去。
- 代码：
```326:330:core/src/foss/golang/clash/transport/tuic/v5/protocol.go
func (c Packet) BytesLen() int {
	return c.CommandHead.BytesLen() + 4 + 2 + c.ADDR.BytesLen() + len(c.DATA)
}

var PacketOverHead = NewPacket(0, 0, 0, 0, 0, NewAddressAddrPort(netip.AddrPortFrom(netip.IPv6Unspecified(), 0)), nil).BytesLen()
```
- 为什么是问题：`WriteTo`（303-311 行）在命令头之后写出 `ASSOC_ID`、`PKT_ID`、`FRAG_TOTAL`、`FRAG_ID`、`SIZE`，共 8 字节。`BytesLen` 只加了 4+2。`PacketOverHead` 因此少 2 字节。`MaxFragSize`（`v5/frag.go` 17 行）和超限后的重试（`v5/packet.go` 192 行，`MaxDatagramPayloadSize - PacketOverHead`）都用这个值切 payload，编出来的 datagram 仍比上限大 2 字节，重试不能成功。
- 方向：按 `WriteTo` 的实际字段宽度计算包头长度。

## r14-3
- 严重度：中
- 症状：同一 UDP 关联上两个分片包撞上相同 `PKT_ID` 且片数相同，重组结果混进另一包的字节，或整片被丢掉。
- 代码：
```84:97:core/src/foss/golang/clash/transport/tuic/v5/frag.go
	bag, _ := d.lru.GetOrStore(m.PKT_ID, newPacketBag)
	bag.mutex.Lock()
	defer bag.mutex.Unlock()
	if int(m.FRAG_TOTAL) != len(bag.frags) {
		// new message, clear previous state
		bag.frags = make([]*Packet, m.FRAG_TOTAL)
		bag.count = 1
		bag.frags[m.FRAG_ID] = m
		return nil
	}
	if bag.frags[m.FRAG_ID] != nil {
		return nil
	}
```
- 为什么是问题：缓存键只有 `PKT_ID`。发送端用 `uint16(randv2.Uint32())` 取这个值（`v5/packet.go` 160 行），没有避开仍在重组的编号。片数相同就不会清空旧袋；新包里槽位已被占用的那一片直接丢弃，空槽则填进去。凑满 `count` 后按槽位拼接，交付的 datagram 来自两个包。
- 方向：重组键带上关联编号，槽位已被占用时另起一包而不是并进旧袋。

## r14-4
- 严重度：中
- 症状：Hysteria Brutal 在高速率下空闲一段时间后不再发包。
- 代码：
```43:49:core/src/foss/golang/clash/transport/hysteria/congestion/pacer.go
func (p *pacer) Budget(now monotime.Time) congestion.ByteCount {
	if p.lastSentTime.IsZero() {
		return p.maxBurstSize()
	}
	budget := p.budgetAtLastSent + (p.getBandwidth()*congestion.ByteCount(now.Sub(p.lastSentTime).Nanoseconds()))/1e9
	return minByteCount(p.maxBurstSize(), budget)
}
```
- 为什么是问题：带宽（字节/秒）与空闲纳秒先相乘再除 `1e9`。`ByteCount` 是有符号整数，乘积溢出后变成负数。`BrutalSender.HasPacingBudget`（`hysteria/congestion/brutal.go` 56-57 行）要求预算不小于一个 datagram，负数永远不满足。`lastSentTime` 只在 `SentPacket` 里更新，发不出包就不会刷新，预算一直保持溢出后的负值。约 100 Mbps 时空闲十来分钟就会跨过这个乘积。`tuic/congestion/pacer.go` 55 行是同一公式；`tuic/congestion_v2/pacer.go` 47-48 行在预算为负时钳到 `1<<62-1`。
- 方向：先按时间换算再乘带宽，乘积为负时钳到突发上限。

## r14-5
- 严重度：中
- 症状：TUIC v5 native 的 `MaxUdpRelayPacketSize` 为 0 时，第一包非空 UDP 回包把进程打崩。
- 代码：
```19:24:core/src/foss/golang/clash/transport/tuic/v5/frag.go
func fragWriteNative(quicConn *quic.Conn, packet Packet, buf *bytes.Buffer, fragSize int) (err error) {
	fullPayload := packet.DATA
	off := 0
	fragID := uint8(0)
	fragCount := uint8((len(fullPayload) + fragSize - 1) / fragSize) // round up
```
- 为什么是问题：`fragSize` 为 0 时这里除零。native 发送（`v5/packet.go` 178-180 行）在 `len(p) > maxUdpRelayPacketSize` 时把该字段原样传进来。字段零值是 0，服务端建 v5 handler 时只在它大于 `MaxFragSizeV5` 时截断（`tuic/server.go` 233-235 行），0 会留着。datagram 路径把中继模式设成 native（`v5/server.go` 77 行），于是任意非空回包都进入这条除法。
- 方向：分片大小至少为 1，未配置时用 `MaxFragSize` 而不是 0。
