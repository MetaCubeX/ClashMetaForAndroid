---
title: "审查发现 r15 transport-rest"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条，最严重的是 SSR auth_sha1_v4 与 auth_chain 在长度偏移越界时 panic，进程会退出。"
---

# 审查发现 r15 transport-rest

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖上面列出的 transport 子目录。下面 5 条都来自这次读到的源码。

## r15-1
- 严重度：高
- 症状：auth_sha1_v4 或 auth_aes128 流解码收到长度字段合法、校验也通过、但随机前缀偏移超出本包的数据时，进程 panic。
- 代码：
```70:76:core/src/foss/golang/clash/transport/ssr/protocol/auth_sha1_v4.go
		pos := int(src.Bytes()[4])
		if pos < 255 {
			pos += 4
		} else {
			pos = int(binary.BigEndian.Uint16(src.Bytes()[5:7])) + 4
		}
		dst.Write(src.Bytes()[pos : length-4])
```
- 为什么是问题：长度只要求 `7 <= length < 8192`，随后用第 4 字节或第 5–6 字节算出 `pos`，直接做 `src.Bytes()[pos : length-4]`。`pos == 255` 时偏移最大可达 65539，大于 `length-4` 时切片越界。auth_sha1_v4 的 CRC32 和 Adler32 都不带密钥，能把明文送进 `Decode` 的对端可以自己填上这两项校验。`auth_aes128_sha1.go` 第 129–135 行是同一处切片，区别是前面的 HMAC 带密钥，持有 userKey 的对端仍能造出同样的 panic。
- 方向：切片前要求 `pos <= length-4`，否则返回长度错误。

## r15-2
- 严重度：高
- 症状：auth_chain_a / auth_chain_b 的 UDP 包在尾部 1 字节 HMAC 通过后，若随机填充比剩余载荷长，进程 panic。
- 代码：
```173:191:core/src/foss/golang/clash/transport/ssr/protocol/auth_chain_a.go
func (a *authChainA) DecodePacket(b []byte) ([]byte, error) {
	if len(b) < 9 {
		return nil, errAuthChainLengthError
	}
	if !bytes.Equal(tools.HmacMD5(a.userKey, b[:len(b)-1])[:1], b[len(b)-1:]) {
		return nil, errAuthChainChksumError
	}
	md5Data := tools.HmacMD5(a.Key, b[len(b)-8:len(b)-1])

	randDataLength := udpGetRandLength(md5Data, &a.randomServer)
	// ...
	wantedData := b[:len(b)-8-randDataLength]
```
- 为什么是问题：`udpGetRandLength`（同文件第 317–319 行）的结果是 `Next() % 127`，范围 0–126。入口只拒绝短于 9 字节的包。`len(b)-8-randDataLength` 为负时，`b[:负值]` 直接 panic。尾部校验只有 1 字节，短包在改最后一字节后有机会通过，随后高概率落到这个切片。auth_chain_b 嵌入 authChainA，走的是同一个 `DecodePacket`。
- 方向：切片前要求 `len(b) >= 8+randDataLength`，否则返回长度错误。

## r15-3
- 严重度：中
- 症状：mkcp 收包队列满时整包被丢掉；已经交给上层的数据段重传回来后不再回 ACK，发送窗口停住。
- 代码：
```74:79:core/src/foss/golang/clash/transport/mkcp/listener.go
		select {
		case l.packets <- packetPayload{payload: payload, addr: addr}:
		case <-l.closed:
			return
		default:
		}
```
- 为什么是问题：`default` 在 `packets`（容量 1024）满时直接丢掉这个数据报。客户端 `conn.go` 第 96–99 行对 1024 的 `cache` 同样处理。数据段一旦被 `deliverLocked` 推进 `recvNext`，重传包的 `s.number - c.recvNext` 会变成很大的 uint32，`conn.go` 第 320–327 行的窗口判断失败，既不进 `recvCache` 也不再写入 `ackList`。丢掉的若是 ACK，对端会重传已经交付的数据，接收端不再确认，在途窗口停在这些序号上。
- 方向：队列满时阻塞或丢弃后仍对已交付序号补 ACK。

## r15-4
- 严重度：中
- 症状：VLESS UDP 负载超过 65535 字节时，长度被截成 uint16，随后仍把整段原文写进流，后续包界错位。
- 代码：
```16:22:core/src/foss/golang/clash/transport/vless/packet.go
func (c *PacketConn) WriteTo(b []byte, addr net.Addr) (int, error) {
	err := binary.Write(c.Conn, binary.BigEndian, uint16(len(b)))
	if err != nil {
		return 0, err
	}

	return c.Conn.Write(b)
}
```
- 为什么是问题：`uint16(len(b))` 会截断。长度为 65536 时线上长度字段是 0，紧接着 `Write` 仍写出全部字节。对端 `ReadFrom` 按 uint16 长度取载荷，多出来的字节被当成下一帧的长度和数据。
- 方向：负载大于 65535 时直接拒绝，不要截断长度后再写全文。

## r15-5
- 严重度：中
- 症状：TrustTunnel UDP 只拒绝负的载荷长度，对端可以把长度声明到接近 4GiB，读取端按该长度继续从连接里读。
- 代码：
```94:98:core/src/foss/golang/clash/transport/trusttunnel/packet.go
	payloadLen := int(length) - (16 + 2 + 16 + 2)
	if payloadLen < 0 {
		return M.Socksaddr{}, E.New("invalid udp length: ", length)
	}
	_, err = buffer.ReadFullFrom(u.body, payloadLen)
```
- 为什么是问题：`length` 来自线上的 uint32。64 位上 `int(length)` 不会变负，因此 `payloadLen < 0` 只挡掉小于 36 字节头的声明。服务端 `readPacketFromClient` 第 230–234 行同样只做这个负数检查，然后 `ReadFullFrom` 按 `payloadLen` 读。一次声明就能让读取端试图吞下数百 MiB 到约 4GiB。
- 方向：给 UDP 载荷设上限，超出则丢弃该包并返回错误。
