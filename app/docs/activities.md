---
title: "app 的 Activity"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-24
summary: "BaseActivity 把 Design 设为内容视图。从策略进入的名单标题是策略名称"
---

# app 的 Activity

Activity 在应用进程。布局、列表和设置控件在 `design` 的 `*Design`。对 `:background` 的调用方式见 [远程调用](remote.md)。分层说明见 [界面](../../docs/systems/ui/INDEX.md)。

## BaseActivity

`BaseActivity<D : Design<*>>` 继承 `AppCompatActivity`，协程作用域是 `MainScope()`，并实现 `Broadcasts.Observer`。

`onCreate` 先按 `UiStore.darkMode` 套用 `AppThemeLight` 或 `AppThemeDark`，并把状态栏、导航栏设成当前主题的颜色。接着把 `uiStore.hideFromRecents` 写到本应用每一个 `appTask`。然后 `launch { main() }`。子类实现挂起函数 `main()`。

把界面交给 `design` 的顺序：

1. 子类构造对应的 `*Design`。`Design` 持有 `root` 视图，以及一个无限容量的 `requests` 通道。
2. `setContentDesign` 在 `window.decorView` 上投递一帧之后才赋值。
3. `design` 非空时 `setContentView(design.root)`；置空时放进一个空 `View`。
4. 子类在 `select` 里收取 `design.requests`，自己去调远程接口或打开下一个 Activity，再调用 Design 上的方法把状态写回去。
5. `onDestroy` 调用 `design.cancel()`，并取消 Activity 协程。

另一路输入是 `events`。`onStart` 把 Activity 登记为 `Remote.broadcasts` 的观察者，并发出 `ActivityStart`。`onStop` 移除观察者，并发出 `ActivityStop`。服务重建、隧道起停、配置加载、配置变更、配置更新成功或失败，都转成 `Event`。隧道停止且带了原因、同时 Activity 处于 started 时，用这个原因构造 `ClashException`，在当前 Design 上弹 toast。

`clashRunning` 读的是 `Remote.broadcasts.clashRunning`，不是当场查询状态 Provider。

`finish()` 先跑一次 `defer` 挂起块，再在不可取消的上下文里调用 `super.finish()`。重复进入 `finish()` 时，如果 `defer` 还在跑，直接返回。日夜模式与当前配置不一致时，`ApplicationObserver.createdActivities` 里已创建的 Activity 全部 `recreate()`。`onSupportNavigateUp` 转到 `onBackPressed()`。

`startActivityForResult` 用 `util/Activity.kt` 的 `ActivityResultLifecycle` 登记一次性合约，把结果恢复成挂起函数的返回值。

## 主界面

`MainActivity` 使用 `MainDesign`。`launchMode` 是 `singleTask`。清单里它响应 `MAIN`、`APPLICATION_PREFERENCES` 和 `QS_TILE_PREFERENCES`，但启动器图标挂在 `MainActivityAlias` 上，别名再指向它。

事件 `ActivityStart`、服务重建、隧道起停、配置加载或变更时调用 `fetch()`：写入 `clashRunning`、会话覆盖里的模式（没有则用隧道状态里的模式）、当前出口节点、当前配置名，以及首页六格的说明。隧道在跑时查询外部资源个数；没跑时个数为 -1。隧道在跑时，`fetch()` 会立刻取一次流量和运行时长，之后每秒再取。流量来自 `queryTrafficNow()` 和 `queryTrafficTotal()`。运行时长优先读 `TunnelClock` 写下的 `filesDir/tunnel_started_at`；文件不在时，从这次界面第一次看到隧道在跑开始计。

当前节点只在隧道运行且模式不是 `Direct` 时查询。`queryProxyGroupNames(true)`：`Global` 取名称忽略大小写等于 `GLOBAL` 的组，没有则取第一个组；其他模式取第一个名称不是 `GLOBAL` 的组，没有也退回第一个组。再以 `ProxySort.Default` 读该组的 `now`，空白则不画出口卡。组类型是 `Selector` 时，点出口卡打开该组的快切列表。

`MainDesign.Request` 的去向：

