---
title: "审查方案 r01 app"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 5、drop 0、weaken 0"
---

# 审查方案 r01 app

核对范围是 `app` 活码。五条引用的行为都还在，结论是 hold 5、drop 0、weaken 0。

## r01-1

- 问题类：应用更新会删掉用户导入的 `geoip.metadb`、`geosite.dat`、`ASN.mmdb`，解压失败还会留下不再重试的半截文件。
- 不变量：这三个文件名若由用户导入成功，之后的包更新不得改回 assets。没有用户标记时，目标缺失或修改时间早于 `lastUpdateTime` 才用 assets 补齐，而且只有完整写完才替换旧文件。拷贝失败保留上一份完整文件，下次启动仍会重试。界面进程和 `:background` 同时解压时，目标文件必须是某一份完整拷贝。
- 要动的符号或文件：`MainApplication.extractGeoFiles`（活码在 `MainApplication.kt` 第 27 行调用，三个地理库的删除和 `FileOutputStream` 覆盖在第 41–70 行；同函数第 72–80 行的 `BundleMRS.7z` 走同一套不安全拷贝）。`MetaFeatureSettingsActivity.importGeoFile` 成功替换 `geoip.metadb`、`geosite.dat` 或 `ASN.mmdb` 时写下用户标记（导入文件名在第 110–118 行）。标记放在两个进程都能在 `onCreate` 里读到的 `clashDir` 旁路，不要依赖此时尚未绑定的远程服务。标记对应的文件已经不存在时，清掉标记并允许从 assets 补拷。
- 明确不改什么：核心读取的仍是 `filesDir/clash` 下原来的文件名。不新增 `BundleMRS.7z` 的导入入口，也不给它用户标记。`country.*` 以及不会被 `extractGeoFiles` 删除的其他扩展名（例如 `geoip.db`）维持现状。
- 怎么本地验证：`app` 下没有现成测试。把「要不要覆盖」和「临时文件成功后再替换」抽成不依赖界面的函数后，断言：有用户标记且 `lastUpdateTime` 新于文件修改时间时，文件字节保持用户内容；无标记且文件旧于更新时间时，成功后字节与 assets 一致；拷贝中途失败时目标字节仍是上一份，并且下一次调用会再试；两路并发解压结束后目标是完整拷贝，而不是两路交错的字节。

## r01-2

- 问题类：导入地理库时先截断原文件，输入流为空仍提示成功，拷贝失败则原库已损且事件循环退出。
- 不变量：`importGeoFile` 在新内容完整落到磁盘之前，不得改变 `clashDir` 里已有目标文件的字节。`openInputStream` 返回 null、拷贝抛错、或写出失败时，提示 `R.string.geofile_import_failed`，不提示 `R.string.geofile_imported`，并让 `main()` 继续接收下一次请求。只有完整写完才替换原文件；若目标是 r01-1 的三个内置文件名，替换成功后才写用户标记。
- 要动的符号或文件：`MetaFeatureSettingsActivity.importGeoFile` 第 122–131 行。这里 `FileOutputStream(outputFile)` 会立刻截断，`ins?.copyTo` 在流为 null 时什么都不写，随后第 130–131 行仍 toast 成功。异常目前没有接住，会离开第 39 行 `while` 里的 `main()`。
- 明确不改什么：扩展名白名单（第 86–108 行）、四种导入请求的分发、覆盖配置的 `defer` 和重置确认。
- 怎么本地验证：`app` 下没有现成测试。断言：输入流为 null 时原文件字节不变且结果为失败；拷贝抛错时原文件字节不变，调用方还能继续处理下一次导入；完整拷贝成功后目标字节等于输入。

## r01-3

