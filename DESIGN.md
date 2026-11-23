---
title: "AgenticClash 高层设计"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-30
summary: "UI 进程与 :background 进程的边界，以及壳层写入、名单投影和失败隔离"
---

# 高层设计

本应用把 mihomo 跑在 Android VPN 服务里，并用一套配置界面管理配置文件、代理选择和按应用分流。

## 进程

界面跑在默认进程。`ClashService`、`TunService`、`RemoteService`、配置处理与状态 Provider 声明在 `:background`。界面通过 `service` 模块的远程接口读写配置和隧道状态，不在 UI 进程里加载 `libclash.so` 的业务循环。

`TunService` 持有 `android.net.VpnService`。流量是否进入隧道，由当前配置、访问控制名单，以及可选的应用分流策略决定。有生效策略时用策略的模式和包名；没有时用应用分流页「默认回退」里的全局名单。

## 模块

| 模块 | 职责 |
|:-----|:-----|
| app | Activity、外部控制入口、日志界面 |
| design | 界面布局、列表与设置控件 |
| service | `:background` 服务、配置库、WebDAV、访问控制与分流策略的存储 |
| core | JNI 与 Go 桥，链到 mihomo，产出 `libclash.so` |
| common | 跨模块常量、Store、兼容工具 |
| hideapi | 隐藏 API 的编译期存根 |

模块依赖与阅读入口见 [架构概览](docs/architecture/overview.md)。

## 内核

mihomo 以 Git 子模块放在 `core/src/foss/golang/clash`。`core/src/main/golang` 用 `replace` 指向它，再由 golang-android 插件编进 native 库。子模块的检出约束见 [构建指南](docs/guides/getting-started/build.md)。

## Fork 行为

相对上游界面，本 fork 增加三类用户可见行为：命名的应用分流策略、访问控制里的系统应用、国内应用和浏览器集合、配置与策略的 WebDAV 同步。契约分别见 [访问控制](docs/systems/access-control/INDEX.md) 与 [WebDAV](docs/systems/webdav/INDEX.md)。

产品承诺见 [docs/product/](docs/product/INDEX.md)。这里不写验收步骤。

## 写入与失败

壳层遵守 [ADR 004](docs/decisions/commit-projection-session.md)：

- 正式文件在新内容完整校验之后才替换。失败保留上一份完整内容。
- 访问控制里看得见的名单是投影。全选、反选和剪贴板导入不删掉当前看不见的已选项。
- 未按返回离开的编辑在重建后还在，存储仍只在 `finish()` 时写。
- 代理页的一次失败不取消主界面。新的 HTTP 监听成功后才关掉旧的。套接字保护失败则这次拨号失败。
