---
title: "审查方案 r16 clash-control"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 4、drop 0、weaken 1"
---

# 审查方案 r16 clash-control

核对范围是规则、DNS、配置、hub 路由、tunnel。r16-1、r16-2、r16-3、r16-4 维持原严重度。r16-5 降为低。没有 drop。

## r16-1（hold，高）

- 问题类：配了 `secret` 之后，Unix 套接字和 Windows 命名管道上的外部控制仍不校验口令，Unix 套接字还是 `0666`。
- 不变量：`Secret` 非空时，Unix 与命名管道的 HTTP 路由和 TCP 一样经过 `authentication(secret)`。Unix 套接字文件模式为当前用户可读写（`0600`）。`Secret` 为空时，这几条监听仍不挂鉴权。
- 要动的符号或文件：`hub/route/server.go` 的 `startUnix` 与 `startPipe`。实际读到：第 286 行 `os.Chmod(addr, 0o666)`；第 290 行 Unix 的 `router(cfg.IsDebug, "", ...)`；第 321 行管道的 `router(cfg.IsDebug, "", ...)`。两处第二个参数改为 `cfg.Secret`。`Chmod` 的模式改为 `0o600`。TCP 与 TLS 已传入 `cfg.Secret`（第 181、242 行），用来对照。
- 明确不改什么：不改 `authentication` 的 Bearer 与 websocket `token` 比较。不改 TCP、TLS 的监听和 secret 传递。不改命名管道的 `\\.\pipe\` 前缀检查。不改 `/debug` 的挂载位置（归 r16-2）。不改 `Chmod` 失败仍被忽略这一点。
- 怎么本地验证：`hub` 下没有测试。应新增断言意图：`Secret` 非空时，与 `startUnix` / `startPipe` 相同的 `router` 调用会拒绝没有 `Authorization` 的请求（401），正确的 `Bearer` 能进入与 TCP 控制器相同的受保护路由；Unix 路径上的 `Chmod` 模式是 `0600`。`Secret` 为空时，Unix 与管道仍不要求 `Authorization`。

## r16-2（hold，高）

- 问题类：日志级别为 debug 时，`/debug/pprof` 和 `PUT /debug/gc` 挂在鉴权分组前面，secret 盖不住。
- 不变量：`IsDebug` 为真且 `secret` 非空时，`/debug` 与 `/configs` 等一样要求 `authentication`。`secret` 为空时 `/debug` 仍可匿名。`IsDebug` 为假时不挂 `/debug`。
- 要动的符号或文件：`hub/route/server.go` 的 `router`。实际读到：第 108–118 行在 `Group` 之前 `Mount("/debug")`，其中含 `PUT /gc` 和 `middleware.Profiler`；第 119–122 行才在 `secret != ""` 时 `Use(authentication(secret))`。把这段 `Mount` 移入该 `Group`，并放在 `Use(authentication(secret))` 之后，仍由 `isDebug` 决定是否挂上。`IsDebug` 仍由 `hub/hub.go` 第 77 行 `cfg.General.LogLevel == log.DEBUG` 赋值。
- 明确不改什么：不改 `debug.FreeOSMemory` 和 `middleware.Profiler` 的处理内容。不在 `secret` 为空时额外加鉴权。不改第 146–157 行 `/ui` 与 external DoH 的挂载位置。不改 TCP、Unix、管道把 `IsDebug` 传进 `router` 的方式。
- 怎么本地验证：`hub` 下没有测试。应新增断言意图：`isDebug` 为真且 `secret` 非空时，不带凭证的 `PUT /debug/gc` 和 Profiler 路径为 401，带正确 `Bearer` 则不是 401。`isDebug` 为假时这些路径不存在。`secret` 为空且 `isDebug` 为真时，不带凭证仍能进入 `PUT /debug/gc`。

## r16-3（hold，中）

- 问题类：A 失败而 AAAA 晚于等待窗口时，`LookupIP` 返回空地址且错误为 nil。
- 不变量：A 查询得到至少一个地址且计时器先到时，仍返回这些 A 地址、错误为 nil，不等待更晚的 AAAA。A 查询失败时不得在计时器分支把错误换成 nil；继续等到 AAAA 通道送来地址，或通道关闭。得到地址则返回这些地址、错误为 nil。通道关闭后仍然没有地址，返回 `resolver.ErrIPNotFound`。计时器与已关闭通道同时就绪时，也不得把「没有地址」报成成功。
- 要动的符号或文件：`dns/resolver.go` 的 `(*Resolver).LookupIP`（第 77–107 行）。A 的失败形态在同文件 `lookupIP` 第 363–370 行：传输错误或零条地址时返回空切片和非 nil 错误。默认窗口是 `config/config.go` 第 510 行 `IPv6Timeout: 100`，`NewResolver` 在 `resolver.go` 第 530、601 行把它乘成毫秒。改 A 失败时的返回，不改这个默认毫秒数。
- 明确不改什么：不改 `LookupIPPrimaryIPv4`（第 53–74 行）。不改 `LookupIPv4` / `LookupIPv6`。不改 A 成功时「计时器先到就返回 A」的行为。不改 `ipv6-timeout` 的默认值 100。
- 怎么本地验证：`dns` 下没有测试。应新增断言意图：A 失败且 AAAA 在 `ipv6Timeout` 之后才返回地址时，结果是这些 AAAA 地址且错误为 nil。A 与 AAAA 都失败时，返回 `ErrIPNotFound`，不是 nil 错误配上空地址列表。A 成功且 AAAA 晚于窗口时，仍只返回 A 地址且错误为 nil。

## r16-4（hold，中）

- 问题类：fake-ip 映射已经不在时，UDP 在嗅探之前被丢掉；同样开启嗅探时 TCP 还能从载荷找回域名。
- 不变量：嗅探关闭时，`preHandleMetadata` 因 fake-ip 记录缺失而失败，UDP 仍 `Drop` 并返回，不建 NAT。嗅探开启时，这次失败不丢包；先装上 `UDPSniff` 并 `DoSniff`，再对更新后的元数据做 `preHandleMetadata`。这次失败则中止拨号。嗅探把域名写回后，预检通过则继续 `resolveMetadata`。TCP 仍按现有顺序：预检失败只做标记，嗅探成功再把标记清掉。
- 要动的符号或文件：`tunnel/tunnel.go` 的 `handleUDPConn`。实际读到：fake-ip 缺失只在 `preHandleMetadata` 第 306–307 行返回错误；UDP 在第 434–438 行对克隆预检失败即 `Drop` 并返回；`UDPSniff` 在第 443–446 行，位于这次返回之后；`dial` 在第 453 行 `DoSniff`，第 458 行 `_ = preHandleMetadata(metadata)` 把错误丢掉。嗅探开启（`sniffingEnable && snifferDispatcher.Enable()`）且第 434 行预检失败时，不要 `Drop`。照常 `GetOrCreate`，并用 `UDPSniff` 包装 sender。仅在这次预检已经失败时，第 458 行改为使用 `preHandleMetadata` 的错误中止拨号。预检已经成功时，保留第 458 行忽略错误。未包装的 `packetSender.DoSniff` 在 `tunnel/connection.go` 第 152 行返回 nil，真正的嗅探依赖上述包装。
- 明确不改什么：不改 `handleTCPConn` 第 519–540 行的 TCP 嗅探恢复。不改 `preHandleMetadata` 对 fake-ip 缺失仍返回错误。不改嗅探关闭时的 UDP `Drop`。不改 `component/sniffer` 的嗅探实现。
- 怎么本地验证：`tunnel` 下没有测试。应新增断言意图：UDP、目标为 fake-ip、映射不存在、嗅探开启、首包能嗅出域名时，不在 `DoSniff` 之前 `Drop`，嗅探之后的 `preHandleMetadata` 得到该域名并继续解析。同一条件下嗅探结果仍无域名时，拨号因 `preHandleMetadata` 失败而中止。嗅探关闭时仍然 `Drop`，且不创建 NAT sender。TCP 在预检失败后仍会尝试嗅探，嗅探成功则不再放弃连接。

## r16-5（weaken，低）

- 问题类：UID 规则在 uid 非 0 但不在范围内时，仍打「could not get uid」。
- 不变量：非 0 的 uid 只按范围比较。在范围内则命中且不告警；不在范围内则不命中且不打「could not get uid」。uid 为 0 时保持不命中，并保留这句告警。
- 要动的符号或文件：`rules/common/uid.go` 的 `(*Uid).Match`（第 44–54 行）。`metadata.Uid != 0` 且 `u.uids.Check` 为假时，直接 `return false, ""`，不要落到第 53 行的 `Warnln`。
- 明确不改什么：不改 `tunnel/tunnel.go` 第 354–375 行的进程查找，包括 CMFA 分支只写 `metadata.Process`。不把 uid 0 放进范围比较。不改 `NewUid` 的平台限制和范围解析。
- 怎么本地验证：`rules/logic_test` 的 `TestAND`、`TestNOT`、`TestOR` 和 `rules/provider` 的 `TestDomainStrategyDumpMrs` 都不覆盖 UID。应新增断言意图：uid 为 1000 且范围含 1000 时命中，且没有「could not get uid」；uid 为 1000 且范围不含 1000 时不命中，且没有这句告警；uid 为 0 时不命中，告警仍在。`helper.FindProcess` 非 nil 时仍被调用。
- 反证：`Match` 在 `metadata.Uid != 0` 且 `uids.Check` 为真时返回 true 和 adapter（`uid.go` 第 48–51 行）。CMFA 分支只在 `FindPackageName` 成功时把包名写入 `metadata.Process`（`tunnel.go` 第 368–375 行），没有给 `metadata.Uid` 赋值，也没有把它写成 0。`Metadata.SourceDetail`（`constant/metadata.go` 第 240–245 行）同样只在 `Uid != 0` 时把 uid 写进来源描述。控制面把 0 当作没有 uid。告警只在 `Match` 最终不命中时出现；命中范围的非 0 uid 不会打这句。严重度改为低。