- 问题类：VPN 尚未授权时，快捷设置磁贴丢掉授权 Intent，快捷方式和外部启动只提示无法启动。
- 不变量：`UiStore.enableVpn` 为真且 `VpnService.prepare` 返回非 null 时，磁贴、桌面快捷方式、`START_CLASH`、`TOGGLE_CLASH` 都打开系统授权页。用户同意后再调用一次 `startClashService()`。用户拒绝或取消则不启动隧道。`prepare` 返回 null 时仍直接启动，并且只启动一次。
- 要动的符号或文件：`TileService.onClick` 第 29–30 行丢掉了 `startClashService()` 的返回值。`ExternalControlActivity.startClash` 第 93–96 行只 toast `R.string.unable_to_start_vpn`。`onCreate` 第 78 行对 `START`/`TOGGLE` 立刻 `finish()`。清单里该 Activity 是 `android:noHistory="true"`（`AndroidManifest.xml` 第 79 行），离开后收不到授权结果，所以不能靠它自己 `startActivityForResult`。磁贴和外部入口要把授权 Intent 交给一个没有 `noHistory`、并且能在 `RESULT_OK` 后再次调用 `startClashService()` 的界面。快捷方式已经指向 `ExternalControlActivity`（`MainActivity.setupShortcuts` 第 234–247 行），改承接结果的那段即可。
- 明确不改什么：`MainActivity.startClash` 第 169–179 行已有的「授权成功后再启动」。`STOP_CLASH`、`enableVpn` 为假时直接启动 `ClashService` 的分支、`ACTION_VIEW` 导入配置、`RestartReceiver`。
- 怎么本地验证：`app` 下没有现成测试。断言：`prepare` 返回非 null 时磁贴点击会打开该 Intent，而不是忽略返回值；`START`/`TOGGLE` 在同样条件下不会只 toast 后结束，同意后会再调用 `startClashService()`，取消后不会调用；`prepare` 返回 null 时服务启动一次。

## r01-4

- 问题类：剪贴板没有纯文本会让访问控制页崩溃；带 `\r` 或对不上包名时会清空已选应用并在离开时写回。
- 不变量：剪贴板项没有文本时，`selected` 保持原样，并给出失败提示，异常不离开 `main()`。有文本时按行去掉 `\r` 和首尾空白、丢掉空行，再与已安装包名求交。交集非空才替换 `selected`。交集为空时 `selected` 保持原样。离开页面时 `defer` 仍只把真正变化后的集合写入策略或全局名单。
- 要动的符号或文件：`AccessControlActivity` 导入分支第 132–137 行。`getItemAt(0).text` 没有空检查就 `split("\n")`，随后无条件 `selected.clear()`。持久化仍在第 56–60 行的 `defer` 和第 181–185 行的 `service.accessControlPackages = packages`（策略分支在第 166–178 行）。
- 明确不改什么：返回键第 34–36 行仍直接 `finish()`。全选、全不选、反选的语义。导出剪贴板。`persistPackages` 里「集合没变就不写」的判断。
- 怎么本地验证：`app` 下没有现成测试。断言：`text` 为 null 时 `selected` 不变且调用不抛异常；`com.example.app\r\n` 这类行能与已安装包名相交并成为新的选择；交集为空时 `selected` 不变，随后走 `persistPackages` 不会把空集合写进存储；本来就是 `\n` 分隔且有交集时，选择仍被替换成该交集。

## r01-5

- 问题类：分流策略自动同步抛错时，`AppsStrategyActivity.main()` 的事件循环结束。
- 不变量：新建、改名、改模式、删除、切换生效策略时，本地写入先完成。自动同步抛出的一般异常只在主线程提示，然后 `main()` 继续接收事件。`CancellationException` 继续向外抛。未开自动同步或端点、用户名、密码不齐时，仍然不调用 `webdavPerform`。
- 要动的符号或文件：`AppsStrategyActivity.syncIfEnabled` 第 180–195 行。调用点在 `setActiveConfig` 第 123 行、`deleteConfig` 第 137 行、`createConfig` 第 155 行、`updateConfig` 第 176 行，都在保存之后，且没有 `try/catch`。`BaseActivity.onCreate` 第 98–100 行的 `launch { main() }` 没有异常处理器。提示方式对齐 `NetworkSettingsActivity.syncAccessControl` 第 53–66 行和 `WebdavSettingsActivity.runAction` 第 53–72 行：接住异常并 `showExceptionToast`。`syncIfEnabled` 运行在 `Dispatchers.IO` 上，提示要回到主线程。
- 明确不改什么：本地保存仍在同步之前。`withProfile` 对 `DeadObjectException` 的重试。WebDAV 设置页和网络设置页已经接住的那两处。访问控制离开页面后的同步。
- 怎么本地验证：`app` 下没有现成测试。断言：`webdavPerform` 抛出一般异常时，四个写入口的本地结果仍在，并且 `main()` 的 `select` 还能收到下一次请求；`CancellationException` 不被吃掉；自动同步条件不满足时不会调用 `webdavPerform`。
