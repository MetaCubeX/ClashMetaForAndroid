---
title: "审查方案 r11 clash-listener"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 5、drop 0、weaken 0"
---

# 审查方案 r11 clash-listener

核对范围只限 `core/src/foss/golang/clash/listener`。五条引用处的行为都还在，结论都成立。hold 5、drop 0、weaken 0。

## r11-1

**问题类：** 同一条 HTTP keep-alive 连接上，一次鉴权成功后 `trusted` 不再回到 false，后续请求不再受用户名密码约束。

**不变量：** 每一轮请求是否放行，只看这一次 `authenticate` 的返回值。`resp == nil` 才进入 CONNECT 劫持或 `client.Do`。上一轮成功不得把本轮的 407/403 盖掉。未配置鉴权（`Authenticator()` 为 nil）时，`authenticate` 仍返回 nil，连接保持免鉴权。

**要动的符号或文件：** `http/proxy.go` 的 `HandleConn`。读到的位置：第 45–47 行初始化 `trusted`，第 61–67 行只在 `resp == nil` 时把 `trusted` 写成 true，第 67–129 行在 `trusted` 为真时转发或劫持，第 131–147 行在 `Proxy-Connection: keep-alive` 且 `ContentLength` 为 0 时不把失败响应标成 `Close`，第 153–168 行 `authenticate` 在无凭证时返回 407、密码错误时返回 403、成功时返回 nil。把「是否放行」改成每轮取本次 `resp == nil`，删掉跨请求粘住的 true。

**明确不改什么：** 不改 `authenticate` 的 407/403 语义，不把 407 改成强制拆连接。不改 CONNECT 在本次鉴权成功后的劫持，不改 `lastUser` 切换时关闭 idle client 的逻辑，不改 hop-by-hop 头处理。

**怎么本地验证：** 仓库里没有覆盖这条连接的测试。应新增断言：同一连接上，带对的 `Proxy-Authorization` 且带 `Proxy-Connection: keep-alive` 的普通请求被转发；紧接着的无凭证请求得到 407、错误凭证得到 403，这两种请求都不进入 tunnel，其中方法为 CONNECT 的也不劫持。第一条鉴权失败时，第二条带对凭证的请求仍能转发。`Authenticator()` 为 nil 时，不带凭证的请求仍被转发。

## r11-2

**问题类：** SOCKS 入站配了用户之后，TCP 握手会验密码，UDP 入站不看 `AuthStore`，任意来源的 SOCKS5 UDP 包都会进隧道。

**不变量：** `Authenticator()` 非空时，只有仍存活的、已通过该鉴权的 UDP ASSOCIATE 所登记的来源 IP，才能把 UDP 包送进 `HandleUDPPacket`。登记用 ASSOCIATE 请求里的目标地址；目标是全零地址时，用来源 TCP 连接的对端 IP。UDP 源端口不必等于 TCP 源端口。控制连接结束时撤销登记。未登记的包归还 buffer 并丢弃。`Authenticator()` 为 nil 时不新增这道限制。

**要动的符号或文件：**

- `socks/tcp.go` 的 `HandleSocks5`（第 180–194 行）：`CmdUDPAssociate` 在握手成功后只丢弃 TCP 字节，没有登记对端地址。
- `socks/udp.go` 的 `NewUDPWithConfig`（第 42–79 行）和 `handleSocksUDP`（第 82–98 行）：`config.AuthStore` 没有被读取；解包成功就 `HandleUDPPacket`。
- 同一入站的 TCP 与 UDP 要共用一张关联表。接线处：`inbound/socks.go` 的 `Listen`（第 100–110 行）、`inbound/mixed.go` 的 `Listen`（第 80–90 行）、`listener.go` 的 `ReCreateSocks`（第 183–195 行）和 `ReCreateMixed`（第 484–492 行）。`parse.go` 第 24、52 行 socks/mixed 的 `UDP` 默认 true，用户列表会走到这两条 `Listen`。

**明确不改什么：** 不改 SOCKS5 TCP 的 `ServerHandshake` 验密规则，不改 UDP 包编解码，不改未配置用户时的放行，不改 SOCKS4。不按「UDP 源端口等于 TCP 源端口」过滤。

**怎么本地验证：** 仓库里没有 SOCKS 入站鉴权测试。应新增断言：配了用户时，未做 ASSOCIATE 的 UDP 包不进 tunnel；用错误密码做 ASSOCIATE 失败后，该来源的 UDP 包不进 tunnel；用正确密码完成 ASSOCIATE 后，同一 IP、不同 UDP 源端口的包进 tunnel；关掉这条 TCP 控制连接后，该 IP 的后续 UDP 包不再进 tunnel。未配置用户时，现有不登记也放行的行为保持不变。

## r11-3

**问题类：** 入站热更新时，配置变化会先关掉旧监听；新 `Listen` 失败后，表里仍留着已关闭对象，已 bind 的新套接字也没有句柄再关。

**不变量：** `inboundListeners` 里每个名字要么没有条目，要么对应一次已经成功且仍在听的 `Listen`。`Listen` 返回错误时，这个新对象上已经 append 的 TCP/UDP 监听都被 `Close`，名字不写回表。下一次用同一份配置再 `Patch` 会重新 `Listen`。配置相等且旧对象仍在听时，继续跳过重建。

