---
title: "审查方案 r13 clash-common"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 3、drop 1、weaken 1"
---

# 审查方案 r13 clash-common

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 core/src/foss/golang/clash/common。

hold 3、drop 1、weaken 1。

drop：r13-4

## r13-1

判定：hold。严重度维持高。引用行号未漂移，行为仍在 `core/src/foss/golang/clash/common/net/deadline/pipe_sing.go`。

- 第 22–24 行约定：从 `rdRx` 成功取出之后必须向 `rdTx` 回执。
- `write` 第 129–130 行把剩余切片送出后，在 select 之外执行 `nw := <-p.wrRx`。`localDone`、`remoteDone` 和写超时都在第 128–139 行的 select 里，碰不到这次等待。
- `waitReadBuffer` 第 199–206 行：从 `rdRx` 取出 `bw` 后，`buffer.Write(bw)` 返回错误就 `Release` 并 return。第 208 行的 `p.rdTx <- nr` 不会执行。
- `Close` 第 169–171 行只关闭 `localDone`。
- 普通 `read` 第 94–96 行在 `copy` 之后无条件回执。

sing v0.5.8 的 `Buffer.Write` 只在调用前 `IsFull()`（`end == capacity`）时返回 `io.ErrShortBuffer`，这时 `n == 0`。缓冲未满但数据比剩余空间长时返回 `(n, nil)`，走第 207–208 行，写端按 `nw` 切掉前缀再送尾部。错误分支才缺回执：读端已经取走切片，写端停在第 130 行，这一段到不了调用方。`wrMu` 在第 125–126 行一直持有，`Close` 解不开。

问题类：回执缺失造成的死锁。触发条件是这次 `WaitReadBuffer` 的读缓冲在 `Write` 之前已经满了。

不变量：每次从 `rdRx` 收到数据之后都向 `rdTx` 发送消费字节数，包括 `Write` 返回错误。错误时回执值用 `Write` 返回的 `n`（当前实现是 0），未消费的尾部留在写端。读端同时把该错误返回给 `WaitReadBuffer` 的调用方。写端收到回执后回到 select，`Close` 和写超时重新生效。

要动的符号：`pipe.waitReadBuffer`。文件：`core/src/foss/golang/clash/common/net/deadline/pipe_sing.go`。

明确不改：`read` 第 94–96 行；`waitReadBuffer` 在 `err == nil` 时的 `PostReturn` 和回执（第 207–208 行）；`write` 的分片循环；`Pipe` 的通道配对。

怎么本地验证：在 `core/src/foss/golang/clash` 下对 `common/net/deadline` 做检查。`Pipe()` 取出两端，读端 `InitializeReadWaiter`，选项 `MTU == 0` 且 `FrontHeadroom == buf.BufferSize`（默认构建 32KiB，`with_low_memory` 为 16KiB），让 `NewBuffer` 在 `Write` 前已满。对端把一段非空数据的 `Write` 放进单独的 goroutine，读端调用 `WaitReadBuffer`。读端应返回错误。读端 `Close` 之后，写端应返回，不能停在回执上。再用零值 `ReadWaitOptions` 写入一段长于 `BufferSize` 的数据，两端都应结束，数据按片交割完整。

## r13-2

判定：hold。严重度维持高。引用行号未漂移，行为仍在 `core/src/foss/golang/clash/common/net/deadline/packet_sing.go`。

- `ReadPacket` 第 77–89 行：`disablePipe` 为假且 `deadline` 非零时，取走 `resultCh` 令牌，启动 `pipeReadPacket`，再递归进入 `ReadPacket`。
- `pipeReadPacket` 第 92–99 行：本地 `buffer` 承接底层 `ReadPacket`，结果只写入 `destination` 和 `err`。`singReadResult.buffer` 保持 nil，本地缓冲没有 `Release`。
- 消费端第 57 行调用 `result.buffer.Bytes()`。sing v0.5.8 的 `Bytes` 读取 `b.data`，nil 接收者在这里崩溃。这一包已经从底层连接读走。
- `pipeWaitReadPacket` 第 171 行会把 `buffer` 赋回结果。`pipeReadPacket` 没有对应赋值。

问题类：空指针崩溃，同时这一包丢失，临时缓冲泄漏。

不变量：`pipeReadPacket` 在底层 `ReadPacket` 返回后，把该 `buffer` 放进 `singReadResult.buffer`，再送进 `resultCh`。`err` 非 nil 时也要放进去。消费 `*singReadResult` 时，先确认 `buffer` 非 nil，再调用 `Bytes`、`Advance`、`Release`。

要动的符号：`singPacketConn.pipeReadPacket`；`singPacketConn.ReadPacket` 里处理 `*singReadResult` 的分支（第 54–63 行）。文件：`core/src/foss/golang/clash/common/net/deadline/packet_sing.go`。

明确不改：`pipeWaitReadPacket`（第 168–175 行已经赋值）；`WritePacket`；`WaitReadPacket` 使用的 `*singWaitReadResult`；类型不匹配时的放回循环，那一部分归 r13-5。

怎么本地验证：在 `common/net/deadline` 用实现 `packet.SingPacketConn` 的桩，`ReadPacket` 向传入缓冲写入固定字节并返回一个目的地址。外层经 `NewSingPacketConn` 包装，`SetReadDeadline` 设为将来的非零时间，`disablePipe` 保持假，再调用 `ReadPacket`。调用应返回，不发生 panic，调用方缓冲里是那一包字节，桩分配的缓冲在读完后被 `Release`。桩改为返回非 nil 错误时，调用同样不 panic，临时缓冲不泄漏。

## r13-3

判定：hold。严重度维持中。引用行号未漂移。

