---
title: "审查方案 r11 clash-component"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 4、drop 0、weaken 1"
---

# 审查方案 r11 clash-component

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 core/src/foss/golang/clash/component。

hold 4、drop 0、weaken 1。

drop：无。

## r11-1

判定：hold。严重度维持高。

`FlushFakeIP` 仍在 `fakeip/pool.go` 110-116 行：清库成功后把 `cycle` 写成 false，把 `offset` 写成 `first.Prev()`，全程不拿 `p.mux`。`StoreState` 仍在 119-125 行读这两个字段并写入 cachefile，也不拿锁。`Lookup` 的锁仍在 43-45 行，`LookBack` 在 59-61 行，`Exist` 在 66-68 行。发现稿把后两处锁写成 43-45，行号不全，锁还在。`get` 仍在 94-107 行改 `offset`、`cycle` 和 store，只被持锁的 `Lookup` 调用。

刷新可以插在 `get` 已经执行 `p.offset = p.offset.Next()` 之后、`PutByIP` 之前。后面的 `PutByIP` 用的是被拨回的 `p.offset`。`POST /cache/fakeip/flush`（`hub/route/cache.go`）和查询可以同时发生。`Shutdown` 会调用 `StoreState`。

问题类：共享状态的并发。

不变量：`Lookup`、`LookBack`、`Exist`、`FlushFakeIP`、`StoreState` 对 `offset`、`cycle` 和 store 的访问处在同一把 `p.mux` 里。`FlushFakeIP` 成功返回时，旧映射已经清掉，`cycle == false`，`offset == first.Prev()`。一次 `Lookup` 返回的地址落在 `[first, last]`，`LookBack` 这个地址得到本次写入的 host。

要动的符号：`Pool.FlushFakeIP`、`Pool.StoreState`，文件 `component/fakeip/pool.go`。`get` 继续由 `Lookup` 持锁调用，自己不加锁。`sync.Mutex` 不可重入。`restoreState`（128-144 行）会调用 `FlushFakeIP`，加锁之后从 `New` 调用仍然安全。

明确不改：`store` 接口、`memory.go` / `cachefile.go` 的键和 bbolt 桶、`common/lru` 的锁、分配起点和回绕条件、`CloneFrom`。内存 LRU 每次 `Get`/`Set`/`Clear` 已有自己的锁，结构不会被并发写坏；要补的是整段游标更新和清库的原子性。

本地验证：在 `core/src/foss/golang/clash` 下执行 `go test -race -count=1 -run 'TestPool_Flush' ./component/fakeip/`。现有 `TestPool_FlushMemoryCache` 和 `TestPool_FlushFileCache` 是单 goroutine，只覆盖 flush 之后重新分配。再补并发：若干 goroutine 同时 `Lookup`、`LookBack`，另一个 goroutine 反复 `FlushFakeIP`。`-race` 不得报告 `offset`、`cycle` 或 store 的数据竞争。`Lookup` 返回的地址必须能被 `LookBack` 查回同一 host，并且落在 `first` 与 `last` 之间。

## r11-2

判定：hold。严重度维持中。

`searchProcNetFileByPort` 398-410 行未变。IP 相等时立刻返回该行；`inode == 0` 跳过；否则在 `!found || (bestUID == 0 && lineUID != 0)` 时留下第一条。之后同端口、UID 已经非 0 的另一条连接不会再比较。注释仍在 290-293 行。`findProcessName` 72-76 行未变：netlink 已经找到套接字且 UID 为 0 时，procfs 返回非 0 UID 会把 `uid` 和 `inode` 一起换成端口匹配的结果。`resolveSocketByProcFS` 334-342 行对 `/proc/net/{tcp,tcp6}` 或 udp 的两个文件使用同一条保留规则，IPv4 文件先扫。

问题类：套接字身份歧义。

不变量：精确 IP 加端口命中时，仍返回该行的 UID 和 inode（398-399 行），包含 inode 为 0 的行。端口回退只在恰好一个 inode 非 0 的套接字命中时返回。零个或多于一个都返回 `ErrNotFound`。netlink 已经得到 inode 时，procfs 只能补上同一 inode 的非 0 UID，不能换成另一条连接的 inode。

要动的符号：`searchProcNetFileByPort`、`resolveSocketByProcFS`、`findProcessName`。文件 `component/process/process_linux.go`。

