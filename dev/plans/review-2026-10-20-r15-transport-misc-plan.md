---
title: "审查方案 r15 transport-misc"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 4、drop 0、weaken 1"
---

# 审查方案 r15 transport-misc

核对发现稿 5 条，引用行号与活码一致。hold 4、drop 0、weaken 1。

## r15-1

- 问题类：packet-up 已向调用方报告写成功的上行，在 POST 失败或关闭时会被丢掉，且 `Close` 仍返回成功。
- 不变量：`Write` 返回 nil 的字节，必须在 `Close` 返回 nil 之前 POST 成功。POST 失败时这段字节仍留在 `PacketUpWriter.buf`，`Close` 返回 `flushErr`。重发必须使用同一次失败 POST 的序号。
- 要动的符号或文件：`core/src/foss/golang/clash/transport/xhttp/client.go` 的 `PacketUpWriter.flush`（101–105 行：`write` 返回后无条件 `c.buf = c.buf[:0]`）、`PacketUpWriter.Close`（143–156 行：最多等 1 秒，然后 `cancel` 并 `return nil`）、`PacketUpWriter.write`（121–122 行：`RoundTrip` 之前就 `c.seq++`）。`Close` 必须等到这一次 `flush` 结束再返回；若用 `cancel` 打断卡住的 `RoundTrip`，打断后仍要等 `flush` 结束，并把 `flushErr` 返回出去。失败时保留 `buf`。序号只在 POST 成功后前进，或在失败时退回尚未成功的序号。
- 明确不改什么：stream-one、stream-up、下载 GET、`WaitReadCloser`、服务端 `UploadQueue` 的按序重组，以及 `Conn.Close` 已有的错误合并。
- 怎么本地验证：现有 `TestServerHandlerModeRestrictions`、`TestUploadQueueMaxPackets`、`TestManagerReuseSameEntry` 都不覆盖 `PacketUpWriter`。应新增断言：缓冲未满时 `Write` 返回成功后，POST 失败则 `Close` 返回该错误且缓冲仍是这段字节；用同一序号重发后，服务端按该序号放出载荷。POST 成功时 `Close` 返回 nil，对端字节与 `Write` 一致。

## r15-2

- 问题类：mekya 服务端发送队列满时丢掉该包，同时向调用方返回全部写成功。
- 不变量：`serverSession.Write` 只有在包已进入 `writerChan` 时才返回 `len(p), nil`。队列满时阻塞到会话上下文结束；上下文已结束则返回错误，且该包没有入队。
- 要动的符号或文件：`core/src/foss/golang/clash/transport/mekya/server.go` 的 `serverSession.Write`（281–290 行的 `default` 分支）。通道容量仍是 `getSession` 里的 `s.cfg.PacketWritingBuffer`（137 行）。去掉 `default`，使发送与 `<-s.ctx.Done()` 一起阻塞。
- 明确不改什么：`requestClientSession.Write`（已阻塞至入队、截止或取消）、`writeResponse` 的出队与分片、`ingest`、`PacketWritingBuffer` 的配置值，以及 mKCP `writeSegments` 的重试次数。
- 怎么本地验证：现有 `TestRoundTrip`、`TestNewRoundTripperALPNRouting` 不覆盖满队列。应新增断言：`writerChan` 已满且没有消费者时，`Write` 在上下文取消前不得返回成功；取消后返回错误，通道长度不变。有空位时 `Write` 返回成功，`writeResponse` 能把该包写出。

## r15-3

