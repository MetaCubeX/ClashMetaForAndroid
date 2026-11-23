---
title: "当前快照"
type: progress
status: active
phase: N/A
updated: 2026-11-23
summary: "文档流程、壳层、WebDAV 与发布边界已分批提交；WebDAV 跨端验收仍待完成"
---

# 当前快照

**日期**：2026-11-04

**文档流程**：开发流程以 `scripts/wiki/workflow.md` 为准，决定见 [ADR](../../docs/decisions/projectwiki-shared-workflow.md)。`scripts/wiki/` 已与 ProjectWiki 当前工具包对齐。`check-docs-health.py` 与 `wiki check` 都保留，因为一个读模块表和快照行数，一个读链接和需求编号。归档根仍是 `dev/archive/`。文档流程接入提交是 `f0ca294`。`adopted_at` 仍未写入 `docs/wiki.json`。Git 钩子仍要每个克隆自己执行 `git config core.hooksPath scripts/wiki/hooks`。

**跨客户端 WebDAV 实施**（2026-10-28 记录）：方案三路审查完成后，按用户指令并行落了 Android 的 [v1 契约与共享向量](../../docs/systems/webdav/protocol-v1.md)、身份投影/来源迁移、未知字段与远端原包伴存、scope/legacy 隔离、严格只读、条件写能力探测、Pending 暂缓及 apply journal。Android `:service:testMetaDebugUnitTest :app:compileMetaDebugKotlin` 通过，87 tests / 0 failures / 0 errors。用户认可桌面本地 issue 豁免后，正式移入目标后端并接入七个 IPC、typed event、持久 dirty 自动调度、取消 generation、恢复 epoch、可移植 ZIP、provider 运行投影和设置页入口；桌面前端 typecheck、lint、architecture check、34 项 Vitest、Rust check、fmt 和 10 项 WebDAV 聚焦测试通过。本机缓存 Mihomo 已验证离线 HTTP provider，尚非打包内核或安装客户端验收。Service/IPC v2.7.6 不支持 HTTP provider asset 播种，本轮未改第三仓库并明确暂缓。Android 完整配置/策略可恢复事务、故障注入、真实 WebDAV/桌面运行和双端体验尚未完成。具体范围、命令及证据见[活跃实施看板](../parallel/active/webdav-cross-client-implementation.md)；[计划](../plans/webdav-cross-client-sync.md)为 in_progress，[ADR 005](../../docs/decisions/cross-client-webdav-sync.md)保持 proposed。Android 这部分代码在 `be1bb6f`。未安装，未改第三仓库。mihomo pin 在 `8f8cb66`，不属于这次 WebDAV 改动。

**新建配置**：保存时内核要先建暂存目录。旧包用空目录调用 `os.MkdirTemp`，Android 上落到 `/data/local/tmp`，应用不能在那里建 `cmfa-profile-…`，于是报 `permission denied`。暂存目录改到配置目录的上一级，名字以 `.cmfa-profile-` 开头，结束时删除。带 `foss,with_gvisor,cmfa` 标签的 `go test ./native/config/` 通过。`GOTOOLCHAIN=local ./gradlew :app:assembleMetaRelease --offline` 在 1 分 18 秒通过。`cmfa-2.11.35-meta-arm64-v8a-release.apk` 已用 `adb install -r` 覆盖安装到当时连着的四台手机，版本都是 `2.11.35.Meta`，没有签名拒绝。没有在界面上再走一遍新建。

**应用语言**：设置 → 应用 → 界面的第一项是语言。点整行弹出菜单，第一项是跟随系统，后面按 `locales_config.xml` 列出 English、日本語、한국어、Русский、Tiếng Việt、简体中文、繁體中文（香港）、繁體中文（台灣），名称用各语言自己的文字。默认不写偏好，跟随系统，包括 Android 13 的按应用语言。选中后写入 `ui` 的 `app_language` 并重建界面。`:background` 启动时套用同一项；已在运行时收到广播，下一次取文案用新语言，已经显示的通知要等刷新。空值和无法识别的值按跟随系统。`:common:testMetaDebugUnitTest` 的 `AppLanguagesTest`、`:design:compileMetaDebugKotlin`、`:app:compileMetaDebugKotlin` 和 `:app:assembleMetaDebug` 已通过。连接的手机上核对过：菜单第一项是跟随系统并默认打勾，后面是上述八种语言；切到 English 后首页、底栏、设置壳和应用页标题都变成英文；再选跟随系统后，应用页标题是「应用」，语言行是「跟随系统」，首页和底栏回到简体中文。页面标题在重建时按当前语言重新读取。

