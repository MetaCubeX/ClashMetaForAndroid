---
title: "审查方案 r14 transport-proxy"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 5、drop 0、weaken 0"
---

# 审查方案 r14 transport-proxy

核对发现稿 5 条。引用行号上的行为仍在，且都是缺陷。hold 5、drop 0、weaken 0。

## r14-1

- 问题类：VLESS UDP 的 `ReadFrom` 在缓冲区短于长度前缀时已经取走长度，载荷仍留在流上，下一次读取会把载荷当成新的长度。
- 不变量：`ReadFrom` 返回 `io.ErrShortBuffer` 时，这一包的载荷必须已经从连接上读完；下一次 `ReadFrom` 从下一包的长度字段开始。只有整包载荷都读完才返回 `io.ErrShortBuffer`；丢弃过程中的读错误原样返回。
- 要动的符号或文件：`core/src/foss/golang/clash/transport/vless/packet.go` 的 `(*PacketConn).ReadFrom`。活码在 25–35 行：`binary.Read` 读出 `uint16` 长度后，若 `len(b) < int(length)` 直接 `return 0, nil, io.ErrShortBuffer`。这里改为按该长度把载荷读完并丢掉，再返回 `0, nil, io.ErrShortBuffer`。调用方缓冲区里不写入这一包的任何字节。
- 明确不改什么：`(*PacketConn).WriteTo`；`(*PacketConn).WaitReadFrom`（38–57 行按长度向池申请缓冲，不走这条短缓冲路径）；`Client.PacketConn` 的返回类型。
- 怎么本地验证：目录内现有 `TestAddons`、`TestHasAESGCMHardwareSupport` 不覆盖 `PacketConn.ReadFrom`。应新增断言：底层连续两包长度前缀加载荷，第一次 `ReadFrom` 的缓冲短于第一包载荷，返回 `io.ErrShortBuffer` 且 `n == 0`；紧接着用足够大的缓冲 `ReadFrom`，得到的是第二包的完整载荷。丢弃读到一半连接出错时，返回该读错误，而不是 `io.ErrShortBuffer`。

## r14-2

- 问题类：SOCKS5 客户端握手读了 REP 却不判断，上游拒绝连接时仍把绑定地址当成握手成功。
- 不变量：`ClientHandshake` 只有应答 REP 为 `0x00` 时才返回绑定地址和 nil 错误。REP 为其他值时返回 `Error(REP)`，不返回绑定地址。
- 要动的符号或文件：`core/src/foss/golang/clash/transport/socks5/socks5.go` 的 `ClientHandshake`。活码在 269–274 行：`io.ReadFull(rw, buf[:3])` 之后直接 `return ReadAddr(rw, buf)`。在 `ReadAddr` 之前检查 `buf[1]`，非 `0x00` 则 `return nil, Error(buf[1])`。
- 明确不改什么：`ServerHandshake` 的应答组包；`ReadAddr` / `ReadAddr0` 的地址解析；方法协商里对 `buf[1]` 的认证判断（227–262 行）；不增加对应答 VER 的检查；失败分支不继续读绑定地址。
- 怎么本地验证：`transport/socks5` 下没有测试。应新增断言：应答为 `VER=5, REP=0x05, RSV=0` 且后面是合法绑定地址时，`ClientHandshake` 返回的错误等于 `ErrConnectionRefused`，地址为 nil。`REP=0x04` 时错误等于 `ErrHostUnreachable`。`REP=0x00` 且绑定地址合法时，返回该地址且错误为 nil。

## r14-3

- 问题类：SSR `http_simple` 把一次 `Read` 里没有 `\r\n\r\n` 当成对端关闭，已读字节被丢掉。
- 不变量：响应头未凑齐时，已读字节保留在这次读的累积里，直到读到空行或底层连接错误。找不到空行不得返回 `io.EOF`。凑齐之后，空行后的正文仍按现有逻辑交给调用方，多出来的正文留在 `httpConn.buf`。
- 要动的符号或文件：`core/src/foss/golang/clash/transport/ssr/obfs/http_simple.go` 的 `(*httpConn).Read`。活码在 56–65 行：一次 `c.Conn.Read`，`bytes.Index` 找不到 `\r\n\r\n` 就 `return 0, io.EOF`，`hasRecvHeader` 仍为 false（66 行才置 true）。改为在同一次 `Read` 调用里继续从 `c.Conn` 读取并拼接，直到出现 `\r\n\r\n` 或底层返回错误。`Read` 返回 `n > 0` 时先把这 `n` 字节拼进累积，再处理错误。累积上限为 `pool.RelayBufferSize`；超过仍无空行则返回错误，且该错误不是 `io.EOF`。找到空行后沿用 66–71 行：置 `hasRecvHeader`，把正文拷进调用方缓冲，剩余正文留在 `c.buf`。
- 明确不改什么：`(*httpConn).Write`、`packURLEncodedHeadData`、`packBoundary`、`userAgent`；头已收完之后直接 `c.Conn.Read` 的路径（52–54 行）；`c.buf` 作为头之后正文剩余的含义（41–49 行）。部分头不能放进 `c.buf`，否则会被当成正文返回。
- 怎么本地验证：`transport/ssr` 下没有测试。应新增断言：响应头拆成两次底层读，第一次不含 `\r\n\r\n`，第二次补齐头和正文，`httpConn.Read` 不返回 `io.EOF`，读到的正文与头后的字节一致。空行的 `\r\n\r\n` 被拆在两次读的边界上时，仍能进入正文。累积超过 `pool.RelayBufferSize` 仍无空行时，错误不是 `io.EOF`，且 `hasRecvHeader` 仍为 false。