| 请求 | 行为 |
|:-----|:-----|
| `ToggleStatus` | 在跑则 `stopClashService()`，否则走下面的启动 |
| `OpenProxy` | 切到节点页。组名非空时再打开这一组。这不是导出的 `clash://` 契约 |
| `OpenProfiles` / `OpenAppsStrategy` | 切到配置或分流。设置只从底栏进入 |
| `OpenProviders` | 在跑则切到节点页的外部资源分段，否则 toast 先连接 |
| `OpenLogs` | `LogcatService.running` 为真时打开 `LogcatActivity`，否则打开 `LogsActivity` |
| `ReloadKernel` | 在跑则 `requestClashReload()`，否则 toast 先连接 |
| `OpenWebdav` | 打开 `WebdavSettingsActivity` |
| `OpenFiles` | 当前配置已导入则打开带该 UUID 的 `FilesActivity`，否则打开 `MetaFeatureSettingsActivity` |
| `SelectNode` | `patchSelector(group, name)`，再重载这一组，然后 `fetch()` |
| `SetMode` | 先提示模式切换，再把 `Clash.OverrideSlot.Session` 的 `mode` 写回去并 `fetch()` |

启动前先 `withProfile { queryActive() }`。没有当前配置，或当前配置尚未导入时，toast 提示并给出切到配置页的动作，不启动服务。否则调用 `startClashService()`。返回的 VPN 授权 Intent 非空时，用 `StartActivityForResult` 拉起系统授权；`RESULT_OK` 后再调一次 `startClashService()`。这一步抛异常则 toast 无法启动 VPN。

首页、节点、配置、应用分流、设置是 `MainActivity` 里的五页。底栏从左到右是节点、配置、仪表盘、分流、设置，只改当前页，不启动另一个 Activity。点当前页不重新创建。节点、配置、分流、设置不画返回。系统返回在这四页时先回到仪表盘。属性、访问控制、网络、覆写、Meta、WebDAV、关于、日志和文件盖住底栏。`MainActivity` 在前台时，配置更新完成或失败由它按 UUID 查出名称再 toast；失败 toast 可以打开对应的 `PropertiesActivity`。

这四页的会话分别在 `ProxyShell`、`ProfilesShell`、`AppsStrategyShell`、`SettingsShell`。对应 Activity 仍能单独打开，那时才 `setContentDesign`，有返回，没有底栏，会话是同一个 Shell。

图标未隐藏时，`onCreate` 注册三个动态快捷方式：切换、启动、停止。Intent 的类名是 `ExternalControlActivity`，动作是 `Intents` 里的对应常量，并带 `NEW_TASK`、`EXCLUDE_FROM_RECENTS`、`NO_ANIMATION`。Android 13 及以上若还没有通知权限，会请求 `POST_NOTIFICATIONS`；授权结果的回调是空的。

## 外部控制与主界面

`ExternalControlActivity` 不继承 `BaseActivity`，不创建 Design。清单把它标成导出、透明、`noHistory`、`excludeFromRecents`。它是给其他应用、链接和快捷方式用的入口，不是主界面上的一屏。用户能观察到的承诺见 [PRD-006](../../docs/product/requirements/PRD-006.md)。动作字符串与 meta 包名的对应关系见 [运行时](../../docs/systems/runtime/INDEX.md)。

`onCreate` 去掉过渡动画。除了 `ACTION_VIEW` 会先返回、在协程里收尾，其余分支处理完就 `finish()`。

`ACTION_VIEW` 要求链接上有 `url`。`type` 为 `file` 时创建 `Profile.Type.File`，为 `url` 或其他值时创建 `Profile.Type.Url`。名称缺省是 `R.string.new_profile`。`update-interval` 按分钟解析：大于 0 时至少 15 分钟，否则为 0，再换成毫秒。`withProfile` 里 `create` 再 `patch`，然后打开带这个 UUID 的 `PropertiesActivity` 并 `finish()`。导入之后的配置行为见 [配置](../../docs/systems/profiles/INDEX.md)。

启停不打开主界面，也不走 `MainActivity.startClash()`：

| 动作 | 行为 |
|:-----|:-----|
| `ACTION_TOGGLE_CLASH` | `StatusClient.currentProfile()` 非空则停止，否则启动 |
| `ACTION_START_CLASH` | 已在跑只 toast；否则启动 |
| `ACTION_STOP_CLASH` | 停止并 toast |

