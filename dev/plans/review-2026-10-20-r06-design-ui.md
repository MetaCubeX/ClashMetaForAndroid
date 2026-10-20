---
title: "审查发现 r06 design-ui"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "发现 5 条：配置用量文案与进度条不一致、供应商零时间戳、脚本模式无选中、分组选择时测速被挡住、弹层关闭动画丢掉后续点击。"
---

# 审查发现 r06 design-ui

范围只含 `design/.../ios`、`nota`、`universe` 的活码。下面 5 条都是这次读到的行为问题。

## r06-1

- 编号：r06-1
- 严重度：中
- 症状：订阅下载量是 0 或 1、但上传和总量已经有数时，配置行底下有用量条，字幕里却没有已用/总量。

```245:250:design/src/main/java/com/github/kr328/clash/design/ios/ProfilesScreen.kt
private fun trafficLine(profile: Profile, fraction: Float?): String? {
    if (profile.download < 2) return null
    val used = (profile.download + profile.upload).toBytesString()
    if (profile.total <= 1 || fraction == null) return used
    return used + "/" + profile.total.toBytesString() + " · " + percentText(fraction)
}
```

```272:278:design/src/main/java/com/github/kr328/clash/design/ios/ProfilesScreen.kt
private fun usageFraction(profile: Profile): Float? {
    if (profile.total < 2) return null
    val divisor = profile.total / 1000
    if (divisor == 0L) return null
    val ticks = (profile.download + profile.upload) / divisor
    return (ticks.toFloat() / 1000f).coerceIn(0f, 1f)
}
```

进度条用 `usageFraction`（看 `total` 和上传+下载），字幕用 `trafficLine`（`download < 2` 直接返回空）。`ProfileRow` 在 `fraction != null` 时仍然画出 `IosFractionBar`（同文件 173–181 行）。下载为 0、上传大于 0、总量不小于 1000 时，用户只看见一条没有数字的用量条。

方向：文案和进度条共用同一套「有没有用量」判断，不要单独把 `download < 2` 当成没有数据。

## r06-2

- 编号：r06-2
- 严重度：中
- 症状：还没更新过的外部供应商（`updatedAt <= 0`）右侧会显示成距今几十年，而不是不显示时间。

```127:132:design/src/main/java/com/github/kr328/clash/design/ios/ProvidersScreen.kt
    IosListRow(
        row = IosDetail(
            title = row.provider.name,
            subtitle = providerTypeLabel(row.provider),
            value = if (inline) null else (now - row.updatedAt).elapsedIntervalString(context),
```

同目录里，文件页和配置页都把 `<= 0` 当成没有时间：`FilesScreen.kt` 81–85 行在 `lastModified <= 0` 时不显示间隔，`ProfilesScreen.kt` 227–230 行在 `updatedAt <= 0` 时不显示「更新于」。供应商行只排除了 Inline，非 Inline 且 `updatedAt` 为 0 时会用 `now - 0` 去格式化。

方向：`updatedAt <= 0` 时不要减 `now`，与文件页、配置页一样留空。

## r06-3

- 编号：r06-3
- 严重度：中
- 症状：隧道模式是 Script 时，首页三个模式按钮都没有选中，正在运行时代理行也不显示脚本模式。

```68:79:design/src/main/java/com/github/kr328/clash/design/ios/HomeScreen.kt
    val modes = listOf(
        TunnelState.Mode.Rule to stringResource(R.string.mode_rule_short),
        TunnelState.Mode.Global to stringResource(R.string.mode_global_short),
        TunnelState.Mode.Direct to stringResource(R.string.mode_direct_short),
    )
    val modeSummary = when (state.mode) {
        TunnelState.Mode.Rule -> stringResource(R.string.mode_rule_summary)
        TunnelState.Mode.Global -> stringResource(R.string.mode_global_summary)
        TunnelState.Mode.Direct -> stringResource(R.string.mode_direct_summary)
        TunnelState.Mode.Script -> null
    }
    val selected = modes.indexOfFirst { it.first == state.mode }
```