## r14-4

- 问题类：SSR `tls1.2_ticket_auth` 把一次 `Read` 的全部字节当成整段服务端握手，分段则校验失败，粘在同一读里的后续字节也参与这段 HMAC。
- 不变量：握手消息的结尾由缓冲内容决定，不由单次 `Read` 的 `n` 决定。不足 76 字节或握手尚未结束时，已读字节留在连接缓冲里。两处 HMAC 只覆盖握手前缀：`[33:43]` 对 `[11:33]`，结尾 10 字节对握手前缀去掉这 10 字节后的部分。前缀之后的字节进入现有 `handshakeStatus == 8` 的 `0x17 03 03` 记录解析。
- 要动的符号或文件：`core/src/foss/golang/clash/transport/ssr/obfs/tls1.2_ticket_auth.go` 的 `(*tls12TicketConn).Read`。活码在 54–57 行只读一次；77–78 行 `n < 11+32+1+32` 时返回 `errTLS12TicketAuthTooShortData` 且不保留字节；81–82 行 HMAC 使用本次 `buf` 的 `n`；85 行成功时 `return 0, nil`，不把剩余字节写入 `decoded`。状态 8 的重组在 59–74 行，握手阶段要先把字节追加到 `underDecoded`（此阶段它还没被使用）。缓冲不足 43 字节时继续读。已有 43 字节但 `hmacSHA1(buf[11:33])[:10]` 与 `[33:43]` 不相等时，返回 `errTLS12TicketAuthHMACError`。固定 HMAC 通过后，在长度至少 76 的前缀里取最短的 `L`，使结尾 10 字节等于 `hmacSHA1(前缀[:L-10])[:10]`。找不到则继续读；累积超过 `pool.RelayBufferSize` 仍找不到则返回 `errTLS12TicketAuthHMACError`。找到后只消费这 `L` 字节，调用现有 `Write(nil)` 进入状态 8，其余字节留在 `underDecoded`，立刻走 59–74 行的记录解析；这次 `Read` 能交出的完整记录要返回给调用方。
- 明确不改什么：`(*tls12TicketConn).Write` 里客户端握手组包、`packData`、`packAuthData`、`packSNIData`、`packTicketBuf`；状态 8 对 `0x17 03 03` 和 `uint16` 长度的解析规则；现在 `c.Write(nil)` 的返回值被忽略这一点。
- 怎么本地验证：`transport/ssr` 下没有测试。应新增断言：握手分两次到达，第一次短于 76 字节且不含合法结尾 HMAC，第二次补齐；`Read` 不返回 `errTLS12TicketAuthTooShortData` 或 `errTLS12TicketAuthHMACError`，随后能读到应用记录载荷。同一次底层读里，合法握手前缀之后紧跟一条完整 `0x17 03 03` 记录时，HMAC 针对握手前缀成功，`Read` 返回该记录载荷。固定位置 `[33:43]` 的 HMAC 错误时，不再继续等待后续字节。

## r14-5

- 问题类：Shadowsocks AEAD 的 `Write` 在底层写出失败时，仍把这一块明文算进返回长度，并且 nonce 已经前进。
- 不变量：`(*shadowaead.Writer).Write` 返回的 `n` 只包含密文已完整写到底层的明文长度。每一块对应的两次 nonce 自增，只发生在该块密文被底层原样全部写出之后。底层返回错误，或写出的字节数小于该块密文长度时，`n` 不含这一块，nonce 停在这一块之前。
- 要动的符号或文件：`core/src/foss/golang/clash/transport/shadowsocks/shadowaead/stream.go` 的 `(*Writer).Write`。活码在 48–61 行：循环后置语句 `n += nr` 在 `w.Writer.Write` 失败后仍执行；56 行和 58 行的 `increment(nonce)` 在写出之前。改为先 `Seal`，仅当底层写出的长度等于该块密文字节数时才两次 `increment` 并执行 `n += nr`；否则返回当时的 `n` 和底层错误。半块密文不折算成明文长度。
- 明确不改什么：`(*Reader).read`、`(*Reader).Read`、`(*Reader).WriteTo`；`(*Writer).ReadFrom` 的返回值语义（67–91 行，`n` 是从源读到的字节数）及其 nonce 更新；39–45 行 `len(p) == 0` 的空块分支；`Conn.initWriter` 的 salt 写出。
- 怎么本地验证：`transport/shadowsocks` 下没有测试。应新增断言：第一块底层写出成功、第二块返回错误且写出 0 字节时，`Write` 的 `n` 等于第一块明文长度且 `err != nil`；用同一 `Writer` 从该偏移重写剩余明文，对端按原 nonce 序列解出全部明文。第一块就写出 0 字节并返回错误时，`n == 0`，随后用同一 `Writer` 重写全部明文仍能被对端解密。底层只写出密文的一部分时，`n` 不含该块且 `err != nil`。
