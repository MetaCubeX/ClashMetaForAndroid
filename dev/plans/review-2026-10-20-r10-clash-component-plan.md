---
title: "审查方案 r10 clash-component"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 5、drop 0、weaken 0"
---

# 审查方案 r10 clash-component

对照 `core/src/foss/golang/clash/component` 当前源码。五条引用的行号与行为一致，全部 hold。

## r10-1

- 问题类：netlink 没对上端口或地址时，仍把另一条 socket 的 UID 当成功结果返回。
- 不变量：`resolveSocketByNetlink` 只在源端口和源地址都命中时返回该 socket 的 UID 与 inode，且错误为 nil；否则返回 `ErrNotFound`，UID 不得来自未命中的响应。Android 上因此走进已有的 `/proc/net` 回退。
- 要动的符号或文件：`process/process_linux.go` 的 `resolveSocketByNetlink`。实际读到 136–174 行：循环前把 `err` 设为 `ErrNotFound`，145 行在 148 行端口比较和 166 行地址比较之前就写下 UID 并把 `err` 改成 nil，未命中则 `continue`。126 行带 `netlink.Dump`。65–77 行只有 `err != nil` 或 `uid == 0` 才调用 `resolveSocketByProcFS`。293–296 行写明 TUN 下源地址经常是网关。改法：先比较端口和地址，两者都相等才写入 `uid`、`inode` 并把 `err` 置 nil，然后 `break`；循环前的 `ErrNotFound` 保留。
- 明确不改什么：`resolveSocketByProcFS` 与 `searchProcNetFileByPort` 的端口优先匹配；`findProcessName` 在 uid 为 0 时的 procfs 条件；其他 GOOS 的 `findProcessName`。
- 怎么本地验证：该目录没有进程匹配测试。应新增断言：多条响应的端口或地址都不命中时返回 `ErrNotFound`，且 UID 不是最后一条的 UID；端口与地址都命中时返回该条 UID；端口命中但地址不命中时返回 `ErrNotFound`，Android 上 `findProcessName` 会调用 `resolveSocketByProcFS`。

## r10-2

- 问题类：更新 MMDB 或 ASN 时落盘失败会截断旧库，下次打开残文件会退出进程。
- 不变量：写入失败时目标路径上的旧字节保持不变，已打开的 reader 仍可用；只有新文件完整替换成功之后才关闭 mmap reader 并 `ReloadIP` / `ReloadASN`。
- 要动的符号或文件：`resource/vehicle.go` 的 `safeWrite`（38–47 行，`os.WriteFile` 打开即截断）；`updater/update_geo.go` 的 `UpdateMMDB`（70–74 行）和 `UpdateASN`（101–105 行）。`mmdb/mmdb.go` 的 `IPInstance`（58–60 行）与 `ASNInstance`（80–82 行）在 `Open` 失败时 `Fatalln`。改法：`safeWrite` 先写同目录临时文件，关闭成功后再 `rename` 覆盖目标；`rename` 失败则删除临时文件并返回错误。两处更新去掉无条件 `defer Reload`；`Write` 成功后再 `Close` 当前 reader 并调用对应的 `Reload`。
- 明确不改什么：`UpdateMMDB` / `UpdateASN` 里 `maxminddb.FromBytes` 的下载校验；`UpdateGeoIp` / `UpdateGeoSite` 的校验和缓存清理；`IPInstance` / `ASNInstance` 在文件确实打不开时的 `Fatalln`。
- 怎么本地验证：该目录没有这两处更新的测试。应新增断言：`safeWrite` 写到一半失败时，目标路径仍能读出完整旧内容；`UpdateMMDB` / `UpdateASN` 在 `Write` 返回错误时不调用 `ReloadIP` / `ReloadASN`，也不关闭当前 reader；`Write` 成功时目标文件变为新内容，并在返回前重置对应 once。

## r10-3