- 问题类：gost relay 的 UDP 帧比 `ReadFrom` 缓冲区长时，尾部被丢掉，调用方仍得到成功的数据报。
- 不变量：长度字段标明的整帧必须从流里消费完，后续帧边界保持不动。缓冲区放不下整帧时返回错误，返回值里的字节数不得让调用方把截短前缀当成完整 UDP 包。
- 要动的符号或文件：`core/src/foss/golang/clash/transport/gost/relay.go` 的 `relayPacketConn.ReadFrom`（293–309 行）。`packetLen > len(p)` 时读完或丢弃这一帧的 `packetLen` 字节，然后返回 `0` 与错误（可用 `io.ErrShortBuffer`），不要 `return copy(p, buf), c.raddr, nil`。
- 明确不改什么：`WriteTo` 的长度上限与绑定地址检查、TCP relay 握手、`ReadFrom` 在 `packetLen <= len(p)` 时的原样读满。
- 怎么本地验证：`transport/gost` 下没有测试。应新增断言：帧长大于缓冲区时 `ReadFrom` 返回错误且字节数为 0；紧接着用足够大的缓冲区调用 `ReadFrom`，能读到下一帧的完整载荷。帧长不超过缓冲区时仍返回该帧全文和 nil 错误。

## r15-4

- 问题类：mKCP 在配置了 `Seed` 时，MSS 没把 GCM nonce 算进开销，加密 UDP 包比 `mtu()` 多出 nonce 长度。
- 不变量：任意一个写出的 UDP 包长度不超过 `Config.mtu()`。`Seed` 非空时开销包含 `NonceSize()`、`Overhead()` 和可选包头；`Seed` 为空时 nonce 开销为 0。
- 要动的符号或文件：`core/src/foss/golang/clash/transport/mkcp/packet.go` 的 `packetWriter.overhead`（52–63 行）。`security != nil` 时加上 `w.security.NonceSize()`。`newConn` 已用该返回值计算 `mss`（`conn.go` 119 行），写出路径已把 nonce 放进包（`packet.go` 80–87 行），这两处跟着变长即可，不必另写一套长度公式。
- 明确不改什么：`simpleAuthenticator`（`NonceSize` 已是 0，`Overhead` 为 6）、`Config.security` 的 AES-GCM 构造、包头伪装、`securityOverhead` 的切片容量预留，以及 `newConn` 里 `mss < 576` 时抬到 576 的下限。
- 怎么本地验证：现有 `TestRoundTrip`、`TestFullDuplex`、`TestRoundTripHeaders` 都使用空 `Seed`，只保证无 seed 时往返仍通。应新增断言：`Seed` 非空时，单个满 MSS 数据段写出的 UDP 包长度不超过 `mtu()`（未设置时为 1350）；空 `Seed` 时包长也不超过 `mtu()`。

## r15-5

- 问题类：simple-obfs TLS 在读记录长度失败时返回 `(0, nil)`，把这一次读取错误吞掉。
- 严重度：低。发现稿标为中，按边界降级。
- 反证：`TLSObfs.read`（`tls.go` 27–38 行）和 `TLSObfsServer.read`（`tls_server.go` 26–37 行）里，固定头的 `io.ReadFull` 失败已经 `return 0, err`。只有后面 2 字节长度失败才 `return 0, nil`。对端关闭、读超时和底层错误在 `net.Conn` 上会保持失败：下一次 `Read` 先读固定头（客户端 3 或 105 字节，服务端 3 或 140 字节，或 `skipOtherExts`），这次 `ReadFull` 会把错误返回出去。`io.Copy` 只会对这一次 `(0, nil)` 再读一轮，然后因错误结束，不会一直空转。
- 不变量：长度字段的 `ReadFull` 失败时，`Read` 返回该错误，不返回 `(0, nil)`。
- 要动的符号或文件：`core/src/foss/golang/clash/transport/simple-obfs/tls.go` 的 `TLSObfs.read`（37–38 行）和 `tls_server.go` 的 `TLSObfsServer.read`（36–37 行）。把这两处改成 `return 0, err`。
- 明确不改什么：固定头读取失败时已经返回错误的分支、`remain` 续读、HTTP simple-obfs（`http.go`、`http_server.go`）。
- 怎么本地验证：`transport/simple-obfs` 下没有测试。应新增断言：固定头已读完、长度 2 字节读到 `io.EOF` 或超时错误时，`TLSObfs.Read` 与 `TLSObfsServer.Read` 返回该错误，且返回字节数为 0。