明确不改：`resolveSocketByNetlink` 144-168 行的地址和端口过滤；`/proc/net` 的列下标（UID 是 `fields[7]`，inode 是 `fields[9]`）；CMFA 的 `DefaultPackageNameResolver`。本仓库 `cmfa` 构建里 `features.CMFA` 为真，`tunnel/tunnel.go` 的进程查找走 `FindPackageName`，不调用 `findProcessName`。这条仍要改，因为函数行为和 `!cmfa` 调用还在。

本地验证：`process` 包目前没有测试。`searchProcNetFileByPort` 接收文件路径，用临时文件写三组 `/proc/net` 行：精确 IP 与端口命中；只有一条端口命中且 inode 非 0；两条端口相同、inode 非 0、UID 不同。期望依次为 exact 返回、非 exact 返回那一条、`ErrNotFound`。`findProcessName` 在 netlink inode 与 procfs inode 不同且 netlink UID 为 0 时，结果 inode 必须仍是 netlink 的那个。

## r11-3

判定：weaken。严重度由中降为低。

`resolveProcessNameByProcSearch` 188-191 行未变：某个 pid 的 `Info` 失败就 `return "", err`。192-193 行仍是 UID 不符则 `continue`。`resolveProcessNameByUID` 244-246 行仍是 `Info` 失败则 `continue`。82 行在套接字解析成功后仍调用按 inode 的扫描。

反证：Android 上 `findProcessName` 83-88 行在这次扫描返回错误且 `uid != 0` 时改调用 `resolveProcessNameByUID`。后者会跳过消失的 pid，并返回该 UID 下某个进程名。无关进程退出因此不会让 uid 非 0 的按应用匹配必然失败。

仍成立的部分：188-190 行的提前返回还在，后面的 pid 不再按 inode 查看。`uid == 0` 时 86-87 行不回退，无关条目消失会让这次查找失败。`GOOS != android` 时没有 83-88 行的回退，一次 `Info` 失败会让整次查找失败。

问题类：错误处理。

不变量：单个 pid 目录在 `ReadDir` 之后消失，扫描继续找目标 inode。目标 inode 仍在时返回该进程。`ReadDir("/proc")` 失败仍向上返回。已经匹配到目标 inode 之后，cmdline 或 exe 读取失败仍返回错误（212-214 行、221 行）。

要动的符号：`resolveProcessNameByProcSearch` 里 `f.Info` 的失败分支。文件 `component/process/process_linux.go`。与 244-246 行一样，该 pid 的 `Info` 失败则 `continue`。

明确不改：`resolveProcessNameByUID`、Android 的 UID 回退（83-88 行）、netlink 匹配、cmdline 的切分。

本地验证：给扫描循环一个可替换的目录读取来源，或在测试里把 `Info` 失败的条目插在目标 pid 之前。目标 inode 仍能返回进程名。`uid == 0` 且目标还在时同样返回，而不是因为前面的条目失败就结束。`ReadDir` 失败的用例仍得到错误。

## r11-4

判定：hold。严重度维持中。

`dialTFO` 仍在 `dialer/tfo.go` 128-139 行。128-138 行与发现稿一致，139 行是返回的 `nil` 错误。129 行把参数 `ctx` 换成 `context.WithTimeout(context.Background(), DefaultTCPTimeout)`。`DefaultTCPTimeout` 仍是 `dialer.go` 22 行的 `5 * time.Second`。`SetReadDeadline` 仍在 91-96 行，`SetWriteDeadline` 仍在 98-103 行：`c.Conn == nil` 时返回 nil，不记录时间。`Read` 37-41 行在内部 `ctx` 结束时返回 `io.ErrUnexpectedEOF`。已取消的调用方 context 不会阻止随后的 `Dial`，因为拨号用的是新的 context。

`Close`（61-64 行）会调用 `cancel`，关掉连接可以打断这次内部 context。这不使用调用方传入的 context。

问题类：取消与截止时间。

