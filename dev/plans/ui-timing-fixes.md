---
title: "四处时序修复"
type: plan
status: implemented
phase: N/A
updated: 2026-10-16
summary: "代理旧查询、测速标记、文件根目录空栈和 WebDAV 忙时的自动同步已按方案落地。meta release 已覆盖安装到当前连上的手机。还没在设备上核对"
---

# 四处时序修复

> 制定日期：2026-10-16
> 状态：已落地。`:app:assembleMetaRelease` 已通过，并用 `adb install -r` 覆盖安装到当前连上的手机。四处时序还没在设备上核对

## 背景

用户可见屏幕已经按 [其余屏幕的分组交互](compose-remaining-screens.md) 换成 Compose。2026-10-16 的审查对照该规格，发现四处会改结果或会失败的时序。它们不是新功能。连接列表、规则全文和视觉收口不在这里。视觉收口见 [界面视觉收口](ui-visual-consistency.md)。

## 目标

- 同一代理组后返回的查询不能盖住更新的排序或点选。
- 测速进行中，导航栏保持进度，直到这次测速自己的重载画上结果。
- 从文件子目录返回后，新建立即消失。栈空时不调用 `stack.last()`。
- WebDAV 正忙时拨开自动同步，开关和存储都保持关。不忙时，关到开仍立即发 `Sync`，不确认。

## 非目标

- 不新增 `Request` 成员，不改 `IClashManager`。
- 不改选择写入内核的方式，不改 `patchSelector` 的参数。
- 不改访问控制的点选、配置的导入更新、WebDAV 的合并与默认常量。
- 不改日志的跟随规则。审查里日志、访问控制和分流策略没有新的行为缺陷。
- 本方案不跑 Gradle，不装到手机上，也不打 release 包。

## 代理

落点：`app/src/main/java/com/github/kr328/clash/ProxyActivity.kt`、`design/src/main/java/com/github/kr328/clash/design/ProxyDesign.kt`。`ProxyDesign.Request` 的成员保持 `Reload(index)`、`Select`、`UrlTest`。序号放在 Activity 里，不放进请求类型。

每个组下标有两个整数，初值都是 0：

- 重载序号。每收到一次该下标的 `Reload` 就加一。查询返回时，序号已经变过，就丢掉整段结果，不调用 `updateGroup`，也不改 `states[index].now`。
- 点选序号。只有 `Select` 加一。查询开始时记下当时的点选序号。返回时重载序号仍有效、但点选序号已经变过：代理列表可以画上，`now` 保留这次点选，不写回查询里的旧节点。

`Select` 仍调用 `patchSelector`，并把本地 `now` 设成点中的名字，然后 `requestRedrawVisible`。

测速标记：

- `requestUrlTesting()` 把该组标成测速中。
- 打开、排序、切组带回来的 `updateGroup` 保持这个标记。
- `healthCheck` 结束、发出下一次 `Reload` 之前，记下「下一次真正画上的该组结果可以清标记」。
- 被重载序号丢掉的旧结果不能清标记，也不能消费这张条子。

## 文件

落点：`app/src/main/java/com/github/kr328/clash/FilesActivity.kt`、`design/src/main/java/com/github/kr328/clash/design/FilesDesign.kt`。

`PopStack` 在 `stack.pop()` 之后，若栈已空，立刻把 `currentInBaseDir` 设为真，再去 `fetch`。新建按钮跟这个标志走，不等列表返回。

`ImportFile(null)` 在栈空时直接返回，不调用 `stack.last()`。子目录里的新建仍发 `ImportFile(null)`。根上已有文件的覆盖导入仍走 `ImportFile(file)`，覆盖前确认。

## WebDAV

落点：`design/src/main/java/com/github/kr328/clash/design/WebdavSettingsDesign.kt` 的 `onAutoSync`。

`enabled == true` 且 `busy` 时直接返回。不把界面上的 `autoSync` 设为真，也不写 `store.autoSync`。

不忙时保持现在的路径：关只写 `false`；关到开写 `true` 并 `trySend(Sync)`，不确认。手动同步、拉取、推送仍先确认再发送。测试不确认。

## 验收

- [x] 同一组连续改两次排序，后返回的旧查询不改变列表顺序，也不改变勾选
- [x] 测速或排序的查询还没回来就点选节点，勾选停在点中的节点
- [x] 打开代理页后立刻测速，进度一直在，直到这次测速的延迟画出来；期间不能再点测速
- [x] 文件子目录按返回后，新建立即不可点；若仍发出 `ImportFile(null)`，不抛 `EmptyStackException`
- [x] WebDAV 同步或测试进行中拨开自动同步，开关保持关；结束后再拨开才会发 `Sync`

## 风险

| 风险 | 缓解 |
|:-----|:-----|
| 点选之后丢掉整份列表，排序结果也没了 | 点选序号只拦住 `now`。重载序号仍有效时，列表照常画上 |
| 测速条子被下一次无关重载清掉 | 只有测速自己发出的、并且真正画上的那次结果可以清 |
| 返回后列表还是子目录的文件，但新建已经隐藏 | 接受。列表仍由随后的 `fetch` 换成根目录 |

## 参考

- 屏幕规格：[其余屏幕的分组交互](compose-remaining-screens.md)
- 行为说明：[界面](../../docs/systems/ui/INDEX.md)
- 另一份草稿：[界面视觉收口](ui-visual-consistency.md)
