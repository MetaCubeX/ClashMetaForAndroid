---
title: "审查方案 r01 app"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 4、drop 1、weaken 0"
---

# 审查方案 r01 app

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 app/ 活码。结论：hold 4、drop 1、weaken 0。

drop：r01-5。`LogcatCache.append` 在写满 128 条时仍会把 `appended` 先减再加，增量快照的 `appended` 可以停在 0，全量快照的追加条数仍是 `array.size() + appended`。`LogcatActivity` 第 123–127 行把整份 `snapshot.messages` 交给界面，界面整表替换，`removed` 和 `appended` 不参与绘制。新行在这份列表里，不存在发现稿说的「只删旧行、不附新行」。

## r01-1

- 结论：hold
- 问题类：隐藏系统应用时，全选、反选或剪贴板导入会把当前看不见的已选应用从选择集里删掉，返回后写回策略配置。
- 不变量：全选之后，当前可见包都在 `selected` 里，操作前已选但不在可见列表里的包名仍在。反选只翻转可见包，可见列表外的已选包名仍在。导入用剪贴板里的非空行匹配已安装包名，可见性不作为匹配条件；有匹配时，新的选择集是「可见列表外的原有已选」并上「剪贴板里已安装的包」；没有任何已安装包匹配时，`selected` 保持原样。全不选之后 `selected` 为空。`config` 非空时，离开页面仍由 `persistPackages` 把最终的 `selected` 写回 `appsStrategyConfigs`。
- 要动的符号或文件：`AccessControlActivity` 的 `SelectAll`（现 157–166 行）、`SelectInvert`（现 174–183 行）、`Import`（现 204–212 行，整段处理在 185–223 行）。这三处现在都先拿 `design.apps` 的包名，再 `selected.clear()`。`design.apps` 来自 `loadAccessControlApps` 的可见列表：没有 `INTERNET` 的普通应用在 `AccessControlApps.kt` 第 44 行被跳过，关闭显示系统应用时系统应用在第 48 行不进入可见列表。导入要匹配已安装包，不能只拿 `design.apps`；在 `Import` 分支另查已安装包名，或让 `AccessControlApps` 把这份包名带出来。写回仍在 `defer`（现 64–74 行）和 `persistPackages`（配置写回现 270–278 行）。
- 明确不改什么：`SelectNone` 继续清空整个 `selected`。`loadAccessControlApps` 的跳过规则和「隐藏系统应用只改可见行」保持原样。`defer` 的写回条件、`ExitAction` 的重启与同步保持原样。`AppsStrategyShell` 不改。应用集合的 `SaveAppSet` 不改。
- 怎么本地验证：app 模块没有现成测试。应新增断言：`showSystem` 为假且 `selected` 已含一个系统包时，全选后该系统包仍在，可见的非系统包都在；反选后该系统包仍在，可见包的选中状态对调；剪贴板同时有该系统包和一个可见包时，导入后两者都在，且不在剪贴板里的旧可见包被替换掉；剪贴板没有任何已安装包时 `selected` 不变；全不选后 `selected` 为空。

## r01-2

- 结论：hold
- 问题类：代理页重载分组、选节点、测速或切换模式失败时，异常会取消主界面同一个 `coroutineScope` 里的仪表盘、配置、应用和设置。
- 不变量：`queryProxyGroup`、`patchSelector`、`healthCheck`、会话模式的 `patchOverride` 抛出 `CancellationException` 以外的异常时，异常停在代理页并在本页提示，`ProxyShell.run` 不把该异常抛给 `MainActivity`。`CancellationException` 继续向上传递。`withClash` 对 `DeadObjectException` 的重试保持原样。更新提供者失败时仍只提示该提供者，不取消主界面循环。
- 要动的符号或文件：`ProxyShell.collect` 里的 `Request.Reload`（现 269–272 行，经 `launch` 调用 `startQuery`）、`Request.Select`（现 273–281 行，在 `select` 回调里直接 `withClash`）、`Request.UrlTest`（现 282–288 行，`launch` 里直接 `withClash`）、`Request.PatchMode`（现 308–315 行，回调里直接 `withClash`）。`startQuery` 现 194–221 行没有接住异常。`withClash` 只吞 `DeadObjectException`（`Remote.kt` 现 22–26 行）。`ProxyShell.run` 现 104–109 行只重新抛出 `CancellationException`。`MainActivity.main` 的 `coroutineScope` 现 73–180 行，其中四个壳的 `launch` 在 74–77 行，仪表盘循环在 83–179 行。`BaseActivity.onCreate` 的 `launch { main() }` 现 106–108 行没有接住。这四个请求要在进入该 `coroutineScope` 之前把非取消异常接住；提示用代理页已有的异常提示。
- 明确不改什么：`withClash` 和 `withProfile` 的 `DeadObjectException` 重试不改。`UpdateProvider`（现 289–307 行）、嵌入模式下 `reloads` 里的 `queryProxyGroupNames`（现 227–234 行）、`refreshProviders`（现 80–89 行）已有的捕获不改。不把 `MainActivity` 的 `coroutineScope` 改成 `supervisorScope`。`MainActivity` 仪表盘自己的 `SetMode`、`SelectNode`、`fetch` 不在这条里改。
- 怎么本地验证：没有现成测试。应新增断言：上述四个调用抛出 `IOException` 或 `ClashException` 时，`ProxyShell.run` 不向外抛出该异常，同一 `coroutineScope` 里的另一个子循环仍能收到下一次事件；抛出 `CancellationException` 时该异常仍传出 `run`。

## r01-3

