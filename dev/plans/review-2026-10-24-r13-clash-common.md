---
title: "审查发现 r13 clash-common"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条；最严重的是管道短写丢数据并卡死写端，以及带读超时的 ReadPacket 空指针丢包"
---

# 审查发现 r13 clash-common

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖 core/src/foss/golang/clash/common。下面 5 条都来自这次读到的源码。

## r13-1
- 严重度：高
- 症状：内存管道的 WaitReadBuffer 在写入读缓冲失败时丢掉已接到的字节，对端 Write 永久堵在回执上，Close 也解不开。
- 代码：
```199:208:core/src/foss/golang/clash/common/net/deadline/pipe_sing.go
	case bw := <-p.rdRx:
		buffer = p.readWaitOptions.NewBuffer()
		var nr int
		nr, err = buffer.Write(bw)
		if err != nil {
			buffer.Release()
			return
		}
		p.readWaitOptions.PostReturn(buffer)
		p.rdTx <- nr
```
- 为什么是问题：同文件第 22–24 行约定，从 rdRx 取出数据后必须向 rdTx 回执消费掉的字节数。写端第 129–130 行把整段剩余数据送出后，单独阻塞在 `<-p.wrRx`，这次等待不在 select 里，localDone/remoteDone 都碰不到它。上面这条路径一旦 Write 返回错误，先 Release 掉已经写进 buffer 的内容再 return，rdTx 不会被发送。读端拿到错误，写端不再往下走。同文件普通 Read 在第 94–96 行是 copy 之后无条件回执。
- 方向：Write 出错时仍按实际写入的字节数回执，未消费的尾部留给写端重发。

## r13-2
- 严重度：高
- 症状：Sing 包连接在读超时生效后，ReadPacket 的后台读一完成就空指针崩溃，这一包从连接里读走后既不交给调用方也不归还缓冲。
- 代码：
```92:99:core/src/foss/golang/clash/common/net/deadline/packet_sing.go
func (c *singPacketConn) pipeReadPacket(pLen int) {
	buffer := buf.NewSize(pLen)
	destination, err := c.singPacketConn.ReadPacket(buffer)
	result := &singReadResult{}
	result.destination = destination
	result.err = err
	c.netPacketConn.resultCh <- result
}
```
- 为什么是问题：deadline 非零且未禁用 pipe 时，ReadPacket 在第 86–89 行把令牌取走并调用这里。result.buffer 保持 nil，本地 buffer 没有放进结果，也没有 Release。消费端第 57 行执行 `result.buffer.Bytes()`，必然空指针。同文件的 pipeWaitReadPacket 在第 171 行会把 buffer 赋回结果。
- 方向：后台读完成后把 buffer 放进结果，消费端在 buffer 为 nil 时不要解引用。

## r13-3
- 严重度：中
- 症状：Observable 里有一个订阅者不再读取后，事件循环停住，其余订阅者收不到后续事件，退订也进不去。
- 代码：
```16:24:core/src/foss/golang/clash/common/observable/observable.go
func (o *Observable[T]) process() {
	for item := range o.iterable {
		o.mux.Lock()
		for _, sub := range o.listener {
			sub.Emit(item)
		}
		o.mux.Unlock()
	}
```
- 为什么是问题：Emit 是无超时发送（observable/subscriber.go 第 14–16 行），通道缓冲只有 200（第 30 行）。process 持着 o.mux 发送，缓冲满就会一直占着锁。UnSubscribe 在第 50 行也要这把锁，没法关闭那个订阅者的通道，发送就解不开。一个停读的订阅者会卡住全部分发，process 也走不到末尾的 close。
- 方向：分发事件时不要占着订阅表的锁。

## r13-4
- 严重度：中
- 症状：正文里夹着换行的 Base64 订阅会整份导入失败，一行节点都解析不出来。
- 代码：
```24:33:core/src/foss/golang/clash/common/convert/base64.go
func tryDecodeBase64(buf []byte) ([]byte, error) {
	dBuf := make([]byte, encRaw.DecodedLen(len(buf)))
	n, err := encRaw.Decode(dBuf, buf)
	if err != nil {
		n, err = enc.Decode(dBuf, buf)
		if err != nil {
			return nil, err
		}
	}
	return dBuf[:n], nil
}
```
- 为什么是问题：ConvertsV2Ray 第 17 行先对整段正文调用 DecodeBase64。这里的 RawStdEncoding 和 StdEncoding 都把换行当成非法字符。解码失败后 DecodeBase64 在第 18–19 行把原文原样返回，再按换行拆开。折行后的每一段只是 Base64 碎片，没有 `://`，全部被跳过，最后返回 format invalid。
- 方向：整段解码前去掉空白，或换用接受换行的 Base64 解码。

## r13-5
- 严重度：中
- 症状：包连接用一种读法超时后改用另一种读法，调用会空转占住 CPU，已经读进通道的报文交不出去。
- 代码：
```70:81:core/src/foss/golang/clash/common/net/deadline/packet.go
		case result := <-c.resultCh:
			if result != nil {
				if result, ok := result.(*readResult); ok {
					n = copy(p, result.data)
					addr = result.addr
					err = result.err
					c.resultCh <- nil // finish cache read
					return
				}
				c.resultCh <- result // another type of read
				runtime.Gosched()    // allowing other goroutines to run
				continue FOR
			}
```
- 为什么是问题：resultCh 只有一个槽。ReadFrom 只认 `*readResult`，类型不对就把结果塞回去并立刻再取。WaitReadFrom（packet_enhance.go 第 38–50 行）、ReadPacket（packet_sing.go 第 52–67 行）、WaitReadPacket（第 133–145 行）是同一套循环，各自只认自己的类型。读超时从递归调用返回后，后台 pipe 读仍会把结果送进通道。若当时没有另一个正堵在 select 里、且类型匹配的读者，当前调用会一直取出又放回，报文留在通道里，调用不返回。
- 方向：结果类型不匹配时不要忙等，留下结果并让调用方可以等到匹配的读者或超时返回。