不变量：拨号的取消和截止时间来自 `dialTFO` 收到的 context。调用方已取消时不得再拨号。调用方给出的更长截止时间要能生效，不能被固定成 5 秒。调用方没有截止时间时，可以从该 context 派生 `DefaultTCPTimeout`。`Conn` 仍为 nil 时，`SetReadDeadline` 和 `SetWriteDeadline` 记下时间，连接就绪后应用到真实连接。读截止时间到达时，`Read` 返回超时或调用方 context 的错误，不再返回 `io.ErrUnexpectedEOF`。`Close` 仍取消在途拨号。

要动的符号：`dialTFO`、`tfoConn.Read`、`tfoConn.Write`、`tfoConn.Dial`、`tfoConn.SetReadDeadline`、`tfoConn.SetWriteDeadline`。文件 `component/dialer/tfo.go`。

明确不改：`dialContext` 在 `DefaultSocketHook != nil` 时跳过 `dialTFO`（`dialer.go` 160-189 行）。本仓库 `delegate.Init` 会设置这个 hook，手机上打开 TFO 不会进入 `dialTFO`。非 TFO 的 `DialContext`、`tfo-go` 的依赖版本、`NeedAdditionalReadDeadline` 在 `Conn == nil` 时返回 true。包装层不能代替 `tfoConn` 自己记住拨号前的截止时间。

本地验证：测试里保持 `DefaultSocketHook == nil`，直接调用 `dialTFO`。已取消的 context 上第一次 `Write` 应立刻失败。约 200ms 截止时间、对端不完成握手时，失败应远早于 5 秒。返回的连接在 `Conn` 仍为 nil 时 `SetReadDeadline` 为约 200ms，`Read` 应在该时间附近返回超时，而不是约 5 秒后的 `io.ErrUnexpectedEOF`。`Close` 仍能打断尚未完成的 `Dial`。

## r11-5

判定：hold。严重度维持中。

`loadGeoSiteMatcherListSF` 与 `loadGeoSiteMatcherSF` 的 `StoreResult: true` 仍在 `geodata/utils.go` 66-67 行，`loadGeoIPMatcherSF` 仍在 156 行。GeoIP 失败分支仍在 182-187 行，只在 `!shared` 时 `Forget`。GeoSite 列表仍在 106-110 行，matcher 仍在 143-147 行，写法相同。

`common/singleflight/singleflight.go` 的 `Do`：等待方在 110 行固定返回 `shared == true`；发起方在 118 行于 `c.dups > 0` 时同样返回 `shared == true`。`doCall` 在 `StoreResult` 为真时不删除 map 项（163-164 行），错误会留下来。并发失败时发起方和等待方都跳过 `Forget`。之后同 key 的 `Do` 直接返回这次错误，直到 `ClearGeoIPCache`、`ClearGeoSiteCache` 或进程重启。单独一次失败且没有等待方时，现有 `Forget` 会删掉错误，这条路径现在是好的。

问题类：错误结果被当成可复用的缓存。

不变量：同 key 加载失败返回之后，下一次 `Do` 必须重新执行加载函数。成功结果仍留在组里，直到对应的 `Clear`。正在返回的等待方不能删掉这次失败之后新写入的成功结果。`Group.Forget`（`singleflight.go` 214-217 行）按 key 删除整项，不能让每个 `shared == true` 的返回者都调用它。

要动的符号：`LoadGeoIPMatcher`、`LoadGeoSiteMatcher` 里对 `loadGeoIPMatcherSF`、`loadGeoSiteMatcherSF`、`loadGeoSiteMatcherListSF` 的失败处理。文件 `component/geodata/utils.go` 的 106-110、143-147、182-187 行。调用点无法只撤销这一次失败的 call 时，再改 `singleflight.Group` 对错误结果的保留；仓库里 `StoreResult: true` 只有这三组。

明确不改：成功结果的缓存、`ClearGeoSiteCache`、`ClearGeoIPCache`、国家码大小写和 `!` 取反（164-168、189-191 行）。

本地验证：用 `RegisterGeoDataLoaderImplementationCreator` 和 `SetLoader` 装一个测试 loader。两个 goroutine 同时 `LoadGeoIPMatcher` 同一个 country，loader 第一次失败；失败都返回后让 loader 成功，再调用一次，必须成功。单 goroutine 失败后再调用，仍会重新加载。成功之后再调用，加载函数不得再次执行。GeoSite 的列表 key 和 matcher key 各做一遍。测试结束调用 `ClearGeoIPCache` 和 `ClearGeoSiteCache`，并把 loader 名改回去。
