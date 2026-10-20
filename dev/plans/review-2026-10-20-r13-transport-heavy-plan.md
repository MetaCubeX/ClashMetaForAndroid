---
title: "审查方案 r13 transport-heavy"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 5、drop 0、weaken 0"
---

# 审查方案 r13 transport-heavy

核对范围是 `transport/hysteria` 与 `transport/tuic` 的当前源码。五条引用行上的行为都还在。

## r13-1

处置：hold。严重度维持高。

**问题类**：同一条 Hysteria 连接上，后到的分片消息会清掉尚未拼完的前一条，交错到达的分片被丢掉。

**不变量**：`Feed` 只在某一 `(SessionID, MsgID)` 自己的分片没到齐时返回 nil。另一个 `MsgID` 或另一个 `SessionID` 的分片到达时，已收到的分片仍留在该键下。同一键的分片到齐后交出完整 `Data`。`FragCount <= 1` 的数据报仍立即返回，不进入重组表。

**要动的符号或文件**：

- `core/src/foss/golang/clash/transport/hysteria/core/frag.go` 的 `defragger` 与 `Feed`（现为 29–64 行的单槽：`msgID` 不同就 `make` 新切片）。改为按 `(SessionID, MsgID)` 保存未完成分片。
- `core/src/foss/golang/clash/transport/hysteria/core/client.go` 的 `Client.udpDefragger`（48 行）与 `handleMessage` 里的 `Feed` 调用（137 行）：这一状态只服务当前这一次 `handleMessage`，不与另一条 QUIC 连接共用。
- `core/src/foss/golang/clash/transport/hysteria/core/frag_test.go` 的 `Test_defragger_Feed`。

**明确不改什么**：不改 `fragUDPMessage`、`WriteTo` 的分片格式，也不在 `WriteTo` 里按 `MsgID` 重传。不改 `udpMessage` 的线上字段。连接结束时丢掉该连接未完成的分片；不要因为出现新的 `MsgID` 就清掉其他键。

**怎么本地验证**：改 `Test_defragger_Feed`。该测试现在把 `MsgID` 777 与 778 插队后、必须重发第一片才拼上「hello moto」写成期望。改为断言：两条不同 `MsgID` 的分片按 A1、B1、A2、B2 送入后都能重组；两条不同 `SessionID`、分片交错时同样都能重组。`Test_fragUDPMessage` 仍按原样通过。

## r13-2

处置：hold。严重度维持中。

**问题类**：`Feed` 用本包的 `FragCount` 判断下标之后，直接索引可能更短或为 nil 的切片，进程会崩。

**不变量**：`Feed` 在任何输入下都不 panic。`FragID >= FragCount`、切片为空、或 `FragID >= len(frags)` 时返回 nil。同一 `(SessionID, MsgID)` 上后到的 `FragCount` 与已有切片长度不一致时，不按旧切片下标写入；丢弃该包，或按新的 `FragCount` 重建这一键的状态后再放入。零值 `defragger` 收到 `MsgID == 0` 且 `FragCount > 1` 的分片时同样不 panic。

**要动的符号或文件**：`frag.go` 的 `Feed`（35–64 行）。49 行 `d.frags[m.FragID]` 位于 `MsgID` 相同的分支，进入前只比较了 `m.FragID` 与 `m.FragCount`（39–42 行）。与 r13-1 同一次改 `defragger` 的存储时，分配切片之后、写入之前用切片长度再判一次。

**明确不改什么**：不改 `FragCount <= 1` 的直接返回。不把合法、长度一致的分片重组改成丢弃。不在 `handleMessage` 外包 `recover` 来掩盖越界。

**怎么本地验证**：在 `Test_defragger_Feed` 上补断言：零值 `defragger` 喂入 `MsgID == 0`、`FragCount > 1`、`FragID < FragCount` 的分片，测试进程不崩，返回值为 nil 或按新规则重组的结果。先喂入较小 `FragCount` 建好切片，再喂入同一 `MsgID`、更大 `FragCount`、且 `FragID` 不小于旧切片长度的分片，测试进程不崩。

## r13-3

处置：hold。严重度维持高。

**问题类**：重连换上新 QUIC 连接时，旧的收包循环仍按会话号往新的 `udpSessionMap` 里送，会话表指针的替换也不在锁里。

**不变量**：数据报只进入收到它的那条 QUIC 连接所对应的会话表。`handleMessage` 不读取 `Client.udpSessionMap` 字段。`udpSessionMap` 的替换与 `DialUDP`、`CloseFunc` 对表的读写都在 `udpSessionMutex` 下发生。新连接的 `Feed` 看不到上一条连接留下的分片。旧连接在被替换后不再继续往新表投递。

**要动的符号或文件**：

- `client.go` 的 `connectToServer`（75–101 行）：98 行替换 `udpSessionMap`、99 行 `go c.handleMessage(qs)`、100 行写下 `quicSession` 之前，对旧的 `quicSession` 调用 `CloseWithError`。
- `handleMessage`（126–153 行）：改为接收启动时传入的会话表和这一次连接专用的 `defragger`，142 行只查这张表。
- `openStreamWithReconnect` 的永久错误重连分支（177–184 行）走的就是 `connectToServer`，不另写一套替换逻辑。
- `Client.udpDefragger`（48 行）不再跨连接保留；随这一次 `handleMessage` 创建，或在发布新表时一并清掉，且旧循环不再碰它。

