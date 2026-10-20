---
title: "运行时"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: ":background 进程中的 Clash 服务、VpnService 和 libclash.so。启停与重载顺序见专页。"
---

# 运行时

`service` 把隧道放在 `:background`。`ClashService` 管核心生命周期，`TunService` 实现 `android.net.VpnService`。界面进程通过远程接口观察状态和下发重载，不自己持有 VPN。

`core` 提供桥接模型（代理、流量、隧道状态、配置覆盖）和 `libclash.so`。Go 侧入口在 `core/src/main/golang/native`，内核来自子模块。构建约束见 [构建指南](../../guides/getting-started/build.md)。

## 启停

启动、停止和重载的跨模块顺序见 [启停与重载](lifecycle.md)。

## 外部控制

`app` 的 `ExternalControlActivity` 是导出的入口。Manifest 登记了 meta 包名下的启动、停止和切换动作，以及 `clash` / `clashmeta` 的 `install-config` 链接。运行时代码里的动作字符串用当前 `packageName` 拼接，见 `common` 的 `Intents`。meta flavor 下两者一致。

## 重载

访问控制或分流策略保存后，如果这份名单正在驱动已运行的隧道，界面请求重载，而不是在返回路径上等待进程停干净。细节见 [访问控制](../access-control/INDEX.md)。顺序见 [启停与重载](lifecycle.md)。

模块入口：[service](../../modules/service/INDEX.md)、[core](../../modules/core/INDEX.md)、[app](../../modules/app/INDEX.md)。
