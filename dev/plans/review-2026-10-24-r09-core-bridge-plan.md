---
title: "审查方案 r09 core-bridge"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 4、drop 0、weaken 1"
---

# 审查方案 r09 core-bridge

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 `core/src/main` 与 `core/build.gradle.kts`。发现稿 5 条的引用文件都在 `core/src/main` 里重新打开过。`core/build.gradle.kts` 没有被发现稿引用，这次没有从构建脚本得到条目。

hold 4、drop 0、weaken 1。

drop 无。

## r09-1

- 判定：hold。严重度仍为高。
- 现在行号：`FetchAndValid` 在 `core/src/main/golang/native/config/fetch.go` 第 169–197 行，与发现稿一致。`writeFile` 在第 104 行 `os.Rename` 到目标路径。`UnmarshalAndPatch` 在第 194 行。provider 的 `fetch` 在第 250 行，失败在第 251 行返回。同函数第 242 行的 `writeFile(ps, file)` 也在校验前写最终路径。`core/src/main/golang/native/config.go` 第 46–48 行只把错误交回 `fetch_complete`，没有恢复文件。
- 问题类：持久化更新没有提交点。强制更新或本地还没有 `config.yaml` 时，新内容先替换原文件；解析、后续 provider 或 `Parse` 失败只返回错误。坏的 `config.yaml` 留下后，第 169 行在非 force 时看到文件已存在就跳过下载，下次仍对这份坏文件做校验。
- 不变量：`FetchAndValid` 返回错误时，`path` 下的 `config.yaml` 以及调用前已经存在的 provider 文件保持调用前的字节。这次新建的文件不留在正式目录。下一次 `force == false` 不会因为这次残留的坏 `config.yaml` 跳过下载。订阅信息只在整次更新成功后上报。
- 要动的符号或文件：`fetch.go` 的 `FetchAndValid`、`fetch`、`writeFile`。第 184 行对 `config.yaml` 的 `fetch`、第 242 行 path-in-bundle 的 `writeFile`、第 250 行 provider 的 `fetch`，都要纳入同一次提交。成功顺序是：临时位置上完成 `UnmarshalAndPatch`、provider 落盘和 `Parse`，再替换正式文件，然后 `reportSubscriptionInfo`。可以用临时 profile 目录，也可以在覆盖前备份、失败时恢复。两条做法都要满足上面的不变量。
- 明确不改什么：`writeFile` 在复制、关闭或 `Rename` 失败时删除临时文件；60 秒超时、User-Agent、`http(s)` 与 `content` 的分流；`Load` 和 `Preview` 的读路径；`force == false` 且文件在这次调用前就存在时跳过下载；`validConfig` 的规则内容；`destroyProviders` 仍在 `Parse` 成功之后关闭内存中的 provider，`FetchAndValid` 继续不调用 `hub.ApplyConfig`。
- 怎么本地验证：在临时目录放入当前 `UnmarshalAndPatch` 能接受的 `config.yaml`。`force=true`，用本地 HTTP 返回非法 YAML，断言 `FetchAndValid` 返回错误且 `config.yaml` 字节不变。紧接着 `force=false`，读到的仍是调用前的正文。再测目录里本来没有 `config.yaml`、正文非法：返回错误后正式路径上没有 `config.yaml`，再次 `force=false` 会重新下载。provider 用带 `url` 的最小配置，让第二个 provider 在 `config.yaml` 已经准备好之后失败，断言正式目录回到调用前，失败的 provider 没有留在正式路径。

## r09-2

