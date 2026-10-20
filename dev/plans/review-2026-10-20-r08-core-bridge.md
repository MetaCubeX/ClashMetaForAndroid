---
title: "审查发现 r08 core-bridge"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "覆写落盘和订阅下载会丢掉已有文件，节点切换、provider 更新和 TUN 启动会把失败当成成功。"
---

# 审查发现 r08 core-bridge

只读 `core/src/main` 与 `core/build.gradle.kts` 的活码。下面 5 条都出在桥的状态或错误路径上。

## r08-1

严重度：高

症状：持久覆写一旦打开成功，旧的 `override.json` 已被截断；写入失败时调用方看不到错误。成功打开的读取和保存都不关闭文件，句柄泄漏累积后新连接会被拒绝。

```46:58:core/src/main/golang/native/config/override.go
func WriteOverride(slot OverrideSlot, content string) {
	switch slot {
	case OverrideSlotPersist:
		file, err := os.OpenFile(overridePersistPath(), os.O_WRONLY|os.O_TRUNC|os.O_CREATE, 0600)
		if err != nil {
			return
		}

		_, err = file.Write([]byte(content))
	case OverrideSlotSession:
		sessionOverride = content
	}
}
```

```26:39:core/src/main/golang/native/config/override.go
func ReadOverride(slot OverrideSlot) string {
	switch slot {
	case OverrideSlotPersist:
		file, err := os.OpenFile(overridePersistPath(), os.O_RDONLY, 0600)
		if err != nil {
			return defaultPersistOverride
		}

		buf, err := io.ReadAll(file)
		if err != nil {
			return defaultPersistOverride
		}

		return string(buf)
```

```33:36:core/src/main/golang/native/config/process.go
func patchOverride(cfg *config.RawConfig, _ string) error {
	if err := json.NewDecoder(strings.NewReader(ReadOverride(OverrideSlotPersist))).Decode(cfg); err != nil {
		log.Warnln("Apply persist override: %s", err.Error())
	}
```

```29:41:core/src/main/golang/native/platform/limit.go
func ShouldBlockConnection() bool {
	fd, err := syscall.Dup(nullFd)
	if err != nil {
		return true
	}

	_ = syscall.Close(fd)

	if fd > maxFdCount {
		return true
	}

	return false
```

```50:53:core/src/main/golang/native/delegate/init.go
	dialer.DefaultSocketHook = func(network, address string, conn syscall.RawConn) error {
		if platform.ShouldBlockConnection() {
			return errBlocked
		}
```

为什么是问题：`O_TRUNC` 在 `Write` 之前就把旧文件清空。写入长度和错误都被丢掉，短写或磁盘满时留下空文件或残缺 JSON，下一次加载套不上覆写，或把残缺内容解码进配置。`Clash.patchOverride` 没有错误返回，调用方分不清有没有写上。读路径在 `ReadAll` 失败时同样不关文件，并返回 `{}`，这次加载会跳过磁盘上仍在的覆写。成功路径也不 `Close`。加载配置时 `patchOverride` 每次都会读持久槽，`queryOverride` 每次也会读，句柄只增不减。`maxFdCount` 是 `RLIMIT_NOFILE` 的四分之三；泄漏把低号 fd 占满后，`Dup` 得到的号超过它，拨号钩子里的 `ShouldBlockConnection` 直接阻断新连接。

方向：写到临时文件并确认写完整后再替换 `override.json`，读写都关闭句柄，失败时把错误返回给 Kotlin。

## r08-2

严重度：高

症状：强制更新订阅若在写盘中途失败，原来可用的 `config.yaml` 会被删掉。

```84:99:core/src/main/golang/native/config/fetch.go
func writeFile(file string, reader io.Reader) error {
	_ = os.MkdirAll(P.Dir(file), 0700)

	f, err := os.OpenFile(file, os.O_WRONLY|os.O_TRUNC|os.O_CREATE, 0600)
	if err != nil {
		return err
	}

	defer f.Close()

	_, err = io.Copy(f, reader)
	if err != nil {
		_ = os.Remove(file)
	}

	return err
}
```

```157:177:core/src/main/golang/native/config/fetch.go
	configPath := P.Join(path, "config.yaml")

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
```

为什么是问题：`force` 为真时，已有的 `config.yaml` 会作为 `fetch` 的目标。`writeFile` 先把该文件截断，拷贝失败再删除。下载超时、连接中断或读到一半出错时，更新前那份配置已经不在。错误会回到界面，但本地配置回不去，随后的加载会因为缺少 `config.yaml` 失败。

方向：先写入同目录临时文件，拷贝成功并关闭后再替换 `config.yaml`，失败时保留原文件。

## r08-3

严重度：中

症状：切换节点失败时，桥仍向界面返回成功，并断开这个策略组上的现有连接。

```157:165:core/src/main/golang/native/tunnel/proxies.go
	if err := s.Set(name); err != nil {
		log.Warnln("Patch selector `%s`: %s", selector, err.Error())
	}

	log.Infoln("Patch selector %s -> %s", selector, name)

	closeConnByGroup(selector)

	return true
```

