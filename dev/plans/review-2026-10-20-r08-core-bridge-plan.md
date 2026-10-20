---
title: "审查方案 r08 core-bridge"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 5、drop 0、weaken 0"
---

# 审查方案 r08 core-bridge

## r08-1

问题类：持久覆写先截断旧文件再写，失败不外传，读写都不关句柄。

不变量：已有的 `override.json` 只有在新内容全部写完并成功关闭后才被替换。`ReadOverride` 与 `WriteOverride` 在每次返回前关闭本次打开的文件。文件打不开时读取仍得到空覆写 `{}`。读出失败时不得把 `{}` 交给加载逻辑。持久槽 JSON 解码失败时 `patchOverride` 返回该错误，这份 `RawConfig` 不得继续进入后续处理器。持久槽写入失败时 `Clash.patchOverride` 的调用方能得到该错误。

要动的符号或文件：

- `WriteOverride`（`core/src/main/golang/native/config/override.go` 47–58）：写同目录临时文件，写满并成功关闭后再替换 `override.json`；失败返回错误，原文件保持不动。
- `ReadOverride`（同文件 26–39）：成功与失败都关闭文件；仅打开失败返回 `{}`；读出失败把错误交给调用方。
- `patchOverride`（`core/src/main/golang/native/config/process.go` 33–41）：持久槽读出或解码失败时返回该错误。
- `writeOverride`（`core/src/main/golang/native/config.go` 57–62）、`Java_com_github_kr328_clash_core_bridge_Bridge_nativeWriteOverride`（`core/src/main/cpp/main.c` 356–365）、`Bridge.nativeWriteOverride`（`core/src/main/java/com/github/kr328/clash/core/bridge/Bridge.kt` 47）、`Clash.patchOverride`（`core/src/main/java/com/github/kr328/clash/core/Clash.kt` 200–208）：把持久槽写入错误传到 Kotlin。

明确不改什么：会话槽仍只改内存里的 `sessionOverride`。`ClearOverride` 仍只删除持久文件或重置会话槽。`ShouldBlockConnection` 与 `maxFdCount` 的计算保持现状。拨号钩子仍按 `ShouldBlockConnection` 的结果拒绝新连接。

怎么本地验证：`core/src/main` 内没有覆盖这段行为的测试。应新增断言：写入中途失败后原 `override.json` 字节不变；`ReadOverride` 与 `WriteOverride` 返回后该次打开的描述符已关闭；持久槽写入失败能被 `Clash.patchOverride` 的调用方观察到；持久槽解码失败时该 `RawConfig` 不会被继续处理和生效。

## r08-2

问题类：强制更新在拷贝失败时删掉原来的 `config.yaml`。

不变量：`writeFile` 的目标在调用前已经存在时，拷贝或关闭失败后，该路径的存在性和内容与调用前相同，并且错误向上返回。

要动的符号或文件：

- `writeFile`（`core/src/main/golang/native/config/fetch.go` 84–99）：先写同目录临时文件，`io.Copy` 成功并关闭后再替换目标；失败时删除临时文件并返回错误。
- `fetch`（同文件 81）继续把 `writeFile` 的错误返回给 `FetchAndValid`（同文件 174–176）。

明确不改什么：`FetchAndValid` 里 `force` 决定是否重新拉取 `config.yaml` 的判断（同文件 159）保持现状。订阅头解析与 `reportStatus` 保持现状。provider 文件已存在时是否跳过留给 r08-4。

怎么本地验证：`core/src/main` 内没有这项测试。应新增断言：目标文件已有内容且 reader 返回错误时，目标路径字节与调用前一致，函数返回该错误。

## r08-3

问题类：选择器 `Set` 失败时仍向界面返回成功，并断开该组连接。

不变量：`Set` 返回错误时 `PatchSelector` 返回 false，不关闭该组现有连接，也不记录切换成功。

要动的符号或文件：

