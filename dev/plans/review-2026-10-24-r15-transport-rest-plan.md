---
title: "审查方案 r15 transport-rest"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 3、drop 2、weaken 0"
---

# 审查方案 r15 transport-rest

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


hold 3、drop 2、weaken 0。

drop：r15-3、r15-5。

- r15-3：`listener.go` 74–79 行和 `conn.go` 96–99 行在队列满时仍丢弃整包；已滑过 `recvNext` 的重传仍进不了 `conn.go` 320–327 行的 `ackList`。发送窗口会继续前进。`flushAcksLocked`（572–612 行）按 `nextFlush` 重发还留在 `ackList` 里的确认；`flush`（560–561 行）每 3000ms 发出的 ping 在 `pingSegmentLocked`（697–701 行）带上 `receivingNext`；`processReceivingNextLocked`（422–436 行）删除编号小于 `receivingNext` 的发送段。
- r15-5：`packet.go` 94–98 行和 230–234 行仍只拒绝 `payloadLen < 0`。`ReadFullFrom`（模块 `github.com/metacubex/sing` v0.5.8 的 `common/buf/buffer.go` 166–169 行）在 `end+size` 大于 `capacity` 时返回 `io.ErrShortBuffer`，不从连接读取。`ReadPacket` 使用调用方已经准备的 buffer。`WaitReadPacket` 的默认包缓冲是 `UDPBufferSize`：标准构建 16KiB，`with_low_memory` 为 8KiB。

## r15-1

结论：hold。行号未动。严重度维持高。

核对：`auth_sha1_v4.go` 的 `Decode` 仍在 70–76 行用 `pos` 做 `src.Bytes()[pos : length-4]`。长度门仍是 55–58 行的 `7 <= length < 8192`。`auth_aes128_sha1.go` 的同一形态切片仍在 129–135 行。`auth_aes128_md5.go` 的 `newAuthAES128MD5` 返回同一个 `authAES128`，没有第二份切片。用 `PickProtocol` 构造对象后，`auth_sha1_v4` 的 9 字节帧（第 4 字节为 10，CRC32 与 Adler32 按 `Decode` 的算法填好）panic，切片界是 `[14:5]`。`auth_aes128_sha1` 的 11 字节帧（第 4 字节为 255，第 5–6 字节为 little-endian 1000，HMAC 用 `userKey` 和当前 `recvID`）panic，切片界是 `[1004:7]`。`transport/ssr/protocol` 的 `Conn.Read` 没有 `recover`。

问题类：解码时切片越界，进程 panic。

不变量：CRC/HMAC 与 Adler/HMAC 都通过之后，只有 `pos <= length-4` 才把 `[pos:length-4]` 写入 `dst`。`pos == length-4` 的空载荷保持允许。越界时与本函数已有的非法长度分支相同：置 `rawTrans`、`Reset` 掉 `src`、返回对应的 length 错误。`pos < 255` 与 `pos == 255` 两种偏移都走这一比较。

要动的符号或文件：

- `authSHA1V4.Decode`，`core/src/foss/golang/clash/transport/ssr/protocol/auth_sha1_v4.go`。失败时走 55–58 行的收尾，返回 `errAuthSHA1V4LengthError`。
- `authAES128.Decode`，同目录 `auth_aes128_sha1.go`。比较放在 127 行 `recvID++` 之前。失败时走 112–116 行的收尾，返回 `errAuthAES128LengthError`。

明确不改什么：不改 `Encode`、`packData`、`packAuthData`、`packRandData`，不改 CRC、Adler、HMAC 和两种长度前缀的字节序。不改 `auth_aes128_md5.go`。不改 `rawTrans` 在其它错误上的既有含义。

怎么本地验证：在 `core/src/foss/golang/clash` 为 `transport/ssr/protocol` 增加包内测试，然后执行 `go test ./transport/ssr/protocol/ -count=1`。

- `auth_sha1_v4`，Key 任意。9 字节：大端长度 9，小端 `crc32.ChecksumIEEE` 的低 16 位，第 4 字节为 10，Adler32 覆盖前 5 字节并写在最后 4 字节。结果是 `errAuthSHA1V4LengthError`，`dst` 为空。同一对象再 `Decode` 一帧恶意数据时不再 panic。
- 对照：同一构造，第 4 字节为 0。现在的结果是 `err == nil`、写入 1 字节、`src` 被消费完。改完保持这个结果。
- `auth_aes128_sha1`：对 `PickProtocol` 返回的对象（`recvID` 为 0，`userKey` 等于 Key）构造 11 字节帧。小端长度 11；`macKey` 是 `userKey` 接 4 字节小端 `recvID`；前 2 字节的 HMAC-SHA1 前 2 字节放在 `[2:4]`；第 4 字节为 255；`[5:7]` 为小端 1000；前 7 字节的 HMAC-SHA1 前 4 字节放在末尾。结果是 `errAuthAES128LengthError`。把第 4 字节改成 0、去掉 `[5:7]` 的大偏移后，`Decode` 成功且不 panic。`auth_aes128_md5` 走同一个 `Decode`，不必另备一份切片样本。