```82:92:core/src/main/golang/native/tunnel.go
//export patchSelector
func patchSelector(selector, name C.c_string) C.int {
	s := C.GoString(selector)
	n := C.GoString(name)

	if tunnel.PatchSelector(s, n) {
		return 1
	}

	return 0
}
```

为什么是问题：选择器不存在或类型不对时会返回 false。一旦它实现了 `SelectAble`，`Set` 失败只写日志，函数仍然返回 true。节点名无效，或列表刚刷新后目标已经不在组里时，Kotlin 的 `patchSelector` 得到 true，界面会把这次切换当成完成。与此同时 `closeConnByGroup` 会关掉该组当前连接，选中项却没有改掉。

方向：`Set` 返回错误时返回 false，并且不要关闭该组连接。

## r08-4

严重度：中

症状：强制更新不会刷新已经下载过的 provider；provider 第一次下载失败时，失败也不会成为这次校验的错误。

```159:159:core/src/main/golang/native/config/fetch.go
	if _, err := os.Stat(configPath); os.IsNotExist(err) || force {
```

```213:238:core/src/main/golang/native/config/fetch.go
		if _, err := os.Stat(ps); err == nil {
			return
		}

		url, err := U.Parse(us)
		if err != nil {
			return
		}

		if prefix == RULES {
			if pib, uok := provider["path-in-bundle"]; uok {
				if pib, uok := pib.(string); uok && pib != "" {
					// actually, we don't need to extract the file here; the core will do it.
					// however, due to historical reasons, CMFA fetches provider content when loading profile,
					// so we maintain consistency with the old behavior.
					if file, err := RB.Open(pib); err == nil {
						defer file.Close()
						if err := writeFile(ps, file); err == nil {
							return
						}
					}
				}
			}
		}

		_, _ = fetch(url, ps)
```

为什么是问题：`force` 只出现在 `config.yaml` 那一次 `Stat` 判断里。provider 文件只要 `Stat` 成功就 return，远程内容变了，这次强制更新也不会重下。文件还不存在时，URL 解析失败被 return 吃掉，`fetch` 的错误被 `_, _` 丢掉。回调会先上报 `FetchProviders`，然后继续验证，下载失败不会成为 `FetchAndValid` 的返回错误。

方向：`force` 为真时重新下载已有 provider，并把 URL 解析和下载错误返回给 `FetchAndValid`。

## r08-5

严重度：中

症状：TUN 启动失败时 Kotlin 调用正常返回；失败发生在监听器接管 fd 之前时，这个 fd 也不会被关闭。重启时旧隧道已经先被拆掉。

```112:129:core/src/main/cpp/main.c
JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeStartTun(JNIEnv *env, jobject thiz,
                                                              jint fd,
                                                              jstring stack,
                                                              jstring gateway,
                                                              jstring portal,
                                                              jstring dns,
                                                              jobject cb) {
    TRACE_METHOD();

    scoped_string _stack = get_string(stack);
    scoped_string _gateway = get_string(gateway);
    scoped_string _portal = get_string(portal);
    scoped_string _dns = get_string(dns);
    jobject _interface = new_global(cb);

    startTun(fd, _stack, _gateway, _portal, _dns, _interface);
}
```

```71:91:core/src/main/golang/native/tun.go
	if rTun != nil {
		rTun.close()
		rTun = nil
	}

	f := int(fd)
	s := C.GoString(stack)
	g := C.GoString(gateway)
	p := C.GoString(portal)
	d := C.GoString(dns)

	remote := &remoteTun{callback: callback, closed: false, limit: semaphore.NewWeighted(4)}

	app.ApplyTunContext(remote.markSocket, remote.querySocketUid)

	closer, err := tun.Start(f, s, g, p, d)
	if err != nil {
		remote.close()

		return 1
	}
```

```32:36:core/src/main/golang/native/tun/tun.go
		prefix, err := netip.ParsePrefix(gatewayStr)
		if err != nil {
			log.Errorln("TUN:", err)
			return nil, err
		}
```

```70:73:core/src/main/golang/native/tun/tun.go
	listener, err := sing_tun.New(options, tunnel.Tunnel)
	if err != nil {
		log.Errorln("TUN:", err)
		return nil, err
```

```67:76:core/src/main/java/com/github/kr328/clash/core/Clash.kt
    fun startTun(
        fd: Int,
        stack: String,
        gateway: String,
        portal: String,
        dns: String,
        markSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int
    ) {
        Bridge.nativeStartTun(fd, stack, gateway, portal, dns, object : TunInterface {
```

为什么是问题：Go 的 `startTun` 在 `tun.Start` 失败时返回 1，JNI 方法是 `void`，这个返回值被丢掉。`Clash.startTun` 也没有错误通道，网关前缀无法解析或 `sing_tun.New` 失败时，调用方只能看到函数返回。前缀解析失败发生在 `FileDescriptor` 交给监听器之前，函数直接 `return nil, err`，传入的 fd 没有关闭。若这是一次重启，旧的 `rTun` 在新隧道成功前就已经 `close`，失败后旧隧道和新隧道都不在，界面得不到失败。

方向：把 `startTun` 的失败码回传给 Kotlin；`tun.Start` 未接管 fd 时关闭该 fd，并让调用方知道启动没有成功。
