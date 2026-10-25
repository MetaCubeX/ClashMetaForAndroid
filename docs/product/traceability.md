---
title: "需求追踪"
type: reference
status: accepted
phase: N/A
updated: 2026-10-25
summary: "每条 PRD 文件对应的规格与代码位置。没有设备验收记录，故需求保持 accepted"
---

# 追踪

下表是承诺在树里的位置，不是一次安装或测试的记录。

| ID | 规格 | 体现 |
|:---|:-----|:-----|
| [PRD-001](requirements/PRD-001.md) | [运行时](../systems/runtime/INDEX.md) | `service` 的 `TunService` / `ClashService`（`:background`），`core` 产出 `libclash.so` |
| [PRD-002](requirements/PRD-002.md) | [配置](../systems/profiles/INDEX.md) | `app` 的 `ProfilesActivity`、`ExternalControlActivity`；`service` 的 `ProfileManager` |
| [PRD-003](requirements/PRD-003.md) | [访问控制](../systems/access-control/INDEX.md) | `AccessControlActivity`、`loadAccessControlApps`、`design` 的 `AppSet` |
| [PRD-004](requirements/PRD-004.md) | [访问控制](../systems/access-control/INDEX.md) | `AppsStrategyConfig` 与 `ServiceStore` 的 `settings/app_lists_configs.json` |
| [PRD-005](requirements/PRD-005.md) | [WebDAV](../systems/webdav/INDEX.md) | `service/.../webdav/`。行为测试在 `service/src/test/.../webdav/` |
| [PRD-006](requirements/PRD-006.md) | [运行时](../systems/runtime/INDEX.md) | `ExternalControlActivity` 与 `common` 的 `Intents` |
| [PRD-007](requirements/PRD-007.md) | [界面](../systems/ui/INDEX.md) | `AppLanguages`、`AppSettingsScreen` 的语言行、`locales_config.xml` |

构建是否成功不作为这些条目的验收。构建步骤见 [构建指南](../guides/getting-started/build.md)。
