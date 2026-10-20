---
title: "访问控制与分流策略"
type: index
status: accepted
phase: N/A
updated: 2026-10-19
summary: "包名名单、系统/国内应用集合，以及命名分流策略的保存位置。策略名单和全局名单用不同标题"
---

# 访问控制与分流策略

全局名单和命名策略都是包名集合。界面在 `app` 的 `AccessControlActivity` 与 `AppsStrategyActivity`，控件在 `design`，存储在 `service` 的 `ServiceStore`。

## 谁会出现在列表里

`loadAccessControlApps` 跳过本应用。其余已安装包要声明 `INTERNET`，或者 UID 小于应用 UID。系统应用记入系统集合。包名出现在国内目录里的，记入国内集合。

「隐藏系统应用」只决定系统应用的行是否显示。两个集合的成员不受这个开关影响。

国内目录是 `app/src/main/res/raw/domestic_packages.txt`，由 `DomesticAppCatalog` 读取。行格式是 `包名`，`#` 后面是注释。包名要匹配目录加载器里的正则：第一段以字母或下划线开头，后续段可以以数字开头。不放进 `assets/`，因为那个目录会被 `:app:clean` 删掉。

集合成员在每次加载列表时按当前安装重新计算。后来安装的应用不会自动被选中。

## 集合点选

`design` 的 `appSetMembership` / `applyAppSetToggle`：

- 当前成员全部已选：点一次只取消这些成员。
- 未全选：点一次选中全部当前成员。
- 没有成员：行不可点。
- 部分选中时，勾选框换成 `ic_app_set_indeterminate`。Material Components 1.6.1 的勾选框没有半选状态 API。
- 副标题是已安装数与已选数。

保存下来的仍然是包名集合，不是集合 ID。

## 离开页面

返回回调直接 `finish()`。`defer` 里立刻把包名写入存储，然后返回。隧道重载和 WebDAV 同步由 `scheduleExitWork` 在返回之后做。

写入目标：

- 从某份分流策略进入时，更新那份 `AppsStrategyConfig`。窗口标题是该策略的名称。
- 否则更新全局 `accessControlPackages`。标题是「全局应用名单」。

分流策略页的标题是「应用分流」。网络设置在已有生效策略时，模式和包列表仍显示各自的值，下面另有一句说明当前由该策略接管，改的仍是上面的全局名单。名单上方显示已选数量。

包名没有变化则不重载、不同步。有变化时，只有这份名单正在驱动已运行的隧道才重载：编辑的是生效中的策略，或者当前没有生效策略而编辑的是全局名单。重载调用 `requestClashReload()`。

自动同步仅在已开启、有端点、用户名和密码都非空时发生，超时 60 秒。

## 分流策略的存储

`ServiceStore` 把策略列表写在应用文件目录的 `settings/app_lists_configs.json`。当前生效策略的 UUID 另存在偏好里。`TunService` 在有生效策略时使用那一份包名。

模块入口：[app](../../modules/app/INDEX.md)、[design](../../modules/design/INDEX.md)、[service](../../modules/service/INDEX.md)。
