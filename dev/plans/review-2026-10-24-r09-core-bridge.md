---
title: "审查发现 r09 core-bridge"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条，最严重的是强制更新时先覆盖 config.yaml，校验失败不回滚，旧配置丢失"
---

# 审查发现 r09 core-bridge

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖 core/src/main 与 core/build.gradle.kts。下面 5 条都来自这次读到的源码。

## r09-1
- 严重度：高
- 症状：强制刷新订阅，或本地还没有 `config.yaml` 时，新内容先替换原文件。随后解析、provider 下载或校验失败只把错误返回给调用方，已经写上的配置和 provider 仍留在磁盘上。原来可用的配置没有备份；坏的 `config.yaml` 留下之后，下次不带 force 会跳过下载，一直卡在校验失败。
- 代码：
```169:197:core/src/main/golang/native/config/fetch.go
	if _, err := os.Stat(configPath); os.IsNotExist(err) || force {
		url, err := U.Parse(url)
		if err != nil {
			return err
		}

		bytes, _ := json.Marshal(&Status{
			Action:      "FetchConfiguration",
			Args:        []string{url.Host},
			Progress:    -1,
			MaxProgress: -1,
		})

		reportStatus(string(bytes))

		header, err := fetch(url, configPath)
		if err != nil {
			return err
		}

		reportSubscriptionInfo(header, reportStatus)
	}

	defer runtime.GC()

	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		return err
	}
```
- 为什么是问题：`fetch` 走 `writeFile`，复制成功后在 `core/src/main/golang/native/config/fetch.go` 第 104 行 `os.Rename` 到目标路径，这一步替换掉原来的 `config.yaml`。第 194 行才开始 `UnmarshalAndPatch`。provider 同样在第 250 行先 `fetch` 写入，失败在第 251 行返回。下载阶段的失败会删掉临时文件并保留旧文件，Rename 之后的失败不会恢复。
- 方向：校验和全部 provider 都成功后再替换原文件，失败时保留更新前的配置。

## r09-2
- 严重度：中
- 症状：对不存在或类型不对的策略组做延迟测试，界面收到的是成功。每次测试还泄漏一个 JNI 全局引用，对应的 `CompletableDeferred` 无法回收。某个 provider 的健康检查若 panic，这次测试会一直不结束。
- 代码：
```68:75:core/src/main/golang/native/tunnel.go
//export healthCheck
func healthCheck(completable unsafe.Pointer, name C.c_string) {
	go func(name string) {
		tunnel.HealthCheck(name)

		C.complete(completable, nil)
	}(C.GoString(name))
}
```
- 为什么是问题：`HealthCheck` 在 `core/src/main/golang/native/tunnel/connectivity.go` 第 15–18 行和第 22–25 行发现组不存在或不是 `ProxyGroup` 时直接 return，没有错误。上面的导出函数随后总是 `C.complete(completable, nil)`。`core/src/main/cpp/main.c` 第 195 行用 `new_global` 保住回调；`updateProvider` 在 `core/src/main/golang/native/tunnel.go` 第 104 行会 `C.release_object`，`healthCheck` 没有这一步。`connectivity.go` 第 33–36 行把 `wg.Done()` 放在 `provider.HealthCheck()` 之后，panic 时 `wg.Wait()` 不返回，完成回调也不会发生。
- 方向：失败用异常完成回调，函数退出时释放全局引用，并把 `wg.Done()` 放进 defer。

## r09-3
- 严重度：中
- 症状：HTTP 代理已经在监听时，再次启动若新地址绑定失败，原来的监听也被关掉，代理实际停了。
- 代码：
```13:39:core/src/main/golang/native/proxy/http.go
func Start(listen string) (listenAt string, err error) {
	lock.Lock()
	defer lock.Unlock()

	stopLocked()

	listener, err = http.NewWithAuthenticate(listen, tunnel.Tunnel, false)
	if err == nil {
		listenAt = listener.Address()
	}

	return
}

func Stop() {
	lock.Lock()
	defer lock.Unlock()

	stopLocked()
}

func stopLocked() {
	if listener != nil {
		listener.Close()
	}

	listener = nil
}
```
- 为什么是问题：`Start` 在新监听创建成功之前就 `stopLocked()`。`NewWithAuthenticate` 返回错误时，函数把错误交回 `startHttp`（`core/src/main/golang/native/proxy.go` 第 15–16 行返回 nil），旧 listener 已经 Close，并且字段被置成 nil。调用方只看到启动失败，正在用的 HTTP 入站没有了。
- 方向：新监听创建成功后再关闭旧监听。

## r09-4
- 严重度：中
- 症状：TUN 因网关前缀不合法而启动失败时，core 已经关闭传入的 fd。创建 TUN 设备失败时，本函数又不关闭这个 fd。两条失败在 Kotlin 里都变成同一个 `startTun failed`，调用方无法判断还要不要关 fd。
- 代码：
```33:38:core/src/main/golang/native/tun/tun.go
		prefix, err := netip.ParsePrefix(gatewayStr)
		if err != nil {
			log.Errorln("TUN:", err)
			_ = syscall.Close(fd)
			return nil, err
		}
```
- 为什么是问题：第 36 行在返回错误前关闭了调用方传入的 fd。第 72–75 行 `sing_tun.New` 失败时只 `return nil, err`，这里没有 Close。`core/src/main/golang/native/tun.go` 第 81–89 行收到错误后 `remote.close()`，此时 closer 还是 nil，也不会关 fd。`core/src/main/cpp/main.c` 第 128–129 行把两种失败都收成 `JNI_FALSE`，`Clash.startTun` 在 `core/src/main/java/com/github/kr328/clash/core/Clash.kt` 第 89–90 行抛出固定的 `startTun failed`。调用方若在失败时再关一次，前缀错误这条路径会二次关闭，fd 号复用后会关掉无关描述符；若失败时不再关，设备创建失败这条路径会把 VPN fd 留在进程里。
- 方向：失败路径对 fd 使用同一种所有权，并把具体错误传回调用方。

## r09-5
- 严重度：中
- 症状：出站套接字标记失败时，连接仍然发出。保护没有生效的 fd 会继续被 VPN 收进去。
- 代码：
```73:79:core/src/main/java/com/github/kr328/clash/core/Clash.kt
        markSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int
    ) {
        val started = Bridge.nativeStartTun(fd, stack, gateway, portal, dns, object : TunInterface {
            override fun markSocket(fd: Int) {
                markSocket(fd)
            }
```
- 为什么是问题：`markSocket` 的返回值表示标记是否成功，这里调用后直接丢掉。`TunInterface.markSocket` 是 `Unit`，`core/src/main/golang/native/tun.go` 第 37 行的 `C.mark_socket` 也没有结果。`core/src/main/golang/native/delegate/init.go` 第 55–57 行在 `conn.Control` 里调用 `app.MarkSocket` 后照样让拨号继续。`core/src/main/golang/native/tun/tun.go` 第 62 行写明标记实现是 `VpnService::protect`。protect 失败的 fd 仍属于 VPN，这条出站连接会被再次导入 TUN。
- 方向：标记失败时中止这次拨号，并把失败传回 dialer hook。
