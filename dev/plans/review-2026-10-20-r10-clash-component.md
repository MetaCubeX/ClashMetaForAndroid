---
title: "审查发现 r10 clash-component"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "进程识别会串到别的应用，地理库和外部 UI 更新会丢掉旧文件，订阅本地读失败和 Geo 坏下载会被当成成功。"
---

# 审查发现 r10 clash-component

只读 `core/src/foss/golang/clash/component` 的当前源码。下面 5 条都是这条路径里会触发的行为错误。

## r10-1

- 编号：r10-1
- 严重度：高
- 症状：连接的源地址对不上本机 socket 时，仍返回另一个 socket 的 UID。Android 上按这个 UID 做应用分流或访问控制，会套到别的应用上。

```136:174:core/src/foss/golang/clash/component/process/process_linux.go
	err = ErrNotFound
	for _, msg := range messages {
		if len(msg.Data) < inetDiagResponseSize {
			continue
		}

		response := (*inetDiagResponse)(unsafe.Pointer(&msg.Data[0]))

		// always set to allow fallback when check fails
		uid, inode, err = response.UID, response.INode, nil

		// check src port
		if binary.BigEndian.Uint16(response.SrcPort[:]) != uint16(srcPort) {
			continue
		}

		// check src IP
		var src netip.Addr
		switch response.Family {
		case unix.AF_INET:
			var a [4]byte
			copy(a[:], response.Src[:4])
			src = netip.AddrFrom4(a)
		case unix.AF_INET6:
			var a [16]byte
			copy(a[:], response.Src[:])
			src = netip.AddrFrom16(a).Unmap()
		default:
			continue
		}
		if src != ip.Unmap() {
			continue
		}

		// this is the one we want
		break
	}

	return
```

```65:78:core/src/foss/golang/clash/component/process/process_linux.go
func findProcessName(network string, ip netip.Addr, srcPort int) (uint32, string, error) {
	uid, inode, err := resolveSocketByNetlink(network, ip, srcPort)
	if runtime.GOOS == "android" {
		// on Android (especially recent releases), netlink INET_DIAG can fail or return UID 0 / empty process info for some apps
		// so trying fallback to resolve /proc/net/{tcp,tcp6,udp,udp6}
		if err != nil {
			uid, inode, err = resolveSocketByProcFS(network, ip, srcPort)
		} else if uid == 0 {
			pUID, pInode, pErr := resolveSocketByProcFS(network, ip, srcPort)
			if pErr == nil && pUID != 0 {
				uid, inode, err = pUID, pInode, nil
			}
		}
	}
```

请求带了 `netlink.Dump`（同文件 126 行），内核会回一串 socket。端口或地址不符时循环 `continue`，但 145 行已经把 `err` 写成 `nil` 并留下最后一条的 UID。一条都对不上时，函数仍成功返回这条 UID。同文件 293–296 行写明 TUN 下源地址经常是网关而不是 socket 本机地址，所以上面的地址比较会失败。此时 `err == nil` 且 UID 常常不是 0，70–77 行的 `/proc/net` 回退不会执行，后续还会按这个错误 UID 去找进程名。

方向：地址或端口未命中时恢复 `ErrNotFound`，让 Android 走进 procfs 回退。

## r10-2

- 编号：r10-2
- 严重度：高
- 症状：更新 MMDB 或 ASN 时如果落盘失败，旧库已经被截断，接着重新打开这个坏文件会把进程打退出。

```70:74:core/src/foss/golang/clash/component/updater/update_geo.go
	defer mmdb.ReloadIP()
	mmdb.IPInstance().Reader.Close() //  mmdb is loaded with mmap, so it needs to be closed before overwriting the file
	if err = vehicle.Write(data); err != nil {
		return fmt.Errorf("can't save MMDB database file: %w", err)
	}
```