## r15-2

结论：hold。行号未动。严重度维持高。

核对：`authChainA.DecodePacket` 仍是 173–191 行。入口仍只拒绝短于 9 字节的包。尾部仍是 `HmacMD5(userKey, b[:len(b)-1])` 的 1 字节。`udpGetRandLength` 仍是 317–319 行，`Next() % 127`，范围 0–126。切片仍是 189 行的 `b[:len(b)-8-randDataLength]`。`auth_chain_b.go` 嵌入 `*authChainA`，没有自己的 `DecodePacket`。`packet.go` 31 行和 44 行把 UDP 载荷交给 `DecodePacket`。Key 为 `key` 时，64 个不同的 9 字节包在补上这 1 字节 HMAC 后，`auth_chain_a` 全部 panic。同样形状的一包在 `auth_chain_b` 上 panic，切片界是 `[:-1]`。200 字节且尾字节 HMAC 正确的包现在返回 `nil`。

问题类：UDP 解码时切片越界，进程 panic。

不变量：`udpGetRandLength` 得出 `randDataLength` 之后，只有 `len(b) >= 8+randDataLength` 才取 `b[:len(b)-8-randDataLength]` 并做 RC4。否则返回 `errAuthChainLengthError`，并且不改输入切片。

要动的符号或文件：`authChainA.DecodePacket`，`core/src/foss/golang/clash/transport/ssr/protocol/auth_chain_a.go`。判断放在 189 行切片和紧随的 `XORKeyStream` 之前。

明确不改什么：不改 `EncodePacket`、`udpGetRandLength` 的取值，不改 1 字节 HMAC。不改流式 `Decode` 里 112–114 行和 139–141 行已有的负数护栏。不改 `auth_chain_b.go`。不在 `DecodePacket` 里置 `rawTrans`。

怎么本地验证：仍在 `core/src/foss/golang/clash` 执行 `go test ./transport/ssr/protocol/ -count=1`。

- `auth_chain_a` 与 `auth_chain_b` 各发 9 字节包，最后一字节等于 `HmacMD5(userKey, 前 8 字节)` 的首字节。结果是 `errAuthChainLengthError`，原切片最后一字节不变。
- 对照：200 字节包，最后一字节用同样方式填上。结果仍是 `nil` 错误，不返回 `errAuthChainLengthError`。不要用 `EncodePacket` 再 `DecodePacket` 对照明文；当前这对函数的随机填充输入不一致，成功返回的字节并不是原载荷。

## r15-4

结论：hold。行号未动。严重度维持中。

核对：`PacketConn.WriteTo` 仍是 `vless/packet.go` 16–22 行：`uint16(len(b))` 之后 `Conn.Write(b)` 写出整段。`ReadFrom` 25–39 行和 `WaitReadFrom` 42–60 行按这个 `uint16` 取载荷。对 `PacketConn` 写入 65536 字节时，`WriteTo` 返回 `n=65536, err=nil`，连接上的前两字节是 0，总字节数是 65538。

问题类：长度前缀被截断，流上的包界错位。

不变量：线上的 `uint16` 等于紧随其后的载荷字节数。`len(b) > 65535` 时 `WriteTo` 返回错误，并且不写长度、不写载荷。`len(b) <= 65535` 时字节布局与现在相同。

要动的符号或文件：`PacketConn.WriteTo`，`core/src/foss/golang/clash/transport/vless/packet.go`。在 `binary.Write` 之前拒绝超长切片。

明确不改什么：不改 `ReadFrom`、`WaitReadFrom`，不改 `addr` 被忽略的现状，不改长度不超过 65535 时的写入。

怎么本地验证：在 `core/src/foss/golang/clash` 执行 `go test ./transport/vless/ -count=1`。用内存中的 `net.Conn` 承接写入。65536 字节的 `WriteTo` 返回错误，连接上的字节数为 0。65535 字节的 `WriteTo` 成功，前两字节为 65535，总字节数为 65537。
