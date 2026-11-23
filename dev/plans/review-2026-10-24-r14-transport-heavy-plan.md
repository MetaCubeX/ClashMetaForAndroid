---
title: "审查方案 r14 transport-heavy"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 3、drop 0、weaken 2"
---

# 审查方案 r14 transport-heavy

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 `core/src/foss/golang/clash/transport/` 下的 sudoku、tuic、openvpn、hysteria。发现稿 5 条都落在 tuic 与 hysteria；sudoku、openvpn 没有条目。

计数：hold 3、drop 0、weaken 2。

drop：无。

## r14-1

判定：weaken。严重度由高降为中。

现在行号：`transport/tuic/v4/server.go` 的 `serverHandler.HandleStream` 仍是 118–129 行，先调用 `HandleTcpFn`，返回后再 `NewResponseSucceed` 或 `NewResponseFailed`，然后 `buf.WriteTo(conn)`。`transport/tuic/v4/client.go` 的 `DialContext` 仍是 311–334 行：`FastOpen` 为 false 时先 `response()`，其中 `ReadResponse` 在 315 行。响应仍是版本、类型、REP 三字节（`v4/protocol.go` 的 `CommandHead` 加 `Response.REP`）。

反证：仓库里的 v4 服务端回调不会把转发做完才返回。`listener/tuic/server.go` 129–138 行的 `handleTcpFn` 用 goroutine 调用 `HandleSocket`，然后立刻 `return nil`。`HandleStream` 因此马上把 Connect 响应写到流上，不等待目标连接结束。非 FastOpen 的客户端不会因为转发函数未返回而停在 `ReadResponse`。只有转发 goroutine 抢先把目标字节写进同一条流时，这三字节才会被当成响应。当前 goroutine 在 `HandleTcpFn` 返回后立刻写响应；转发侧还要过规则和拨号才会写目标数据。这是缺少先后屏障的竞态，不是发现稿写的必现卡住。

问题类：同一条 QUIC 流上的协议帧和应用数据没有先后顺序。

不变量：v4 Connect 响应的三字节完整写入流之后，转发才可以写入应用字节。`HandleTcpFn` 的错误返回仍然可以选择成功或失败响应，前提是它返回前自己不写这条件流。

要动的符号或文件：`v4/server.go` 的 `serverHandler.HandleStream`，以及传给 `HandleTcpFn` 的那条 `conn`。

明确不改什么：不改 `v5/server.go` 的 `HandleStream`（v5 没有 Connect 响应）。不改客户端 `FastOpen` 为 true 时的 `NewEarlyConn`。不改 `listener/tuic/server.go` 里“先返回 nil、拨号失败以后再关连接”的结构，也不为这条去补拨号失败后的 `ResponseFailed`。

本地验证：在 `core/src/foss/golang/clash` 为 `transport/tuic/v4` 增加测试。先把 handler 的 `authOk` 设为真并关闭 `authCh`，否则 `HandleStream` 会停在 `<-s.authCh`。`HandleTcpFn` 在返回前启动 goroutine，向传入的连接写一段已知负载。对端读到的前三字节必须是 `0x04`、`0xff` 和对应 REP，负载只能出现在这三字节之后。现有 `v4/packet_test.go` 只覆盖 UDP 包。命令：`go test ./transport/tuic/v4/ -count=1`。

## r14-2

判定：hold。

现在行号：`transport/tuic/v5/protocol.go` 的 `Packet.BytesLen` 仍是 326–328 行，`PacketOverHead` 仍是 330 行。`WriteTo` 在命令头之后写出的字段在 295–311 行，不是发现稿写的 303–311。303–311 只有 `FRAG_TOTAL`、`FRAG_ID`、`SIZE` 共 4 字节；`ASSOC_ID` 和 `PKT_ID` 在 295–301 行。五字段实际宽度是 2+2+1+1+2＝8。`BytesLen` 只加了 4+2。`v5/frag.go` 的 `MaxFragSize` 仍是 17 行。超限重试仍是 `v5/packet.go` 192 行：`MaxDatagramPayloadSize - PacketOverHead`。