这里的「在跑」是状态 Provider 返回了当前配置名，不是 `Remote.broadcasts.clashRunning`。启动前先 `withProfile { queryActive() }`。没有当前配置，或当前配置尚未导入时，toast `R.string.no_profile_selected`，不调用 `startClashService()`，也不 toast 已启动。`onCreate` 在查询返回前就 `finish()`，所以这次查询和 toast 用 `applicationContext`，协程不在 `onDestroy` 里取消。已有导入配置时调用 `startClashService()`。若返回 VPN 授权 Intent，只 toast 无法启动 VPN，不拉起授权界面。主界面的开关会拉起授权。两边最后都落到 `util/Clash.kt` 的 `startClashService` / `stopClashService`。

`TileService` 是另一条控制路径，也不经过 `ExternalControlActivity` 或 `MainDesign`。点击时磁贴为 inactive 就 `startClashService()`，为 active 就 `stopClashService()`，返回的授权 Intent 被忽略。开始监听时用 `StatusClient` 取当前配置名，并注册隧道起停、配置加载和服务重建广播；磁贴标签是配置名，空则用启动名称。

`RestartReceiver` 在清单里导出，但 `android:enabled` 默认是 `false`。`AppSettingsActivity.autoRestart` 用 `PackageManager.setComponentEnabledSetting` 开关这个组件。收到 `BOOT_COMPLETED` 或 `MY_PACKAGE_REPLACED` 时，若 `StatusProvider.shouldStartClashOnBoot` 为真，调用 `startClashService()`。

`DialerReceiver` 导出，匹配 `android_secret_code` 主机 `252746382`，收到后以 `NEW_TASK` 打开 `MainActivity`。

## 配置、代理与文件

`ProfilesShell` 持有 `ProfilesDesign`。进入前台或配置变更时 `withProfile { queryAll() }` 刷新列表。请求包括新建、更新单个、更新全部已导入且类型不是 `File` 的配置、删除、编辑、复制、设为当前，以及 `setProfileOrder`。拖动排序只写本机顺序文件。未导入的配置不能直接设为当前，改为让 Design 要求保存。`ProfilesActivity` 自己在前台时，更新完成或失败由它 toast。前台时每分钟让 Design 更新经过时间。

配置页的加号弹出锚点菜单，不再打开新建配置页。菜单第一组是文件、URL、二维码，第二组是能响应 `Intents.ACTION_PROVIDE_URL` 的外部 Activity，没有外部提供者时不显示这一组。每次打开菜单都重新查询外部提供者，查询失败只当作没有外部提供者。文件、URL 和外部提供者都走 `withProfile { create }`，然后打开 `PropertiesActivity`。二维码走 `ScanQRCode`，成功则按 URL 类型创建并把扫到的字符串当作 source；取消不做任何事，没有相机权限或扫描失败在配置页上用带「详情」的 toast。外部提供者用 `startActivityForResult` 取回 `data` URI 和可选名称，类型为 `Profile.Type.External`。属性页返回后刷新配置列表。

`PropertiesActivity` 从 Intent 取 UUID，`queryByUUID` 失败就结束。离开时 `defer` 调用 `release`。`ActivityStop` 且用户没有取消、字段相对进入时有变化，则 `patch` 名称、来源、间隔和 `ageSecretKey`。提交时再校验名称和非文件类型的来源，然后 `patch` 加 `commit`。浏览文件打开带同一 UUID 的 `FilesActivity`。服务重建则结束本页。返回键在有未保存修改时先问是否放弃。

`FilesActivity` 用 `FilesClient` 列 `:background` 里文件 Provider 的文档。根文档 id 是配置 UUID。`configurationEditable` 是 `profile.type != Url`，只决定根目录上已有文件的导入。新建只在子目录出现，根上不发 `ImportFile(null)`。从子目录返回且栈变空时，立刻把界面标成根目录，新建随之消失，然后才 `fetch`。栈空时收到 `ImportFile(null)` 直接返回，不调用 `stack.last()`。栈顶目录用 `list`；根目录若存在空的 `config.yaml`，列表只保留这一项。请求包括进入目录、查看、删除、重命名、导入和导出。返回键发送 `PopStack`，栈空才结束。