**切页动效**：`ShellPages` 切底栏 tab 时，新页淡入 200ms，曲线 `CubicBezierEasing(0f, 0f, 0.58f, 1f)`，旧页同帧移出，不做淡出、位移和横滑。仪表盘是 Compose 内容，走 `graphicsLayer`；另外四页在 `AndroidView` 里，图层变换不保证作用到真实 View，改对宿主 `FrameLayout` 用 `ViewPropertyAnimator` 跑同一组参数。动画值用 `remember(tab)` 建，切页首帧就是透明的；首次进入不播。仪表盘收起时尺寸为 0，并用空的 `drawWithContent` 跳过绘制，否则标题上 `requiredSize` 的日志图标会画在窗口左上角，淡入时从透明页面下透出来。`ui-web-demo` 的五个 tab 共用一个滚动容器，切换时按页面 id 记 `scrollTop`，切回来直接恢复。`:design:compileMetaDebugKotlin --offline` 已通过。未在真机上核对切页。

**开源前审查**：工作区、全部历史、工作流和子模块已扫描。没有发现云密钥、私钥块或真实订阅凭据。WebDAV 默认地址、内网地址、用户名和私有目录已清空或换成中性值；所有 locale 的提示和测试夹具同步更新。两个发布工作流不再回显 `signing.properties`，也不再解码覆盖 `release.keystore`。密钥库文件与上游一样固定为仓库根的 `release.keystore`；`scripts/write-signing-properties.py` 只写入三个 secret。Android 备份只保留 `app.xml`、`tips.xml`、`ui.xml`，新增 `data_extraction_rules.xml`；配置库、导入目录、WebDAV 设置和 MCP 令牌都不再随系统备份离开设备。根 README、隐私政策、贡献指南和新增的 `SECURITY.md` 已按本 fork 重写。演示页的品牌、节点名、IP 和服务域名已换成中性示例。`release.keystore` 与上游是同一份文件，继续放在仓库里。mihomo 子模块改为 fork `https://github.com/TIIEHenry/mihomo` 的 `ca-bundle`，tip 是 `e4c61f30d8011aa3eb6a0f8cb46b1d9356018c9a`。`90a5afcc..563a0da4` 的 5 个 bug 和 1 条建议已在 `e4c61f30` 修复；子代理复审没有新问题，并已推到 fork。公开仓库 [AgenticClash](https://github.com/TIIEHenry/AgenticClash) 仍是不带旧历史的快照，gitlink 是 `e4c61f30`。这次分批提交推到开发远程 `TIIEHenry/ClashMetaForAndroid` 的 `main`，不改写那个快照。清单和验收见 [开源前脱敏审查](../plans/review-2026-10-24-open-source-sanitization.md)。

**当前**：壳层四条边界已按 [ADR 004](../../docs/decisions/commit-projection-session.md) 落地，分别在 `8f8cb66`、`be1bb6f`、`ba360f0` 和 `0bdd615`。配置和地理库在完整内容换上之前保留旧文件；访问控制的全选、反选和导入只改可见包；集合草稿随目标 id 作废；未返回的编辑在重建后还在，存储仍只在 `finish()` 写。代理页失败不取消主界面。HTTP 监听成功后才替换旧的。套接字保护失败则这次拨号失败。etag 没变仍跳过配置正文，同一 uuid 的远端用量进入合并。预发布在新 APK 成为 `Prerelease-alpha` 的资产之前不删除旧 Release。`:service:testMetaDebugUnitTest` 里 `WebdavSyncTest` 33 项和 `ProfileDirectoryTest` 3 项通过。`:design:test` 通过。`core/src/main/golang` 下 `app`、`delegate`、`proxy`、`config` 的 `go test` 通过。`:app:compileAlphaDebugKotlin` 当时停在 design 里已有的未解析字符串，后来 design 测试已经通过，app 编译没有再跑。mihomo 子模块的修复在 fork 的 `ca-bundle`。

五个根页面在同一个主界面里，底栏从左到右是节点、配置、仪表盘、分流、设置。点底栏只切换当前页。节点、配置、分流、设置不画返回。系统返回从这四页先回到仪表盘。再往里的页面盖住底栏。隧道停着时在真机上切过这五页，都停在主界面里。从配置页打开生效配置的属性后，底栏不见了，返回仍回到配置页。首页在停止时也能看到流量卡和六格入口。配置页、节点页、应用分流各自有分段。内核没跑时不编造出口地址，也不显示运行时长。首页六格图标在标题左侧。停止时六格收在一屏里。运行中多一张出口卡时，最下面两格仍可能要往上滑。底栏内容区高 56dp，文字 12sp，系统导航条的高度加在内容区下面。选中项是细线图标，线宽 2.25，图标 24dp，后面有一块 48×28 的 12% 灰胶囊。未选中是同一套细线，线宽 1.5，图标 22dp，没有胶囊。按下只把整格前景压暗。首页标题和另外四个根页面一样，是 48dp 栏里的 20sp。

首页：

- 标题右侧是日志、重载。核心没跑时点重载，提示先连接。设置只在底栏。
- 状态卡只放呼吸点、电源键和模式。运行中另有一张出口卡，点整张卡打开快切或节点页。流量卡一直在，速率来自内核采样。没有至少两个采样时不画折线。
- 「正在使用」是应用分流、WebDAV、外部资源、文件与 Geo、配置、代理。分流格写当前名单、模式和个数。文件格写当前配置名、实际存在的 Geo 文件，以及 Age 私钥是否已设置。WebDAV 在有过同步时带上经过时间。外部资源和代理在连接前显示「连接后可用」。

三个分段：

- 节点页是「节点 / 外部资源」。资源列表沿用原来的更新。核心查询失败时显示不可用。没有组时，导航末端不放更多、列表切换和测速。有组时，排序和排除不可选从更多旁边弹出。选择分组时标题改成「选择分组」，并画出返回；当前组是 12% 灰底、半粗标题和右侧单选。深色模式、协议栈、覆写和 Meta 的短选项、日志级别，也从当前行旁边弹出，当前项打勾。
- 配置页是「配置 / 文件」。预览出口按分组、节点、外部资源分段；组先收起，点开才列成员。节点段只列文件里的内联节点，协议在行尾，不再和每个组的成员叠成一张长表。点生效中的配置打开属性。点未生效的配置仍是激活。生效中的配置整行 12% 灰底、标题半粗，不另放标签。更多在标题右侧，更新和分享在副标题右侧。更新、全部更新、外部资源更新和首页重载一点就转，转到这次操作结束。多于一份时，行首握把长按后可以拖动排序，顺序只留在本机。文件页里每份已导入配置写根目录条目个数和文件大小；每个实际存在的 Geo 文件单独一行，写大小和修改经过时间，点它打开 Meta 并滚到 Geo 文件。生效配置另有 Age 私钥是否已设置。
- 应用分流是「策略 / 应用集 / 访问列表」。生效策略是 12% 灰底、半粗标题和右侧单选，不另放「生效中」。再点已生效的自定义策略保持选中。模式和内置、自定义都是灰底标签。策略卡在名单完整包含某个应用集时写「覆盖」。首页六格的说明在第一个间隔处换行，避免把词拆开。访问控制的排序留在更多菜单里，点开后换成四个带勾选项。编辑应用集分「基本信息 / 成员应用」两段，名称、说明和固定状态不再和成员列表抢高度；成员表独占剩余空间，保存固定在底部。删除在标题旁，确认仍是贴底框。只改内置集合的名称或说明不会固定成员；改成员会先说明固定，并可恢复自动计算。从应用分流进入编辑，关闭后回到应用分流。自定义集合名称为空时不保存。应用集卡显示真实应用名，自定义集可以删除，编辑打开原来的成员编辑器，并可以写说明。访问列表打开当前策略的访问控制。

应用集说明存在 `AppSetData.description`。旧的 `settings/app_sets.json` 缺这个字段时当作空字符串；缺 `membershipFrozen` 时，已保存的内置集合仍视为已固定。运行时长记在服务进程启动隧道的时刻。直连，或当前组没有选中节点时，不画出口卡。真机运行中看到计时从约 1 分钟走到 6 分钟，出口卡是当前节点、组名和测速延迟，点整张卡打开快切。这份配置没有外部资源，首页和节点页的资源分段都是空列表。看完隧道已停。

WebDAV 同步少传正文。etag 与上次一致就不再下载那份配置。订阅流量写在 `_sync.json`，正文没变不重传整份配置。空文件和解析失败的远端文件不删。两台设备改同一份时按修订号、时间、设备号和内容哈希收敛。412 只重试冲突的那个文件。自动同步在改动后防抖再推；服务启动后的周期任务只拉取。设置页的密码行只显示已设置或未设置，编辑框不回填已存密码；留空确认保持原值，点「清除」才清空。三条路径已在小米上实测。密文读不出来时 `WebdavCipher.open` 返回 null，界面写「无法读取，请重新输入」，地址、用户名和远端路径保留；无关字段的写入沿用原密文，不会把空密码固化。`WebdavSyncTest` 36 项通过。

应用包名是 `universe.agentic.device.clash`，alpha 和 meta 都不再加后缀。清单里的外部控制 action 改用这个包名。显示名是 `AgenticClash`，alpha flavor 是 `AgenticClash Alpha`。定制界面的源码包名是 `universe.agentic.clash`，源码在 `design/src/main/java/universe/agentic/clash/`。Activity 和 `*Design` 仍在 `com.github.kr328.clash`。共享控件仍在 `nota.ui` 和 `universe.agentic.ui.search`。主题组合函数从 `ClashMetaXTheme` 改为 `AgenticClashTheme`。

设置的「高级」里有本地 MCP。页面依次是监听开关、用途说明、地址、令牌和八个工具的说明。开关打开后，`:background` 里的前台服务只听 `127.0.0.1` 的 `POST /mcp`，用 `ServiceStore` 里的令牌校验。工具可以看状态、列节点和配置、切换节点或配置，以及重载、启动、停止隧道。启动不会自己弹出 VPN 同意框。行为见 [本地 MCP](../../docs/systems/mcp/INDEX.md)。

访问控制的滑动托选在 `DragSelect.kt`，接在应用列表和编辑应用集的成员列表右侧复选框列。手指从最后一列开始拖动，第一行决定这次是选中还是取消，中间经过的行连续应用，靠近上下边缘会滚动。从更左边开始仍是普通滚动。本期没有新增手势实现。

访问控制在系统应用和国内常用软件下面多了一行内置集合「浏览器」。成员来自 `browser_packages.txt`，国内和国外的网页浏览器都在里面，例如 Via、夸克、Chrome、Firefox。这些包不再计入系统集合或国内集合。点选浏览器会一次选中当前已安装的成员。此前已经勾过的应用仍保持勾选。未安装任何浏览器时这一行显示未安装，不能点。

设置 > 网络不再有访问控制模式和访问控制包列表，只剩 VPN 开关、私网绕过、DNS 劫持、允许绕过、IPv6、系统代理和协议栈。全局模式和全局名单只在应用分流的「默认回退」里编辑：模式从卡片的更多菜单选，应用图标打开不带 UUID 的名单页。有生效策略时，卡下写「仅在未选择分流策略时生效」。没有生效策略时，改全局模式或名单会重载已运行的隧道；有生效策略时不重载。再点已生效的自定义策略保持选中。失效的策略 UUID 不再显示成生效项。`resolveAccessControl` 统一决定用策略还是全局回退。

设置 → 关于是独立页面，按新规范固定四段：应用身份头、开发者、社区、应用信息。身份头是启动图标、名称、`vX.Y.Z (Build N)` 和一句介绍，不含更新操作；图标由 `AboutActivity` 从安装包读取。开发者组依次是 `Clarence Henry`、一句话介绍、邮箱 `anyvoid@qq.com`、酷安、公众号「超越观」；邮箱发 `mailto:`，酷安打开外链，公众号整行复制并提示「已复制」。公众号与 QQ 频道下面各有一行「显示二维码」，弹窗展示随包资源，关闭后焦点回到该行。社区组是 Telegram 频道和 QQ 频道 `pd72903491`；本应用没有 QQ 群聊，整行省略。应用信息组是开放源代码、开源许可 `GPL-3.0`、引用开源；引用开源进入二级页，按名称排序列出 ClashMetaForAndroid 与 mihomo 的作者和许可证，AOSP、AndroidX 与 AgenticUILibrary 不列入。常量只在 `AboutContent`，`AboutContentTest` 校验分组顺序、取值和二维码归属。帮助页已移除。检查更新与更新日志不在关于页。安装包损坏页使用本 fork 的项目地址。

配置页的加号从按钮旁弹出菜单，不再打开新建配置页。第一组是文件、URL、二维码，第二组是外部提供者，没有外部提供者时不显示。选完来源后由 `ProfilesShell` 创建并打开属性页，返回时刷新列表。新建配置页和它的 Design、Compose 文件已删除。

配置属性的自动更新改为预设下拉：已禁用、15 分钟、30 分钟、1 小时、6 小时、12 小时、24 小时，末尾「自定义…」仍打开原来的分钟输入。选中预设立即写回间隔，整小时在行尾按小时显示。新建配置后进入同一属性页，添加和编辑都能用。

每个配置的标题行右侧有「预览出口」按钮。它只读该配置的 `config.yaml`，用配置自己的 Age 私钥解密，列出组、成员、节点和外部资源；远程 provider 只显示名字和类型，`include-all` 系列只标成「包含全部节点」。预览不加载内核、不改当前隧道、不套覆盖。面板走底部弹层，长列表可以滚动。

节点页在隧道连上后自动出现组。此前 `ProxyShell` 在配置加载前拿到空组列表就结束会话，`ProfileLoaded` 也没有当前组可恢复。现在空会话会重建，组名变化时保留原组，原组不在就取新列表第一项。真机上看到 `PROXY`、`Selector`、`URLTest`、节点列表和 `DIRECT`，停止再启动隧道后仍能恢复。隧道没启动时节点页为空是预期行为。

底部弹层不再用「已画出的高度超出多少」反向增加底部内边距。旧写法让高度和内边距互相推动，打开属性页的自动更新菜单时主线程卡在 Compose 测量和绘制，系统报 `Input dispatching timed out`。现在最大高度在布局时一次算出，弹层仍可滚动。修复后打开「自定义…」正常，设备没有新的 ANR 记录。

配置更新的系统通知改为只跟后台更新走。配置页的单个更新和全部更新在 `IProfileManager.update` 上传 `notify = false`，`ProfileWorker` 仍广播完成或失败，页面照常结束转圈并弹 toast，但不再发进行中、成功或失败通知。后台闹钟和失败重试保留缺省的 `notify = true`，继续发通知。标记走 `EXTRA_PROFILE_UPDATE_NOTIFY`。

设置根页的应用行写当前深色模式，网络行写协议栈，WebDAV 行与首页同一句状态，本地 MCP 行写已关闭、正在监听或未在监听。覆写和 Meta 仍各有一句说明。关于和 WebDAV 的测试、同步、拉取、推送不画进入页面的箭头。关于页外链行尾是外链标记，复制行尾是复制图标，引用开源行尾是箭头。配置页文件分段的 Geo 行打开 Meta 后滚到 Geo 文件。

设置项短选项统一走 Compose 的 `IosPopupMenu`：整行做锚点，右侧显示当前值，当前项打勾，选中即写入。旧 `selectableList`、`PopupListAdapter`、`util/ListView.kt` 和 `dialog_menu_min_width` 没有调用方，已删除；`design` 编译通过。