- 判定：weaken。原严重度中，降为低。
- 现在行号：`healthCheck` 在 `core/src/main/golang/native/tunnel.go` 第 68–75 行，与发现稿一致，结尾是 `C.complete(completable, nil)`。`HealthCheck` 在 `core/src/main/golang/native/tunnel/connectivity.go` 第 15–18 行和第 22–25 行直接 `return`。`wg.Done()` 在第 36 行，位于 `provider.HealthCheck()` 之后。`core/src/main/cpp/main.c` 第 195 行 `new_global`。`updateProvider` 在 `tunnel.go` 第 104 行 `C.release_object`，`healthCheck` 没有对应调用。`main.c` 第 464–468 行把 `NULL` 错误完成成 `CompletableDeferred.complete(Unit)`。
- 仍成立：组不存在或不是 `ProxyGroup` 时，导出函数仍以成功完成回调。每次 `nativeHealthCheck` 都留下一个 JNI 全局引用，`CompletableDeferred` 在调用方放开之后仍被这个引用钉住。
- 反证：`provider.HealthCheck()` 若发生未 recover 的 panic，该 goroutine 没有 `recover`。Go 会在这个 goroutine 的 defer 跑完后终止进程。`wg.Wait()` 不会在进程仍然活着的情况下一直停着，发现稿写的「这次测试会一直不结束」不成立。把 `wg.Done()` 放进 defer 也只会在进程退出前执行一次 `Done`，测试仍然不会正常结束。
- 问题类：失败被报成成功，同时每次调用泄漏 JNI 全局引用。
- 不变量：组不存在或类型不是 `ProxyGroup` 时，传给 `nativeHealthCheck` 的 `CompletableDeferred` 以异常完成。goroutine 无论成功还是失败，退出前都 `DeleteGlobalRef`。已完成的延迟测试对象可以在调用方不再持有时被回收。
- 要动的符号或文件：`tunnel.HealthCheck` 改为返回 `error`，第 15–18 行和第 22–25 行的两条路径返回错误。`healthCheck` 用 `marshalString` 把该错误交给 `C.complete`，并在 goroutine 退出时 `C.release_object`，成功路径也释放。`HealthCheckAll` 只消化新的返回值，维持现在的逐组调用。`main.c` 的 `new_global`、`complete` 和 `release_jni_object_impl` 已有，不改 JNI 方法签名。`Clash.healthCheck` 已会在 `completeExceptionally` 时失败，签名保持不动。
- 明确不改什么：不给 `provider.HealthCheck` 加 `recover`，也不把 `wg.Done()` 挪进 defer 来处理发现稿里的悬挂。`healthCheckAll` 仍然没有完成回调，不把各组结果汇总到界面。`updateProvider` 的释放顺序保持原样。
- 怎么本地验证：对空的隧道映射调用 `HealthCheck("missing")`，断言返回错误。能放进一个非 `ProxyGroup` 的代理时，再断言类型错误也返回错误。桥接侧用一个不存在的组名调用 `Clash.healthCheck`，`CompletableDeferred` 异常完成。连续调用后，第 195 行创建的全局引用要有配对的 `DeleteGlobalRef`。验收里不包含「panic 后 `wg.Wait()` 悬挂」。

## r09-3

- 判定：hold。严重度仍为中。
- 现在行号：`proxy.Start` 在 `core/src/main/golang/native/proxy/http.go` 第 13–25 行，第 17 行在 `NewWithAuthenticate` 之前调用 `stopLocked()`。`stopLocked` 在第 34–40 行；发现稿引用到第 39 行，闭合括号在第 40 行，行为相同。`startHttp` 在 `core/src/main/golang/native/proxy.go` 第 15–16 行，错误时返回 `nil`。
- 问题类：重新监听时先拆掉旧套接字。新地址绑定失败后，原来的 HTTP 入站已经 `Close`，包级 `listener` 已是 `nil`。
- 不变量：`proxy.Start` 返回错误时，调用前正在服务的 listener 仍监听原来的地址。新的 `http.Listener` 创建成功之后，才关闭旧 listener 并写入包级变量。
- 要动的符号或文件：`http.go` 的 `Start` 和 `stopLocked`。保留旧 listener，新监听成功后再替换。失败时不把失败结果写进包级 `listener`。`NewWithAuthenticate` 若在返回错误的同时给出非 nil 对象，失败路径关闭这个新对象。
- 明确不改什么：`Stop`、`stopHttp`，以及 `startHttp` 失败时返回 `nil`、`Clash.startHttp` 的可空 `String`。
- 怎么本地验证：在 `proxy` 包里先 `Start("127.0.0.1:0")` 并记下地址，再 `Start` 一个无法绑定的地址。第二次返回错误，第一次的地址仍能接受连接，包级 listener 的地址不变。随后用另一个可绑定地址成功 `Start`，旧地址不再接受连接，新地址在监听。

## r09-4

