---
title: "ClashMetaX 高层设计"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-15
summary: "UI 进程与 :background 进程的边界，以及六个 Gradle 模块的职责"
---

# 高层设计

本应用把 mihomo 跑在 Android VPN 服务里，并用一套配置界面管理配置文件、代理选择和按应用分流。

## 进程

界面跑在默认进程。`ClashService`、`TunService`、`RemoteService`、配置处理与状态 Provider 声明在 `:background`。界面通过 `service` 模块的远程接口读写配置和隧道状态，不在 UI 进程里加载 `libclash.so` 的业务循环。

`TunService` 持有 `android.net.VpnService`。流量是否进入隧道，由当前配置、访问控制名单，以及可选的应用分流策略决定。

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

相对上游界面，本 fork 增加三类用户可见行为：命名的应用分流策略、访问控制里的系统/国内应用集合、配置与策略的 WebDAV 同步。契约分别见 [访问控制](docs/systems/access-control/INDEX.md) 与 [WebDAV](docs/systems/webdav/INDEX.md)。

产品承诺见 [docs/product/](docs/product/INDEX.md)。这里不写验收步骤。
