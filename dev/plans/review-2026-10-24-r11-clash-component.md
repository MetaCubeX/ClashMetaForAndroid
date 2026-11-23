---
title: "审查发现 r11 clash-component"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条，最严重的是 fake-ip 刷新与分配并发时会丢掉或串掉域名映射。"
---

# 审查发现 r11 clash-component

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖 core/src/foss/golang/clash/component。下面 5 条都来自这次读到的源码。

## r11-1
- 严重度：高
- 症状：刷新 fake-ip 与查询叠在一起时，已分配的域名映射会丢，或者域名被写到另一个地址上，假 IP 反查到错误主机。
- 代码：
```110:116:core/src/foss/golang/clash/component/fakeip/pool.go
func (p *Pool) FlushFakeIP() error {
	err := p.store.FlushFakeIP()
	if err == nil {
		p.cycle = false
		p.offset = p.first.Prev()
	}
	return err
}
```
- 为什么是问题：`Lookup`、`LookBack`、`Exist` 都用 `p.mux` 包住对 `offset`、`cycle` 和 store 的修改（`pool.go` 43-45 行、94-107 行）。`FlushFakeIP` 清空整个 store 并改写这两个字段，却不拿这把锁。`StoreState`（119-125 行）读它们时同样不拿锁。刷新可以插进 `get` 的中间：`offset` 已经推进，随即被拨回 `first` 的前一个地址，后面的 `PutByIP` 用的是被改过的 `p.offset`。持久化记录会交叉；内存 store 则是在同一份缓存上并发增删。
- 方向：刷新和保存游标与查询共用 `p.mux`，持锁期间一次做完清库和游标复位。

## r11-2
- 严重度：中
- 症状：Android 上按应用分流时，一条连接会被算成另一个应用，那个应用的规则套到这条连接上。
- 代码：
```398:410:core/src/foss/golang/clash/component/process/process_linux.go
		if lineIP == targetIP {
			return uint32(lineUID), uint32(lineInode), true, nil
		}

		if lineInode == 0 {
			continue
		}

		if !found || (bestUID == 0 && lineUID != 0) {
			bestUID = uint32(lineUID)
			bestInode = uint32(lineInode)
			found = true
		}
```
- 为什么是问题：同文件 290-293 行写明，TUN 模式下 metadata 的源地址经常是网关或 fake-ip，对不上套接字的本地地址。IP 对不上时，这里只要本地端口相同就收下第一条 inode 非 0 的记录；后面同端口但 UID 不同的连接不再比较。IPv4 和 IPv6 的端口空间分开，两个应用可以同时占用同一个端口号。`findProcessName` 在 netlink 已经找到套接字、只是 UID 为 0 时（72-76 行），还会用这个端口匹配替换原来的 inode。
- 方向：端口不能唯一对应一个套接字时返回未找到，不要用另一条连接的 UID 覆盖 netlink 结果。

## r11-3
- 严重度：中
- 症状：扫描 `/proc` 时只要有一个无关进程退出，这次进程识别就失败，目标应用还在也不会被认出来。
- 代码：
```188:193:core/src/foss/golang/clash/component/process/process_linux.go
		info, err := f.Info()
		if err != nil {
			return "", err
		}
		if info.Sys().(*syscall.Stat_t).Uid != uid {
			continue
		}
```
- 为什么是问题：`resolveProcessNameByProcSearch` 在某个 pid 的 `Info` 失败时直接返回。进程刚退出时这里常见 ENOENT。同文件的 `resolveProcessNameByUID`（244-246 行）遇到同样的错误是跳过并继续。Android 上 netlink 之后总会走到这次按 inode 的扫描（82 行），一次无关退出就会让按应用匹配失败。
- 方向：单个 `/proc` 条目消失时跳过，继续找目标 inode。

## r11-4
- 严重度：中
- 症状：打开 TFO 后，调用方取消连接或设置的超时不起作用，拨号被固定成约 5 秒；连接建立前设置的读写截止时间被丢掉。
- 代码：
```128:138:core/src/foss/golang/clash/component/dialer/tfo.go
func dialTFO(ctx context.Context, netDialer net.Dialer, network, address string) (net.Conn, error) {
	ctx, cancel := context.WithTimeout(context.Background(), DefaultTCPTimeout)
	dialer := tfo.Dialer{Dialer: netDialer, DisableTFO: false}
	return &tfoConn{
		dialed: make(chan bool, 1),
		cancel: cancel,
		ctx:    ctx,
		dialFn: func(ctx context.Context, earlyData []byte) (net.Conn, error) {
			return dialer.DialContext(ctx, network, address, earlyData)
		},
	}, nil
}
```
- 为什么是问题：传入的 `ctx` 立刻被 `context.Background()` 上的 `DefaultTCPTimeout`（`dialer.go` 22 行，5 秒）盖掉，`dialContext` 带来的取消和截止时间没有被使用。已经取消的调用仍会拨号。`tfoConn.SetReadDeadline` 和 `SetWriteDeadline`（91-102 行）在底层连接还是 nil 时直接返回 nil，调用方以为截止时间已经生效。`Read` 只等内部这个 ctx，超时后返回 `io.ErrUnexpectedEOF`。
- 方向：拨号沿用调用方的 context，连接出现之前先记住读写截止时间。

## r11-5
- 严重度：中
- 症状：GeoIP 或 GeoSite 有一次并发加载失败后，同一个国家或列表会一直失败，文件后来已经可用也不再读。
- 代码：
```182:187:core/src/foss/golang/clash/component/geodata/utils.go
	if err != nil {
		if !shared {
			loadGeoIPMatcherSF.Forget(country) // don't store the error result
			log.Warnln("Load GeoIP rule: %s", country)
		}
		return nil, err
	}
```
- 为什么是问题：`loadGeoIPMatcherSF`、`loadGeoSiteMatcherSF`、`loadGeoSiteMatcherListSF` 都设了 `StoreResult: true`（66-67 行、156 行）。注释说明 `Forget` 是为了不把错误结果留下来，但只在 `shared` 为 false 时才调用。并发加载同一个 key 时 `shared` 为 true，发起方和等待方都不会 `Forget`，错误留在组里。之后同名的 `LoadGeoIPMatcher` 直接返回这次错误，直到进程重启或有人 `Clear`。GeoSite 列表和 matcher 是同一写法（106-110 行、143-147 行）。
- 方向：加载失败时无论 `shared` 与否都 `Forget`，不要把错误留在打开了 `StoreResult` 的组里。