**明确不改什么**：不改 `DialUDP` 里 `CloseFunc` 捕获创建时那张 `sessionMap` 的做法（254–270 行）。不改控制流鉴权、`WriteClientHello` / `ReadServerHello`、以及 `quicConn` 的 TCP 读写。

**怎么本地验证**：仓库里没有覆盖重连的测试。应新增断言：`OpenStream` 返回非临时错误并再次 `connectToServer` 之后，从旧 `quic.Conn` 取出的 UDP 数据报不会出现在新 `udpSessionMap` 的通道里；替换 `udpSessionMap` 与 `DialUDP` 的加表、`handleMessage` 的查表使用同一把 `udpSessionMutex`；新连接上第一个分片的重组结果不包含旧连接未拼完的 `Data`。

## r13-4

处置：hold。严重度维持中。

**问题类**：TUIC 把域名地址整段放进 `net.UDPAddr.IP`，`ReadFrom` 交给上层的对端不是这个域名。

**不变量**：`AtypDomainName` 时，`ReadFrom` 与 `WaitReadFrom` 返回的 `net.Addr` 主机名是 `ADDR` 里跳过长度字节后的域名，端口是 `PORT`，与 `Address.String` 的域名分支一致。该 `net.Addr` 再经 `NewAddressNetAddr` 写回时，类型仍是域名，主机名不变。`AtypIPv4` 与 `AtypIPv6` 仍得到原来的 IP 与端口。

**要动的符号或文件**：

- `transport/tuic/v4/protocol.go` 的 `Address.UDPAddr`（581–587 行）不能再被域名包使用；域名的 `ADDR` 在 518–528 行是「长度字节 + 域名」。
- `transport/tuic/v4/packet.go` 的 `ReadFrom`（102 行）与 `WaitReadFrom`（117 行）。
- `transport/tuic/v5/protocol.go` 的 `Address.UDPAddr`（566–572 行）；域名布局在 497–508 行。
- `transport/tuic/v5/packet.go` 的 `ReadFrom`（108 行）与 `WaitReadFrom`（128 行）。

v4 与 v5 各走各的 `Address`，两处都要改。域名分支返回主机名形式的 `net.Addr`。

**明确不改什么**：不改 `WriteTo`、`NewAddress`、`NewAddressAddrPort`，以及 `NewAddressNetAddr` 里 IP 已能解析时的路径。不改 v4/v5 `server.go` 里 `parsePacket` 对 `SocksAddr` 的用法。

**怎么本地验证**：这两个目录下没有 `UDPAddr` 或 `ReadFrom` 的测试。应新增断言：构造 `AtypDomainName` 且 `ADDR[0]` 为域名长度的包，v4 与 v5 的 `ReadFrom`、`WaitReadFrom` 得到的主机名等于 `string(ADDR[1:])`、端口等于 `PORT`；把该地址交给 `NewAddressNetAddr` 后类型仍是域名。长度为 4 与 16 的纯 IP 包仍然解析成对应 IP。三字节域名（`ADDR` 总长为 4）不得被当成 IPv4。

## r13-5

处置：hold。严重度维持中。

**问题类**：Hysteria UDP 关联的读截止时间打在保活流上，超时会拆掉整条关联。

**不变量**：`SetReadDeadline` 只让当前正在等的 `ReadFrom` 在截止时间返回超时。超时错误满足 `Timeout() == true`，并且与 `ErrClosed` 不是同一个错误。`MsgCh` 保持打开，`CloseFunc` 不被这次超时调用，会话表项还在。通道里已经有的数据报仍按到达顺序读出；没有数据报时才在截止时间返回超时。清除截止时间之后，后续 `ReadFrom` 还能读到新数据报。`Hold` 只在 QUIC 流本身读失败时关闭关联。

**要动的符号或文件**：

- `client.go` 的 `quicPktConn.SetReadDeadline`（430–432 行）：不再调用 `c.Stream.SetReadDeadline`。
- `quicPktConn.SetDeadline`（426–428 行）：读截止时间走同一套本地截止时间；写截止时间仍交给流。
- `quicPktConn.ReadFrom`（372–379 行）：在 `MsgCh` 与本地读截止时间之间等待。
- `Hold`（360–370 行）继续读流，但该读不再带上用户的读截止时间。

**明确不改什么**：不改 `SetWriteDeadline`。不改 `Hold` 在流真正结束时调用 `Close` 的行为。不改 `WriteTo` 与 `DialUDP` 创建通道、登记会话的过程。

**怎么本地验证**：仓库里没有 `quicPktConn` 的测试。应新增断言：设置读截止时间并让它到期后，`ReadFrom` 返回可识别的超时，`CloseFunc` 未被调用，再写入 `MsgCh` 的数据报仍能被读出；`SetReadDeadline(time.Time{})` 之后没有数据报时 `ReadFrom` 继续等待，而不是返回 `ErrClosed`。