`ProxyShell` 读取代理组名（是否排除不可选由 `uiStore.proxyExcludeNotSelectable` 决定）。组名列表为空时不发送 `Reload`。否则初始下标依次看这次要保留的组、旋转保存的组、`EXTRA_PROXY_GROUP`、`uiStore.proxyLastGroup`，再退回 0。只有用户在组列表里点选才写 `proxyLastGroup`。单独打开 `ProxyActivity` 时，点选还会改写 Intent 里的 extra；组名变了就重新打开本 Activity 并结束当前实例，带上当时显示的组名，这一步不写 `proxyLastGroup`。嵌在主界面里时，组名变了就在原地换一页，仍显示原来的组。每个组有重载序号和点选序号。收到该组的 `Reload` 时重载序号加一；查询返回时序号已变，就丢掉整段结果，不调用 `updateGroup`，也不改 `now`。点选序号只在 `Select` 时加一。查询返回时重载序号仍有效、但点选序号已变，则列表照常画上，`now` 保留这次点选。`Select` 仍调用 `patchSelector`，把本地 `now` 设成点中的名字，再 `requestRedrawVisible`。重载用许可数为 10 的信号量调用 `queryProxyGroup`。延迟测试调用 `healthCheck` 后直接开始该组的下一次查询，不经过通道再发一条 `Reload`。只有这次查询真正画上才能清掉测速进度。被丢掉的旧结果不清进度，也不消费这张条子。打开、排序和切组的重载保持进度。界面不再发送 `PatchMode` 或 `ReloadAll`。隧道起停或配置加载时重新查询外部资源；查询失败则这一段显示不可用。

节点页在隧道连接前就会创建 `ProxyShell`，那时组名列表可能还是空的。`run()` 不把一次空会话当成结束：`runSession()` 返回后仍活跃就再建一次。内嵌页收到 `onProfileLoaded()` 时重新查询组名；列表和当前会话不同，就换一个新会话，并优先保留原组，原组不在时取新列表的第一项。这样配置加载完成后节点页会自己出现组，不需要切走再切回。空列表仍不发送 `Reload`。

`ProvidersActivity` 用 `queryProviders()` 的排序结果建 `ProvidersDesign`。更新请求调用 `updateProvider`。配置加载后列表变了，同样重新打开自身。前台时每分钟更新经过时间。

## 设置

`SettingsShell` 只做转发：应用、网络、覆盖、Meta 功能、WebDAV、本地 MCP、关于。关于打开 `AboutActivity`，版本行是 `vX.Y.Z (Build N)`。`SettingsActivity` 单独打开时走同一个 Shell。

`McpSettingsActivity` 用 `McpSettingsDesign` 读写 `ServiceStore` 的 `mcpEnabled`、`mcpPort`、`mcpToken` 和 `mcpStatus`。打开开关时若令牌为空，先生成令牌，再启动 `McpService`；关闭时停止该服务。地址和令牌都可以复制。MCP 服务自身没有 Activity，监听和工具都在 `:background`，协议见 [本地 MCP](../../docs/systems/mcp/INDEX.md)。

`AppSettingsActivity` 把 `UiStore`、`ServiceStore`、当前是否在跑，以及隐藏图标的回调交给 `AppSettingsDesign`。隧道起停或服务重建时 `recreate()`。Design 发出请求时，已创建的 Activity 全部 `recreate()`。隐藏图标切换 `MainActivityAlias` 的组件启用状态。开机重启即上一节的 `RestartReceiver`。

`NetworkSettingsActivity` 只改 VPN 开关、私网绕过、DNS 劫持、允许绕过、IPv6、系统代理和协议栈。访问控制模式和全局名单不在这一页，入口是应用分流的「默认回退」。条件与名单语义见 [访问控制](../../docs/systems/access-control/INDEX.md) 和 [WebDAV](../../docs/systems/webdav/INDEX.md)。

`OverrideSettingsActivity` 读出 `OverrideSlot.Persist`，`defer` 在离开时 `patchOverride` 写回。确认重置后，把 `defer` 换成 `clearOverride` 再结束。