- `PatchSelector`（`core/src/main/golang/native/tunnel/proxies.go` 157–165）：`Set` 出错时立刻返回 false。
- `patchSelector`（`core/src/main/golang/native/tunnel.go` 82–92）继续按该布尔值返回 1 或 0。

明确不改什么：选择器不存在、适配器不是 `ProxyGroup`、或未实现 `SelectAble` 时已经返回 false 的路径保持现状。`Set` 成功之后仍调用 `closeConnByGroup`（`core/src/main/golang/native/tunnel/conn.go` 24–34）。

怎么本地验证：`core/src/main` 内没有这项测试。应新增断言：`Set` 返回错误时 `PatchSelector` 为 false，且该组链上的连接未被关闭。

## r08-4

问题类：强制更新不重下已有 provider，provider 首次下载失败也不会变成这次校验的错误。

不变量：`force` 为真时，已存在的 provider 文件也重新下载。URL 解析失败或下载失败时，`FetchAndValid` 返回该错误。重下失败时原 provider 文件保持调用前的字节，替换方式与 r08-2 的 `writeFile` 相同。

要动的符号或文件：

- `FetchAndValid` 的 provider 循环（`core/src/main/golang/native/config/fetch.go` 213–238）：`force` 为真时，`Stat` 成功也继续下载；`U.Parse` 与 `fetch` 的错误传出循环，成为 `FetchAndValid` 的返回错误。
- `forEachProviders`（`core/src/main/golang/native/config/provider.go` 14–28）：回调要能把第一条错误交回 `FetchAndValid`。

明确不改什么：`force` 为假且 provider 文件已存在时仍跳过下载。`UpdateProvider`（`core/src/main/golang/native/tunnel/providers.go` 68–92）继续把错误返回给调用方。`path-in-bundle` 仍先于远程下载。`config.yaml` 那一处 `force` 判断保持现状。

怎么本地验证：`core/src/main` 内没有这项测试。应新增断言：`force` 为真且 provider 文件已存在时仍下载，成功后再替换；URL 非法或下载失败时 `FetchAndValid` 返回对应错误，已有 provider 文件字节不变。

## r08-5

问题类：TUN 启动失败没有回到 Kotlin；失败若发生在监听器接管 fd 之前，fd 不关闭，重启还会先拆掉旧隧道。

不变量：`tun.Start` 失败时 `Clash.startTun` 的调用方得到失败。失败发生在把 fd 交给 `sing_tun.New` 之前时，该 fd 已关闭。调用前已在运行的隧道保持运行，其 `ApplyTunContext` 回调仍是原来的那一组。

要动的符号或文件：

- `tun.Start`（`core/src/main/golang/native/tun/tun.go` 32–36，以及到达 70 行之前的其它错误返回）：尚未把 `FileDescriptor` 交给 `sing_tun.New` 就返回错误时关闭传入的 fd。
- `startTun`（`core/src/main/golang/native/tun.go` 66–98）：新隧道成功后再替换 `rTun`；失败时释放本次回调、恢复原先的 `ApplyTunContext`，并返回非 0。
- `Java_com_github_kr328_clash_core_bridge_Bridge_nativeStartTun`（`core/src/main/cpp/main.c` 112–129）、`Bridge.nativeStartTun`（`core/src/main/java/com/github/kr328/clash/core/bridge/Bridge.kt` 22）、`Clash.startTun`（`core/src/main/java/com/github/kr328/clash/core/Clash.kt` 67–89）：把该失败传到 Kotlin 调用方。

明确不改什么：`stopTun` 仍按调用关闭当前隧道。启动成功后仍保存 `closer` 并替换 `rTun`。无法识别的 stack 仍回退 `TunSystem`。`sing_tun.New` 已经收到 `FileDescriptor` 之后，fd 归监听器；该失败路径上 `tun.Start` 不再关闭这个 fd。

怎么本地验证：`core/src/main` 内没有这项测试。应新增断言：网关前缀无法解析时 `startTun` 返回非 0，传入 fd 已关闭，调用前的 `rTun` 仍在；`Clash.startTun` 的调用方能观察到这次失败。