```86:91:design/src/main/java/com/github/kr328/clash/design/ios/HomeScreen.kt
                value = if (state.running) {
                    modes.firstOrNull { it.first == state.mode }?.second
                        ?: stringResource(R.string.mode)
                } else {
                    stringResource(R.string.proxy_after_connect)
                },
```

`selected` 在 Script 时是 -1，`IosModePicker` 没有任何一项 `index == selected`。代理行的值也找不到短标签，退回通用的「模式」文案。配置若以脚本模式运行，用户看到的是未选中，点任意一个按钮还会通过 `onMode` 把模式改掉。

方向：脚本模式要在模式条上有当前态，或单独标出当前模式，避免看起来没有选中。

## r06-4

- 编号：r06-4
- 严重度：中
- 症状：分组选择器打开时，导航栏测速仍然可点；测速打在进入选择器之前的组上，进度行被选择器盖住。

```98:104:design/src/main/java/com/github/kr328/clash/design/ios/ProxyScreen.kt
                if (hasGroups && !testing) {
                    IosNavIcon(
                        icon = painterResource(R.drawable.ic_baseline_flash_on),
                        description = stringResource(R.string.delay_test),
                        onClick = onUrlTest,
                    )
                }
```

```115:127:design/src/main/java/com/github/kr328/clash/design/ios/ProxyScreen.kt
            picking -> {
                GroupPicker(
                    names = groupNames,
                    current = current,
                    query = groupQuery,
                    onQuery = { groupQuery = it },
                    onPick = { index ->
                        picking = false
                        groupQuery = ""
                        onPickGroup(index)
                    },
                )
            }
```

```153:155:design/src/main/java/com/github/kr328/clash/design/ios/ProxyScreen.kt
                    if (testing) {
                        IosProgressRow(stringResource(R.string.delay_test))
                    }
```

测速按钮挂在脚手架尾部，不看 `picking`。进度行只在 `picking == false` 的分支里。选择器打开后点测速会调用 `onUrlTest()`，图标会因 `testing` 消失，但「延迟测试」进度和结果都在被盖住的列表里；此时 `current` 还没变成列表里正看着的那一组。

方向：选择器打开时不要发测速，或把进度放到选择器同一层。

## r06-5

- 编号：r06-5
- 严重度：中
- 症状：底部弹层开始关闭后的约 250ms 里，按钮看起来还能点，但后一次点击被丢掉。先点删除再点取消，删除仍会执行。

```511:530:design/src/main/java/com/github/kr328/clash/design/ios/IosControls.kt
    val requestClose = remember {
        { after: () -> Unit ->
            if (!closingState.value) {
                followUp.value = after
                closingState.value = true
            }
        }
    }
    val closing = closingState.value
    val fraction = progress.value
    val slidePx = ((1f - fraction) * travel).roundToInt()
    LaunchedEffect(closing) {
        progress.animateTo(
            targetValue = if (closing) 0f else 1f,
            animationSpec = tween(durationMillis = SheetMotionMs, easing = FastOutSlowInEasing),
        )
        if (closing) {
            followUp.value?.invoke()
            onDismissState.value()
        }
    }
```

```345:360:design/src/main/java/com/github/kr328/clash/design/ios/IosControls.kt
                IosSheetButton(
                    title = action.title,
                    color = if (action.destructive) colors.red else colors.blue,
                    marked = action.marked,
                    onClick = { dismiss(action.onClick) },
                )
            }
        }
        if (!cancelLabel.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            IosSheetCard {
                IosSheetButton(
                    title = cancelLabel,
                    color = colors.blue,
                    onClick = { dismiss {} },
                )
```

`SheetMotionMs` 是 250。操作要等关闭动画结束才调用 `followUp`。`closing` 一旦为真，再次 `requestClose` 不会改 `followUp`，按钮也没有停用。配置删除走的就是这个动作表（`ProfilesScreen.kt` 206 行的破坏性删除）。用户点了删除后在动画期间点取消，取消的空回调被丢掉，动画结束仍会 `onDelete`。

方向：关闭一开始就停用按钮，或让后一次点击覆盖 `followUp`；取消必须能撤掉还没执行的删除。
