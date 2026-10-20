---
title: "app 模块导航"
type: index
status: accepted
phase: N/A
updated: 2026-10-15
summary: "应用模块：Activity、外部控制、日志与国内应用目录"
---

# app

介绍见 [README](../../../app/README.md)。

| 入口 | 路径 |
|:-----|:-----|
| 主界面与活动 | `app/src/main/java/com/github/kr328/clash/` |
| 外部控制 | `ExternalControlActivity.kt` |
| 访问控制加载 | `AccessControlApps.kt`、`DomesticAppCatalog.kt` |
| 国内目录资源 | `app/src/main/res/raw/domestic_packages.txt` |
| 明文流量 | `app/src/main/res/xml/network_security_config.xml` |

所属系统：[界面](../../systems/ui/INDEX.md)、[访问控制](../../systems/access-control/INDEX.md)、[运行时](../../systems/runtime/INDEX.md)。

模块设计：[入口](../../../app/docs/INDEX.md)、[Activity](../../../app/docs/activities.md)、[远程调用](../../../app/docs/remote.md)、[日志捕获](../../../app/docs/logcat.md)。跨模块行为写在所属系统文档里。