IPv6 地址的 `Address.BytesLen` 与 `WriteTo` 一致，是 1+16+2。命令头 2 字节。空负载的 IPv6 包实际 29 字节，`PacketOverHead` 算成 27。按这个值切出的 datagram 仍大 2 字节，同公式的重试也大 2 字节，`fragWriteNative` 在第一次 `SendDatagram` 失败时就返回。

问题类：包头长度少算，分片尺寸贴着 datagram 上限时发送失败。

不变量：`Packet.BytesLen` 等于 `WriteTo` 写入的字节数。用 `MaxDatagramPayloadSize - PacketOverHead` 得到的分片，经 `WriteTo` 后的长度不超过该上限。首片按 IPv6 地址计算时必须成立。

要动的符号或文件：`v5.Packet.BytesLen`。`PacketOverHead` 和 `MaxFragSize` 由它推导，重试表达式继续用 `PacketOverHead` 即可。

明确不改什么：不改 `v4/protocol.go` 的 `Packet.BytesLen`。v4 在命令头后是 `uint32` 的 `ASSOC_ID` 和 `uint16` 的 `LEN`，4+2 与 `WriteTo` 一致。不改地址编码，也不改 `Address.BytesLen`。IPv4 地址比 IPv6 短 12 字节，现有常量的余量盖得住这 2 字节；不能用只发 IPv4 的结果判断长度已经正确。

本地验证：在 `transport/tuic/v5` 里对 IPv6 首片调用 `WriteTo` 到 `bytes.Buffer`，长度必须等于 `BytesLen()`。再取 `fragSize = limit - PacketOverHead`，断言该分片 `WriteTo` 后的长度小于等于 `limit`。命令：`go test ./transport/tuic/v5/ -count=1`。

## r14-3

判定：hold。

现在行号：`transport/tuic/v5/frag.go` 的 `deFragger.Feed` 仍是 84–97 行，缓存键只有 `m.PKT_ID`。片数相同则不重置；`FRAG_ID` 槽已占用则 `return nil`，空槽则写入。凑满后 103–115 行按槽拼接。发送端 `v5/packet.go` 160 行仍是 `pktId := uint16(randv2.Uint32())`，没有避开尚未重组完的编号。

服务端每个 `ASSOC_ID` 已有自己的 `serverUDPInput.deFragger`（`v5/server.go` 98–102 行）。客户端每个 `quicStreamPacketConn` 也内嵌一个 `deFragger`。混包发生在同一关联内部，不是跨关联串袋。

问题类：重组身份只有 16 位 `PKT_ID`，未完成的两包可以拼进同一个袋。

不变量：一次交付的 `DATA` 只能来自同一个 UDP 包。槽位上已有另一包的分片时，不得把新分片填进该袋。同一分片的原样重传可以继续丢弃。

要动的符号或文件：`deFragger.Feed` 和 `packetBag`。发送侧若要少碰撞，改 `quicStreamPacketConn.WriteTo` 里 `pktId` 的选取，避开本连接仍未重组完的编号。对端重用编号时，接收侧仍必须满足上面的不变量。

明确不改什么：不把 `ASSOC_ID` 加进 LRU 键。关联已经由 `serverUDPInput` 和 `quicStreamPacketConn` 分开。不改 `PKT_ID` 的线格式，仍是 `uint16`。片数不同时现有逻辑会清空旧袋，这条保持为丢弃未完成的旧包，不改成跨片数拼接。

本地验证：沿 `v5/packet_test.go` 的 `Feed` 路径，送入两个 `FRAG_TOTAL == 2`、`PKT_ID` 相同的包：先 A 的片 0，再 B 的片 1。交付结果不得同时含 A、B 的负载。相同片的重复送达仍不增加 `count`。命令：`go test ./transport/tuic/v5/ -count=1`。

## r14-4

判定：weaken。严重度由中降为低。

现在行号：`transport/hysteria/congestion/pacer.go` 的 `pacer.Budget` 仍是 43–49 行，乘积在 47 行：带宽乘空闲纳秒再除 `1e9`。`BrutalSender.HasPacingBudget` 仍是 `brutal.go` 56–57 行，要求预算不小于 `maxDatagramSize`。`lastSentTime` 仍只在 `SentPacket`（33–41 行）更新，而 `OnPacketSent` 在 72–75 行调用它。`transport/tuic/congestion/pacer.go` 55 行是同一公式，没有负值分支。`transport/tuic/congestion_v2/pacer.go` 46–49 行仍在预算小于 0 时钳到 `1<<62-1`。`congestion.ByteCount` 是 `int64`。