**要动的符号或文件：** `listener.go` 的 `PatchInboundListeners`（第 627–644 行）。配置不相等时，`Close` 旧对象后立刻 `delete`。`newListener.Listen` 失败时调用 `newListener.Close()`，并且不要写入表。回收依赖现成的 `Close`：`inbound/mixed.go` 第 98–116 行、`inbound/http.go` 第 89–101 行、`inbound/socks.go` 第 56–73 行，它们会关掉已 append 的监听。多地址来自 `inbound/base.go` 的 `RawAddress`（第 65–75 行）。`mixed.NewWithConfig` 在返回成功前已经在第 122 行开始 `Accept`（`mixed/mixed.go`）。

**明确不改什么：** 不改 `dropOld` 删除「新 map 里没有的名字」那段（第 646–653 行）。不改配置相等时的 `continue`。不改各协议 `Listen` 的成功路径。构造函数在 `append` 之前就返回错误、调用方拿不到对象的情况归 r11-5，不在这里加新条目。

**怎么本地验证：** 仓库里没有 `PatchInboundListeners` 的测试。应新增断言：旧监听已在听，换成另一份配置且 `Listen` 失败时，旧端口不再 Accept，新对象里已经 bind 的地址也被关掉，表里不再留这个名字；紧接着再 Patch 回原来那份配置时会重新 `Listen` 并 Accept。`Listen` 成功且配置未变时，第二次 Patch 不重新 bind。

## r11-4

**问题类：** Redir 或 TProxy 的 UDP 启动失败后，只要 TCP 还停在同一地址，再次创建会在 TCP 地址相等处直接返回，UDP 不再尝试。

**不变量：** TCP 与 UDP 各自判断是否已经在听。TCP 地址未变只跳过 TCP 的重建。`redirUDPListener` / `tproxyUDPListener` 为 nil 时，即使 TCP 地址相同也要再调用 `NewUDP`。两边都已在同一地址上时才整段返回。端口为 0 时仍然关闭并返回。

**要动的符号或文件：** `listener.go` 的 `ReCreateRedir`（第 213–241 行）和 `ReCreateTProxy`（第 410–438 行）。这两处都是 TCP 的 `RawAddress() == addr` 时直接 `return`，UDP 失败只打日志并把对应变量留成 nil。对齐本文件里已经分开判断的 `ReCreateSocks`（第 154–191 行）和 `ReCreateMixed`（第 456–492 行）：TCP 已在听不阻断 UDP 的补建。

**明确不改什么：** 不改 `inbound/tproxy.go` 的 `Listen` 和 `inbound/redir.go` 的 `Listen`。不改 `tproxy.New` / `redir.New` 的 TCP 成功路径。UDP `NewUDP` 内部失败时泄漏已 bind 套接字的问题归 r11-5。

**怎么本地验证：** 仓库里没有 `ReCreateRedir` / `ReCreateTProxy` 的测试。应新增断言：第一次 TCP 成功、UDP 失败后，全局 UDP 监听为 nil、TCP 仍在原地址；用同一端口再调用一次时会再次执行 `NewUDP`。两次都成功且地址不变时，第二次不再重建 TCP，也不重复 bind UDP。

## r11-5

**问题类：** 多地址监听或 bind 之后的检查失败时，构造函数返回 nil 和 error，已经 bind 的套接字和已经启动的 Accept / UDP 读循环没有 `Close`。

**不变量：** 这些构造函数返回非 nil error 时，本次调用里已经 `Listen` 或 `ListenPacket` 成功的套接字全部 `Close`，已经启动的循环跟着退出。尚未 append 的当前套接字在 return 前单独关掉。调用方仍然只在 `err == nil` 时保存返回值。

**要动的符号或文件：**

- `sing_shadowsocks/server.go` 的 `New`：第 143–154 行 UDP 已 append 并在第 164 行启动读循环，第 205–208 行同一地址的 TCP `Listen` 失败时 `return nil, err`。`Close` 在第 236–251 行，能关掉已 append 的 TCP 和 UDP。
- `anytls/server.go` 的 `New`：第 149–163 行。后续地址 `Listen` 失败，或无证书且未开 `AllowInsecure` 时在 append 之前 return。后者当前套接字还不在 `sl.listeners` 里。
- `trojan/server.go` 的 `New`：第 197–218 行，与 AnyTLS 相同；无证书且未开 `AllowInsecure`、也没有 reality/ss 时在 append 之前 return。
- `tproxy/udp.go` 的 `NewUDP`：第 44–64 行。`ListenPacket` 成功后，`SyscallConn` 或 `setsockopt` 失败直接 `return nil, err`，`packetConn` 未关，读循环尚未启动。

错误路径在 return 前关掉上述套接字。`sing_shadowsocks.New` 在默认 additions 下有 defer 把 `sl` 赋给包级 `_listener`（第 51–56 行）；失败时先 `Close`，再让这个包级变量不要指着仍在 Accept 的对象。

**明确不改什么：** 不改成功返回后的握手、加密和用户校验。不改调用方「`err != nil` 就丢弃返回值」的写法（`ReCreateShadowSocks` 第 287–290 行是这种写法）。不改 `tproxy.New` 的 TCP 路径。不改各 `TestInbound*` 已经覆盖的成功握手。

**怎么本地验证：** 现有 `TestInboundShadowSocks_Basic`、`TestInboundAnyTLS_TLS`、`TestInboundTrojan_TLS` 只覆盖启动成功。应新增断言：多地址里前面的地址已经开始 Accept 或 UDP 读、后面的地址失败时，函数返回错误，前面的端口可以立刻再次 bind。AnyTLS 与 Trojan 在无证书且未开 allow-insecure 时，`Listen` 成功后的那个套接字在返回错误后不再占用地址。`NewUDP` 在 `setsockopt` 失败后，同一地址可以立刻再次 `ListenPacket`。