`MetaFeatureSettingsActivity` 同样读写 `OverrideSlot.Persist`。配置页文件分段打开它时带 `EXTRA_FOCUS_GEO`，页面滚到 Geo 文件。从设置进入不带这个 extra，停在页顶。另外可以用 `GetContent` 导入地理文件。扩展名必须是 `.metadb`、`.db`、`.dat` 或 `.mmdb`，否则对话框拒绝。接受的文件按类型写成 `clashDir` 下的 `geoip`、`geosite`、`country` 或 `ASN` 再加原扩展名。`clashDir` 是 `filesDir/clash`。

`WebdavSettingsActivity` 用 `WebdavSettingsStore` 和 `WebdavLocalState.lastMessage()` 建界面。测试、同步、拉取、推送分别对应 `WebdavSync` 的四个动作，经 `withProfile { webdavPerform }` 执行。进行中再次点击会被 `busy` 丢掉。同步或测试进行中拨开自动同步时，开关和 `store.autoSync` 都保持关。不忙时从关到开仍写上并发送 `Sync`，不确认。端点、合并和默认常量见 [WebDAV](../../docs/systems/webdav/INDEX.md)，本文不抄那些字面量。

`AppsStrategyShell` 直接读写 `ServiceStore` 的策略列表、生效 UUID 和全局回退模式：新建、改名、改模式、删除、设为生效，以及默认回退卡的模式菜单。点选一份策略会打开带该 UUID 的 `AccessControlActivity`；默认回退的应用图标打开不带 UUID 的名单页。变更后按 [WebDAV](../../docs/systems/webdav/INDEX.md) 的自动同步条件调用 `webdavPerform(ACTION_SCHEDULE)`。保存位置和谁在驱动隧道见 [访问控制](../../docs/systems/access-control/INDEX.md)。`AppsStrategyActivity` 单独打开时走同一个 Shell。

`AccessControlActivity` 的返回键直接 `finish()`。`defer` 把当前选中包名写入策略或全局名单，再按该文档所述决定是否在返回之后重载隧道和同步。从策略进入且名称非空时，窗口标题设成该策略的名称。列表加载调用 `loadAccessControlApps`。国内包名来自 `DomesticAppCatalog`，浏览器包名来自 `BrowserAppCatalog`。点选规则不在这里重复。

## 日志、关于与异常页

`LogsActivity` 和 `LogcatActivity` 的捕获、读文件和导出见 [日志捕获](logcat.md)。

`AboutActivity` 用 `AboutDesign`。`Open(url)` 用 `ACTION_VIEW` 打开链接；没有邮件应用或浏览器时就地提示对应文案，并允许复制地址。`Copy(text)` 写入剪贴板并提示「已复制」。关于页的开发者、社区、应用信息和引用开源清单来自 `AboutContent`，Activity 不另存一份。身份头图标从当前安装包读取，与启动图标一致。二维码资源随包打包，由关于页弹窗展示。返回键先关闭引用开源二级页，再结束 Activity；二级页返回后焦点回到「引用开源」行。

`ApkBrokenActivity` 由 `Remote.verifyApp` 在安装包里找不到匹配 ABI 的 `libclash.so` 时拉起。Design 请求里的 URL 用 `ACTION_VIEW` 打开。

`AppCrashedActivity` 是 `singleTask`。`Remote` 在 `RemoteService` 短时间连续断开或绑定失败时，先结束已创建的 Activity，再以 `NEW_TASK` 打开它。页面用 `SystemLogcat.dumpCrash()` 填日志。这是系统 `logcat` 命令，不是 `LogcatService` 写的文件。

## 清单里的导出入口

不逐条列权限。导出的组件是：

| 组件 | 导出时做什么 |
|:-----|:-------------|
| `MainActivity` | 应用内主界面。另有 `MAIN`，但没有启动器 category |
| `MainActivityAlias` | `LAUNCHER` 与 `LEANBACK_LAUNCHER`，目标是 `MainActivity` |
| `ExternalControlActivity` | `VIEW`（`clash` / `clashmeta` 的 `install-config`）以及清单里写死的启动、停止、切换动作 |
| `TileService` | 快捷设置磁贴，要求 `BIND_QUICK_SETTINGS_TILE` |
| `RestartReceiver` | 开机与覆盖安装；默认禁用 |
| `DialerReceiver` | 秘密码，打开主界面 |

其余 Activity 和 `LogcatService` 的 `exported` 都是 `false`。`LogcatService` 的前台类型是 `specialUse`。