- 问题类：订阅或规则文件能 stat 到但读失败时，初始化被当成成功，内存里是空配置。
- 不变量：`os.ReadFile` 或 bundle 的 `io.ReadAll` 失败时不得进入 `Initial` 的成功返回；执行继续落到 bundle 和远端回退。全部回退都失败时返回错误，而不是空值加 nil 错误。
- 要动的符号或文件：`resource/fetcher.go` 的 `Fetcher.Initial`。59–61 行 `ReadFile` 的错误被 61 行 `:=` 盖掉；143–145 行 `loadBuf` 见到 nil buf 就返回空值和 nil 错误，64–69 行因此成功返回。78–83 行 bundle 的 `ReadAll` 被 83 行同样盖掉，86–92 行会记成 extract successful。改法：读文件使用单独的错误变量；失败时不调用 `loadBuf`，落到 73 行之后的 bundle 和 100 行的 `Update`。
- 明确不改什么：`loadBuf` 在哈希相同，或并发导致 buf 为 nil 时，返回“内容未变”的约定；`Update` 在 `vehicle.Read` 失败时返回错误；`startPullLoop` 的监听和定时拉取。
- 怎么本地验证：该目录没有 `Fetcher.Initial` 测试。应新增断言：`Stat` 成功且 `ReadFile` 失败时，`Initial` 不返回空配置加 nil 错误，并继续尝试 bundle 和远端；bundle 的 `ReadAll` 失败时不记 extract successful，也不把空结果当成功返回。

## r10-4

- 问题类：Geo 地址返回错误页或无效内容时，初始化仍返回成功并一直使用这份文件。
- 不变量：`downloadToPath` 只在 HTTP 2xx 时把响应体写入目标。`InitGeoSite`、`InitGeoIP`、`InitASN` 只在刚下载的文件通过校验后才把 `initGeoSite`、`initGeoIP`、`initASN` 标成已完成；校验失败则返回错误，标记保持未完成。
- 要动的符号或文件：`geodata/init.go` 的 `downloadToPath`（71–87 行，不看状态码；`http/http.go` 的 `HttpRequest` 90 行直接返回 `client.Do`）。`InitGeoSite` 102–112 行、`InitGeoIP` 131–141 行和 153–163 行、`InitASN` 180–190 行在下载之后无条件设置初始化标记。同目录 `resource/vehicle.go` 149–154 行对非 2xx 另作错误处理。改法：在 `OpenFile` 之前检查状态码，非 2xx 直接返回，不写目标路径。上述分支在 `downloadToPath` 成功后再做 `Verify` 或 `mmdb.Verify`，通过才把 `initGeoSite` 设为 true、`initGeoIP` 设为 1 或 2、`initASN` 设为 true。
- 明确不改什么：`geodata.Verify` 与 `mmdb.Verify` 的校验规则；本地文件第一次校验已经通过时的初始化；`updater` 里手动更新 Geo 的路径。
- 怎么本地验证：该目录没有 `downloadToPath` 或这三个 Init 的测试。应新增断言：4xx/5xx 时 `downloadToPath` 返回错误且目标路径没有错误页；下载内容通不过 `Verify` 或 `mmdb.Verify` 时，`InitGeoSite`、`InitGeoIP`、`InitASN` 返回错误，对应初始化标记保持未完成。

## r10-5

- 问题类：外部 UI 在新文件完整落地前清空旧目录，移动失败时旧界面和新解压结果一起消失。
- 不变量：`downloadUI` 返回错误时，调用前的 `externalUIPath` 内容保持完整。新目录全部就位之后才删除旧文件。失败时旧目录仍是一份完整界面。
- 要动的符号或文件：`updater/update_ui.go` 的 `downloadUI`（115–149 行：122 行 `defer os.RemoveAll(tmpDir)`，132 行在 `moveDir` 之前 `cleanup`）、`cleanup`（282–294 行）、`moveDir`（312–326 行，`Errno(18)` 时 `copyAll`，任一项失败就返回）。改法：先把新界面完整解压到临时目录。不要在替换前 `cleanup` 目标目录。跨设备时先把新树复制到目标所在文件系统的临时目录，复制未完成不碰旧目录。然后把旧目录改名为旁路备份，新目录改名为目标；任一步失败就把备份改回原路径。旧目录已还原，或新目录已完整就位之后，再删除临时目录和备份。
- 明确不改什么：`unzip`、`untgz`、`inDest` 的路径检查；`AutoDownloadUI` 在目录非空时跳过下载；`prepareUIPath` 仅在目录不存在时创建。
- 怎么本地验证：该目录没有 UI 更新测试。`updater/update_core_test.go` 不覆盖这条路径。应新增断言：`moveDir` 或跨设备复制中途失败时，原 UI 目录的条目和内容仍在；全部就位成功后目标目录是新内容；失败返回时旧目录已完整还原。