```101:105:core/src/foss/golang/clash/component/updater/update_geo.go
	defer mmdb.ReloadASN()
	mmdb.ASNInstance().Reader.Close() //  mmdb is loaded with mmap, so it needs to be closed before overwriting the file
	if err = vehicle.Write(data); err != nil {
		return fmt.Errorf("can't save ASN database file: %w", err)
	}
```

```38:47:core/src/foss/golang/clash/component/resource/vehicle.go
func safeWrite(path string, buf []byte) error {
	dir := filepath.Dir(path)

	if _, err := os.Stat(dir); os.IsNotExist(err) {
		if err := os.MkdirAll(dir, dirMode); err != nil {
			return err
		}
	}

	return os.WriteFile(path, buf, fileMode)
}
```

```58:61:core/src/foss/golang/clash/component/mmdb/mmdb.go
		mmdb, err := maxminddb.Open(mmdbPath)
		if err != nil {
			log.Fatalln("Can't load MMDB: %s", err.Error())
		}
```

`vehicle.Write` 走到 `os.WriteFile`，打开时就截断目标文件，写到一半失败（磁盘满、IO 错误）时旧库已经不在。`UpdateMMDB` / `UpdateASN` 用 `defer` 在返回错误时仍执行 `ReloadIP` / `ReloadASN`，下一次 `IPInstance` / `ASNInstance` 打开这个残文件，81–82 行对 ASN 同样 `Fatalln`。旧库回不来，进程直接退出。

方向：写到临时文件并校验后再替换；写入失败不要 Reload 已经被截断的库。

## r10-3

- 编号：r10-3
- 严重度：中
- 症状：订阅或规则文件能 stat 到但读失败时，初始化被当成成功，内存里是空配置，也不会再拉远端。

```56:70:core/src/foss/golang/clash/component/resource/fetcher.go
func (f *Fetcher[V]) Initial() (V, error) {
	if stat, fErr := os.Stat(f.vehicle.Path()); fErr == nil {
		// local file exists, use it first
		buf, err := os.ReadFile(f.vehicle.Path())
		modTime := stat.ModTime()
		contents, _, err := f.loadBuf(buf, utils.MakeHash(buf), false)
		f.updatedAt = modTime // reset updatedAt to file's modTime

		if err == nil {
			err = f.startPullLoop(time.Since(modTime) > f.interval)
			if err != nil {
				return lo.Empty[V](), err
			}
			return contents, nil
		}
	}
```

```143:145:core/src/foss/golang/clash/component/resource/fetcher.go
	if buf == nil { // f.hash has been changed between f.vehicle.Read but should not happen (cause by concurrent)
		return lo.Empty[V](), true, nil
	}
```

`os.ReadFile` 失败时返回的 `buf` 是 nil，错误被下一行 `:=` 盖掉。`loadBuf` 见到 nil 就当作内容未变，返回空值和 `nil` 错误。`Initial` 因此在 64 行走成功分支，把空结果交给调用方，73 行之后的 bundle 和远端回退都不会发生。文件无读权限、路径是目录，或 stat 之后文件被删掉，都会走到这里。78–83 行对 bundle 的 `io.ReadAll` 用了同样的覆盖，读失败同样不会把错误留住。

方向：保留读文件的错误并返回，让后面的 bundle 和远端回退继续执行。

## r10-4

- 编号：r10-4
- 严重度：中
- 症状：Geo 数据库地址返回错误页或无效内容时，`InitGeoSite` / `InitGeoIP` / `InitASN` 仍返回成功，随后一直使用这份无效文件。

```71:87:core/src/foss/golang/clash/component/geodata/init.go
func downloadToPath(url string, path string) (err error) {
	ctx, cancel := context.WithTimeout(context.Background(), time.Second*90)
	defer cancel()
	resp, err := mihomoHttp.HttpRequest(ctx, url, http.MethodGet, nil, nil)
	if err != nil {
		return
	}
	defer resp.Body.Close()

	f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY, 0o644)
	if err != nil {
		return err
	}
	defer f.Close()
	_, err = io.Copy(f, resp.Body)

	return err
}
```