- `core/src/foss/golang/clash/common/observable/observable.go` 第 16–23 行：`process` 持有 `o.mux` 时对每个订阅者 `Emit`。第 24 行的 `close` 要等这轮循环结束。
- `subscriber.go` 第 14–16 行：`Emit` 执行 `s.buffer <- item`，没有超时，也没有别的退出分支。
- 第 28–31 行：缓冲长度为 200。
- `UnSubscribe` 第 49–51 行需要同一把 `o.mux`，才能从 `listener` 删除并 `Close`。

一个订阅者累计 200 条不读之后，`Emit` 停在发送上并占着锁。其余订阅者收不到后续事件。`UnSubscribe` 进不了临界区。`process` 走不到第 24 行，`stopCh` 也不会关。

问题类：持锁做可能一直阻塞的发送。一个订阅者停读会卡住整条事件循环和退订。

不变量：`process` 执行可能阻塞的 `Emit` 时不持有 `o.mux`。`UnSubscribe` 在分发进行时仍能拿到锁并移除该订阅者。停读的订阅者不挡住其他订阅者收到后续事件，也不挡住迭代结束后的 `close`。分发前复制当时仍在表里的订阅者，放开锁后再对这份副本发送。`Emit` 已经阻塞在某个 `buffer` 上时，不能关掉那个 `buffer`，否则 `process` 会 panic。

要动的符号：`Observable.process`。发送侧若要在退订后停下来，再动 `Subscriber.Emit`。文件：`observable/observable.go`、`observable/subscriber.go`。

明确不改：`newSubscriber` 的缓冲长度 200；`Subscribe` 在 `done` 时返回的错误；`Iterable` 的通道类型；迭代结束后 `close` 关闭各订阅者与 `stopCh` 的结果。

怎么本地验证：在 `core/src/foss/golang/clash` 执行 `go test ./common/observable/`。现有测试里的订阅者都会把通道读完。另补这个场景：两个订阅者，其中一个不读；向源写入至少 201 条；另一个订阅者应在超时内收到后续事件；对不读的那个调用 `UnSubscribe` 应在超时内返回；源关闭后 `stopCh` 应在超时内关闭。

## r13-5

判定：weaken。严重度由中降为低。引用行号未漂移。类型不匹配时的忙等代码还在。发现稿里「读超时返回后，当前调用一直不返回」这一句，在截止时间没有被重新 `Set` 时不成立。

反证：

1. 四个读法的 `select` 同时等 `pipeDeadline`：`packet.go` 第 86–87 行，`packet_enhance.go` 第 55–56 行，`packet_sing.go` 第 72–73 行和第 149–150 行。递归调用因超时返回时，截止时间已经是过去，`PipeDeadline.Set`（`pipe.go` 第 58–60 行）已经关闭 `cancel`。下一次读若没有再次 `Set`，超时分支处于就绪，调用返回 `os.ErrDeadlineExceeded`。
2. 同类型的下一次读取会消费缓存。`ReadFrom` 在 `packet.go` 第 72–77 行认出 `*readResult` 后复制、写回 nil 并返回。报文留在通道里，留给下一次同类型的读取。
3. 放回加 `runtime.Gosched` 的注释写明这是在配合另一种读法（`another type of read` / `allowing other goroutines to run`）。有一个堵在同一 `select` 里的匹配读者时，循环是在让出执行权。

仍然成立的部分：`resultCh` 容量为 1（`packet.go` 第 32 行）。`ReadFrom`（第 79–81 行）、`WaitReadFrom`（`packet_enhance.go` 第 48–50 行）、`ReadPacket`（`packet_sing.go` 第 65–67 行）、`WaitReadPacket`（第 142–144 行）各自只认自己的结果类型，不匹配就把结果塞回并立刻再取。若当时没有匹配读者，并且 deadline 已被 `Set` 成零值（`pipe.go` 第 40–43 行，`Wait` 的通道不再关闭）或刷新到将来，当前调用会持续取出又放回。零值时调用不返回，CPU 被占住；将来的截止时间则要空转到那个时限才返回。报文留在通道里，这次不匹配的调用交不出去。

问题类：类型不匹配时的忙等。有截止时间时占住 CPU，截止时间为零时调用不返回。

不变量：`resultCh` 里已有结果时，不匹配的读法在没有匹配读者的情况下不得忙等。结果留在通道里，匹配的读法仍能取走。不匹配的调用在 deadline 为零时阻塞等待；deadline 在将来时不得空转，到点返回超时。不匹配的调用不得丢掉已缓存的报文。

要动的符号：`NetPacketConn.ReadFrom`、`enhancePacketConn.WaitReadFrom`、`singPacketConn.ReadPacket`、`singPacketReadWaiter.WaitReadPacket` 里放回另一种结果的那一支。文件：`net/deadline/packet.go`、`net/deadline/packet_enhance.go`、`net/deadline/packet_sing.go`。

明确不改：`resultCh` 容量 1 和开头放入的 nil 令牌（`packet.go` 第 32–34 行）；四种后台读写入的结果类型；匹配成功后写 nil 结束缓存的路径；`net/deadline/conn.go`。那里的 `resultCh` 类型是 `chan *connReadResult`，不参与这四种结果的互认。

怎么本地验证：桩一个 `net.PacketConn`，包进 `NewNetPacketConn`。`SetReadDeadline` 设一个很短的将来时间，调用 `ReadFrom`，让后台读把 `*readResult` 送进 `resultCh`，这次调用因超时返回。然后 `SetReadDeadline(time.Time{})`，再调用 `WaitReadFrom` 或 `ReadPacket`：调用应停在等待上，进程不把一个核打满；关闭连接后调用能返回。另一次把 deadline 设回将来：应在截止时间返回超时，等待期间不忙等。最后再用 `ReadFrom` 读取，应取回那一包缓存，字节和地址与桩写入的一致。