- 判定：hold。严重度仍为中。
- 现在行号：`tun.Start` 在 `core/src/main/golang/native/tun/tun.go` 第 33–38 行，第 36 行 `syscall.Close(fd)` 后返回错误。第 72–75 行 `sing_tun.New` 失败时只 `return nil, err`。`startTun` 在 `core/src/main/golang/native/tun.go` 第 81–89 行：`remote.closer` 还是 nil，`remote.close` 第 57–59 行不会关 fd，然后 `return 1`。`core/src/main/cpp/main.c` 第 128–129 行把非 0 收成 `JNI_FALSE`。`Clash.startTun` 在 `Clash.kt` 第 89–90 行抛出固定的 `startTun failed`。
- 问题类：两条失败路径对调用方传入的 fd 所有权相反，桥接又把具体错误收成同一个布尔失败。调用方只看到 `startTun failed`，无法知道 fd 是否已经关闭。
- 不变量：`tun.Start` 的每一个错误返回对传入 fd 使用同一种所有权。`Clash.startTun` 抛出的 `ClashException` 带上 `tun.Start` 的错误文本。
- 要动的符号或文件：`tun.Start`、`native/tun.go` 的 `startTun`、`main.c` 的 `nativeStartTun`、`Clash.startTun`。改之前先确认 `sing_tun.New` 返回错误时有没有关闭 `options.FileDescriptor`；这个实现不在 `core/src/main`。库不关闭 fd 时，core 在所有失败路径都不 `Close`，fd 留归调用方。库会关闭 fd 时，core 在所有失败路径都 `Close`，包括现在的 `ParsePrefix` 路径和 `sing_tun.New` 路径。`startTun` 要把 `err.Error()` 传到 JNI，`Clash.startTun` 用这段文本构造 `ClashException`。
- 明确不改什么：成功之后由 listener 持有 fd，`stopTun` 通过 `closer.Close` 关闭。gateway 与 dns 的切分规则、无法识别的栈名落到 `TunSystem`、MTU 和地址字段保持不动。失败路径上 `remote.close` 释放 callback 全局引用的行为保持不动。
- 怎么本地验证：用非法前缀调用 `tun.Start`，用 `F_GETFD` 看 fd，并断言错误文本能到达 `ClashException`。再用一个不是 TUN 设备的 fd 让 `sing_tun.New` 失败，`F_GETFD` 的结果必须和前缀失败相同。两条路径都不再只表现为 `startTun failed`。

## r09-5

- 判定：hold。严重度仍为中。
- 现在行号：`Clash.startTun` 的 `markSocket` 参数在 `Clash.kt` 第 73 行，类型是 `(Int) -> Boolean`；第 77–79 行调用后丢弃返回值。`TunInterface.markSocket` 在 `TunInterface.kt` 第 7 行无返回值。`main.c` 第 590–591 行方法签名是 `(I)V`，第 441–443 行 `CallVoidMethod`。`remoteTun.markSocket` 在 `native/tun.go` 第 37 行调用 `C.mark_socket`，没有结果。`app.MarkSocket` 在 `app/tun.go` 第 13–15 行无返回值。`delegate/init.go` 第 55–57 行在 `conn.Control` 里调用 `app.MarkSocket` 后仍返回 `Control` 的错误。`tun/tun.go` 第 62 行注释写明标记实现是 `VpnService::protect`，`AutoDetectInterface` 为 false。
- 问题类：保护结果在 Kotlin、JNI 和 Go 三层都被丢掉。标记失败时 `DefaultSocketHook` 仍允许这次拨号继续，未保护的 fd 会再被 VPN 收进 TUN。
- 不变量：标记返回 false 时，`DefaultSocketHook` 返回错误，这次拨号失败。返回 true 时，hook 不因标记失败而失败。
- 要动的符号或文件：`TunInterface.markSocket`、`Clash.startTun` 里的匿名实现、`main.c` 的 `m_tun_interface_mark_socket` 与 `call_tun_interface_mark_socket_impl`、`bridge.h` / `bridge.c` 的 `mark_socket`、`remoteTun.markSocket`、`app.MarkSocket` 与 `ApplyTunContext` 的函数类型、`delegate/init.go` 的 `DefaultSocketHook`。JNI 改为读取 boolean。`conn.Control` 的回调本身没有错误返回，要在回调外记下标记结果，标记失败时让 hook 返回错误。
- 明确不改什么：`querySocketUid` 整条路径、`ShouldBlockConnection` 的文件描述符上限、`tun/tun.go` 里 `AutoDetectInterface: false`。`VpnService.protect` 的实现不在 `core/src/main`，core 只传递它的布尔结果。
- 怎么本地验证：在 `ShouldBlockConnection` 为 false 的前提下，`ApplyTunContext` 注入返回 false 的函数，调用 `dialer.DefaultSocketHook`，断言得到错误。再注入返回 true 的函数，断言 hook 不因标记失败。`Clash.startTun` 的 lambda 要把传入的 `(Int) -> Boolean` 原样返回给 `TunInterface`。