```102:113:core/src/foss/golang/clash/component/geodata/init.go
	if !initGeoSite {
		if err := Verify(C.GeositeName); err != nil {
			log.Warnln("GeoSite.dat invalid, remove and download: %s", err)
			if err := os.Remove(C.Path.GeoSite()); err != nil {
				return fmt.Errorf("can't remove invalid GeoSite.dat: %s", err.Error())
			}
			if err := downloadToPath(GeoSiteUrl(), C.Path.GeoSite()); err != nil {
				return fmt.Errorf("can't download GeoSite.dat: %s", err.Error())
			}
		}
		initGeoSite = true
	}
```

`downloadToPath` 不看 HTTP 状态码。`HttpRequest` 对 4xx/5xx 仍返回响应体，错误页会被写进 Geo 文件。`InitGeoSite` 第一次 `Verify` 失败后会再下载一次，但 112 行不再校验就把 `initGeoSite` 设为 true。同一 URL 连续两次都是无效内容时，初始化返回 nil，本次进程内不再重试。`InitGeoIP` 在 131–141 行和 153–163 行对 GeoIP、MMDB 做了同样的事，`InitASN` 在 180–190 行也是。

方向：非成功状态码当下载失败；替换文件之后再次校验，通过才把初始化标记设上。

## r10-5

- 编号：r10-5
- 严重度：高
- 症状：外部 UI 更新在新文件落地前先删掉现有目录；移动失败时旧界面和新解压结果一起消失。

```115:148:core/src/foss/golang/clash/component/updater/update_ui.go
func (u *UIUpdater) downloadUI() error {
	data, err := downloadForBytes(u.externalUIURL)
	if err != nil {
		return fmt.Errorf("can't download file: %w", err)
	}

	tmpDir := C.Path.Resolve("downloadUI.tmp")
	defer os.RemoveAll(tmpDir)

	os.RemoveAll(tmpDir) // cleanup tmp dir before extract
	log.Debugln("extractedFolder: %s", tmpDir)
	err = extract(data, tmpDir)
	if err != nil {
		return fmt.Errorf("can't extract compressed file: %w", err)
	}

	log.Debugln("cleanupFolder: %s", u.externalUIPath)
	err = cleanup(u.externalUIPath) // cleanup files in dir don't remove dir itself
	if err != nil {
		if !os.IsNotExist(err) {
			return fmt.Errorf("cleanup exist file error: %w", err)
		}
	}

	err = u.prepareUIPath()
	if err != nil {
		return fmt.Errorf("prepare UI path failed: %w", err)
	}

	log.Debugln("moveFolder from %s to %s", tmpDir, u.externalUIPath)
	err = moveDir(tmpDir, u.externalUIPath) // move files from tmp to target
	if err != nil {
		return fmt.Errorf("move UI folder failed: %w", err)
	}
```

```312:326:core/src/foss/golang/clash/component/updater/update_ui.go
	for _, dirEntry := range dirEntryList {
		srcPath := filepath.Join(src, dirEntry.Name())
		dstPath := filepath.Join(dst, dirEntry.Name())
		err = os.Rename(srcPath, dstPath)
		if err != nil {
			// Fallback for invalid cross-device link (errno:18).
			if errors.Is(err, syscall.Errno(18)) {
				err = copyAll(srcPath, dstPath)
				_ = os.RemoveAll(srcPath)
			}
		}
		if err != nil {
			return err
		}
	}
```

解压成功之后，132 行的 `cleanup` 先清空已经装好的 UI。随后 `moveDir` 逐项改名，跨设备时改名失败会复制；任一项失败就返回。函数开头的 `defer os.RemoveAll(tmpDir)` 仍会删掉临时目录里还没移走的文件。目标目录里的旧文件已经没了，新文件也不完整。磁盘满、权限错误或跨设备复制失败都会触发。

方向：新目录完整移入并成功之后再删除旧 UI；失败时保留临时目录或旧目录。
