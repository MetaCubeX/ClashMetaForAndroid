---
title: "app 模块导航"
type: index
status: accepted
phase: N/A
updated: 2026-10-24
summary: "应用模块：Activity、外部控制、日志、国内与浏览器应用目录，以及备份范围"
---

# app

介绍见 [README](../../../app/README.md)。

| 入口 | 路径 |
|:-----|:-----|
| 主界面与活动 | `app/src/main/java/com/github/kr328/clash/` |
| 外部控制 | `ExternalControlActivity.kt` |
| 访问控制加载 | `AccessControlApps.kt`、`PackageCatalog.kt`、`AppSetAssembly.kt` |
| 国内目录资源 | `app/src/main/res/raw/domestic_packages.txt` |
| 浏览器目录资源 | `app/src/main/res/raw/browser_packages.txt` |
| 明文流量 | `app/src/main/res/xml/network_security_config.xml` |
| 备份范围 | `app/src/main/res/xml/full_backup_content.xml`、`app/src/main/res/xml/data_extraction_rules.xml` |

所属系统：[界面](../../systems/ui/INDEX.md)、[访问控制](../../systems/access-control/INDEX.md)、[运行时](../../systems/runtime/INDEX.md)。

模块设计：[入口](../../../app/docs/INDEX.md)、[Activity](../../../app/docs/activities.md)、[远程调用](../../../app/docs/remote.md)、[日志捕获](../../../app/docs/logcat.md)。跨模块行为写在所属系统文档里。