反证：负预算不会一直留着。`Budget` 每次用当前时间重算。`HasPacingBudget` 为假时，quic-go 的 `SendMode` 返回 `SendPacingLimited`，`triggerSending` 仍会走 `maybeSendAckOnlyPacket`。ACK 进入 `OnPacketSent` 后，`SentPacket` 发现 `size > budget` 会把 `budgetAtLastSent` 置 0 并刷新 `lastSentTime`。没有 ACK 时，乘积跨过 `2^64` 后符号也会回到非负。按 100 Mbps（12_500_000 字节/秒），乘积大约在空闲 738 秒到 1476 秒之间为负，之后可以再发。永久停发不成立。这个窗口里应用数据确实发不出去，所以保留为低。

问题类：有符号预算先乘后除，空闲窗口内 pacing 拒绝发送。

不变量：`Budget` 在带宽为正、当前时间不早于 `lastSentTime` 时不为负。空闲再久，`HasPacingBudget` 也要在积攒满一个 datagram 后变为真，不能停在溢出后的负值上。

要动的符号或文件：`hysteria/congestion.pacer.Budget`，以及 `tuic/congestion.pacer.Budget` 的同一公式。先把纳秒换成秒再乘带宽；乘积为负时钳到突发上限。

明确不改什么：不改 `tuic/congestion_v2/pacer.go`，它已有负值钳制。不改 `BrutalSender.GetCongestionWindow` 和 ACK 率。不改 quic-go 的 `SendMode`。

本地验证：测试放在 `package congestion`，因为 `pacer` 未导出。带宽取 12_500_000，`lastSentTime` 设为约 15 分钟前，`Budget` 必须大于等于 `maxDatagramSize`。再取约 30 秒的空闲，预算仍为正且不超过突发上限。命令：`go test ./transport/hysteria/congestion/ -count=1`，以及 `go test ./transport/tuic/congestion/ -count=1`。工作目录是 `core/src/foss/golang/clash`。

## r14-5

判定：hold。

现在行号：`transport/tuic/v5/frag.go` 的 `fragWriteNative` 仍是 19–24 行，23 行以 `fragSize` 做分母。`v5/packet.go` 178–180 行在 `len(p) > maxUdpRelayPacketSize` 时把该字段原样传入。`transport/tuic/server.go` 233–235 行只在值大于 `MaxFragSizeV5` 时截断，0 会留下。`v5/server.go` 77 行把 datagram 路径设为 `types.NATIVE`。`fragSize` 为 0 时，非空负载在 23 行整数除零，进程 panic。

问题类：分片大小的零值进入除法。

不变量：传入 `fragWriteNative` 的 `fragSize` 至少为 1。`MaxUdpRelayPacketSize` 未配置（0）时用 `MaxFragSize`，不用 0。重试表达式 `MaxDatagramPayloadSize - PacketOverHead` 若小于 1，也不得传入 `fragWriteNative`。

要动的符号或文件：`fragWriteNative`、`quicStreamPacketConn.WriteTo`，以及 `tuic/server.go` 里组建 v5 `ServerOption` 时对 `maxUdpRelayPacketSize` 的下界。客户端 `v5/client.go` 使用同一个 `WriteTo`，一处改完两条方向都覆盖。

明确不改什么：不改 `listener/tuic/server.go` 119–120 行把 0 换成 1500，也不改 `adapter/outbound/tuic.go` 170–171 行把 0 换成 1252。这两处已经在进入传输层之前替换 0。默认配置 `config.go` 里的 1500 也不改。传输层自己仍要在收到 0 时不除零。

本地验证：直接构造 `maxUdpRelayPacketSize == 0` 的 v5 发送路径，写入一段非空 UDP 负载，不得 panic，并按 `MaxFragSize` 分片或整包发送。不要只启动监听器：那条路径会先把 0 改成 1500，复现不到。命令：`go test ./transport/tuic/v5/ -count=1`。