- 结论：hold
- 问题类：用允许列表里的非规范扩展名导入 GeoIP、GeoSite 或 ASN 时提示成功，规范库文件没有被替换，也没有打上用户标记。
- 不变量：GeoIP、GeoSite、ASN 只有在写入并标记 `userGeoFileNames` 里的对应文件时才提示成功：`geoip.metadb`、`geosite.dat`、`ASN.mmdb`。所选文件的扩展名对不上该类型的这一个规范名时，不写入另一个文件名，不提示成功，并走现有的失败提示。规范名导入后，`importReplacingGeoFile` 写到该文件，且 `isUserGeoMarked` 为真。
- 要动的符号或文件：`MetaFeatureSettingsActivity.importGeoFile`。允许的扩展名现 87–89 行，仍是 `.metadb`、`.db`、`.dat`、`.mmdb`。输出名现 111–120 行，是 `geoip$ext`、`geosite$ext`、`country$ext`、`ASN$ext`。成功提示现 131–132 行，写完就弹出。`importReplacingGeoFile` 现 99–104 行写完即调用 `markUserGeoFile`。`markUserGeoFile` 现 34–35 行对不在 `userGeoFileNames` 里的名字直接返回。`userGeoFileNames` 现 8–12 行只有上述三个名字。`MainApplication.extractGeoFiles` 现 48–55 行安装并在更新时覆盖的也是这三个名字。要改的是 GeoIP、GeoSite、ASN 三个分支的输出名和成功条件。
- 明确不改什么：`userGeoFileNames` 的三个名字不增删。`installBundledGeoFile`、`shouldInstallGeoAsset` 的覆盖条件不改。`BundleMRS.7z` 不改。`ImportCountry` 继续写 `country$ext`，不给它登记新的规范名，也不把它并进上面的成功条件。
- 怎么本地验证：没有现成测试。应新增断言：GeoIP 选 `.mmdb`、`.dat` 或 `.db` 时结果为失败，目录里不出现 `geoip.mmdb`、`geoip.dat`、`geoip.db`，`geoip.metadb` 不被标记；GeoIP 选 `.metadb` 时写入 `geoip.metadb` 且已标记。GeoSite 只有 `.dat` 成功，ASN 只有 `.mmdb` 成功。对不上的扩展名不出现成功提示。

## r01-4

- 结论：hold
- 问题类：访问控制的已选应用，以及覆写设置和 Meta 功能的持久覆写，只在 `finish()` 的 `defer` 里写回；旋转或深色模式重建不经过 `finish()`，未返回前的修改会丢。
- 不变量：旋转销毁或 `recreate()` 生成的新页面，打开时看到的是重建前的已选包和两份持久覆写。进程被杀死后的冷启动仍从存储读取。写回存储、重启 VPN、WebDAV 同步仍只发生在用户离开页面时的那一次 `defer`。同一次离开只写回一次。
- 要动的符号或文件：用 `onCreate` 收到的 `savedInstanceState` 保住这一次重建，不把写存储提前到销毁时。`AccessControlActivity` 的 `selected` 现是 `main()` 的局部变量，只在 `defer`（现 64–74 行）里交给 `persistPackages`。`OverrideSettingsActivity` 的 `configuration` 只在 `defer`（现 22–26 行）里 `patchOverride`。`MetaFeatureSettingsActivity` 的 `configuration` 只在 `defer`（现 27–31 行）里 `patchOverride`。这两个覆写对象和 `selected` 要在 `onSaveInstanceState` 时读得到。`AccessControlActivity.onCreate` 现 36–40 行会先调用 `super.onCreate`，而 `BaseActivity.onCreate` 现 106–108 行在那时就把 `main()` 排队；实例状态要在 `onCreate` 返回前放进字段，`main()` 初始化时优先用它。`OverrideSettingsActivity` 和 `MetaFeatureSettingsActivity` 目前没有自己的 `onCreate`。`ConfigurationOverride` 已可放入 `Bundle`，已选包名用字符串列表放入 `Bundle`。`BaseActivity.onDestroy` 现 125–129 行只 `cancel()`，`finish()` 现 131–144 行才调用 `defer`。`onConfigurationChanged` 现 146–153 行在日夜模式变化时对已创建的 Activity 调用 `recreate()`。清单里这三页的 `configChanges` 只有 `uiMode`：`OverrideSettingsActivity` 现 166–169 行，`MetaFeatureSettingsActivity` 现 171–174 行，`AccessControlActivity` 现 186–189 行。旋转会销毁重建，深色模式走 `recreate()`，两条都会给出 `savedInstanceState`。
- 明确不改什么：`finish()` 里的 `defer` 仍是唯一写存储的地方，`ExitAction` 的重启和同步不改到旋转或 `recreate()` 上。不往这三页的 `configChanges` 里加 `orientation` 或 `screenSize`。`loadAccessControlApps` 的可见性规则不改。排序、是否显示系统应用这些已经写入 `uiStore` 的项不改。`PropertiesActivity` 在 `ActivityStop` 写 profile 的路径不改。Geo 文件导入已经直接落盘，不改。
- 怎么本地验证：没有现成测试。应新增断言：访问控制在未调用 `finish()` 的情况下，把当前 `selected` 放进实例状态后再按该状态构造页面，得到的包名集合与重建前相同，且 `appsStrategyConfigs` 仍是重建前的存储内容。两份持久覆写同样断言实例状态里的 `configuration` 与重建前相同，且这次恢复没有调用 `patchOverride`。没有实例状态时，页面仍从存储或 `queryOverride` 读取。调用 `finish()` 后仍只通过现有 `defer` 写回一次。
