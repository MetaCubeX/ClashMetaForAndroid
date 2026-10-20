---
title: "审查发现 r03 common"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "common 有 5 条：动态广播默认导出、PendingIntent 可变标志在 API 31 以下被写成不可变、浅色系统栏在未 attach 时被丢弃、刘海模式未提交给 WindowManager、ticker 退出时不关闭 Channel。"
---

# 审查发现 r03 common

范围：`common` 下 26 个 Kotlin 源文件。以下均来自当前活码。

## r03-1

- 严重度：高
- 症状：Android 13 及以上，`registerReceiverCompat` 在未传入 `permission`（默认就是 null）时把动态广播注册为 `RECEIVER_EXPORTED`。其他应用只要构造匹配 `IntentFilter` 的广播就能投递进来。传入 permission 时又固定为 `RECEIVER_NOT_EXPORTED`，本应用以外的发送方即使持有该权限也不会被送达。

```31:36:common/src/main/java/com/github/kr328/clash/common/compat/Context.kt
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        registerReceiver(receiver, filter, permission, handler,
            if (permission == null) Context.RECEIVER_EXPORTED else Context.RECEIVER_NOT_EXPORTED
        )
    else
        registerReceiver(receiver, filter, permission, handler)
```

- 为什么是问题：`RECEIVER_EXPORTED` 允许其他 UID 向该接收器发广播。默认参数不带 permission，接收器若处理启停或配置类动作，外部应用无需权限即可触发，属于越权。`RECEIVER_NOT_EXPORTED` 只按同一应用 UID 放行，permission 不能把其他应用重新放进来，需要跨应用、带权限发送的广播会被系统直接丢掉。
- 方向：单独增加 exported 参数，默认 `RECEIVER_NOT_EXPORTED`，只有确定要收外部或系统广播时才导出。

## r03-2

- 严重度：中
- 症状：`mutable = true` 在 API 24 到 API 30 上得到的仍是 `FLAG_IMMUTABLE`。调用方随后 `send` 时填入的 extras 不会写进这个 PendingIntent，依赖可变 PendingIntent 的通知回复、动作按钮或更新 extras 不生效。

```5:15:common/src/main/java/com/github/kr328/clash/common/compat/Intents.kt
fun pendingIntentFlags(flags: Int, mutable: Boolean = false): Int {
    return if (Build.VERSION.SDK_INT >= 24) {
        if (Build.VERSION.SDK_INT > 30 && mutable) {
            flags or PendingIntent.FLAG_MUTABLE
        } else {
            flags or PendingIntent.FLAG_IMMUTABLE
        }
    } else {
        flags
    }
}
```

- 为什么是问题：`FLAG_MUTABLE` 从 API 31 才存在，所以 API 31 以下应保持调用方原始 flags（历史上默认可变）。现在 `SDK_INT > 30 && mutable` 为假时一律或上 `FLAG_IMMUTABLE`，API 23 起该标志就会让 fill-in 被忽略。API 24–30 上请求可变，实际得到不可变。
- 方向：仅在 API 31 及以上按 `mutable` 或上 `FLAG_MUTABLE` 或 `FLAG_IMMUTABLE`；API 24–30 在 `mutable == true` 时不要加 `FLAG_IMMUTABLE`。

## r03-3

- 严重度：中
- 症状：API 30 及以上，在窗口还没 attach 时设置浅色状态栏或浅色导航栏（例如 Activity.onCreate 里配置窗口），图标颜色不会变。浅色背景下状态栏/导航栏图标仍是浅色，或反过来，图标看不清。

```54:78:common/src/main/java/com/github/kr328/clash/common/compat/UI.kt
        if (value) {
            if (Build.VERSION.SDK_INT >= 30) {
                decorView.windowInsetsController?.apply {
                    setSystemBarsAppearance(
                        APPEARANCE_LIGHT_STATUS_BARS,
                        APPEARANCE_LIGHT_STATUS_BARS
                    )
                }
            } else {
                decorView.systemUiVisibility =
                    decorView.systemUiVisibility or SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        } else {
            if (Build.VERSION.SDK_INT >= 30) {
                decorView.windowInsetsController?.apply {
                    setSystemBarsAppearance(
                        0,
                        APPEARANCE_LIGHT_STATUS_BARS
                    )
                }
            } else {
                decorView.systemUiVisibility =
                    decorView.systemUiVisibility and SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }
```

```86:110:common/src/main/java/com/github/kr328/clash/common/compat/UI.kt
        if (value) {
            if (Build.VERSION.SDK_INT >= 30) {
                decorView.windowInsetsController?.apply {
                    setSystemBarsAppearance(
                        APPEARANCE_LIGHT_NAVIGATION_BARS,
                        APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            } else {
                decorView.systemUiVisibility =
                    decorView.systemUiVisibility or SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        } else {
            if (Build.VERSION.SDK_INT >= 30) {
                decorView.windowInsetsController?.apply {
                    setSystemBarsAppearance(
                        0,
                        APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            } else {
                decorView.systemUiVisibility =
                    decorView.systemUiVisibility and SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        }
```

- 为什么是问题：`View.getWindowInsetsController()` 在 DecorView 尚未 attach、`mAttachInfo` 为空时返回 null。`?.apply` 把这次设置直接跳过，也没有任何字段把外观记下来。API 30 以下走 `systemUiVisibility`，标志会留在 View 上；API 30 及以上这次写入丢失。`getDecorView()` 只会创建 Decor，不会把它 attach 到窗口。
- 方向：用会保存未 attach 状态的 `WindowInsetsControllerCompat`，或在 DecorView attach 之后再调用 `setSystemBarsAppearance`。

## r03-4

- 严重度：中
- 症状：窗口已经显示之后再切换 `isSystemBarsTranslucentCompat`，刘海/挖孔区域的布局模式不更新。内容该伸进刘海时没有伸进去，或关闭透明系统栏后仍按短边切入刘海。

```37:45:common/src/main/java/com/github/kr328/clash/common/compat/UI.kt
        if (Build.VERSION.SDK_INT >= 28) {
            if (value) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
```

- 为什么是问题：`attributes.layoutInDisplayCutoutMode = ...` 只改 `getAttributes()` 返回对象上的字段，不会调用 `setAttributes()`，也就不会 `updateViewLayout`。窗口 add 上去时 WindowManager 复制的是当时的 LayoutParams；之后只改字段，系统仍用旧副本。同一次赋值里，API 30 及以上的 `setDecorFitsSystemWindows` 还发生在修改刘海模式之前，即使它触发了一次布局，带出去的也是修改前的 cutout 模式。
- 方向：改完 `layoutInDisplayCutoutMode` 后把 LayoutParams 通过 `setAttributes` 交回 Window。

## r03-5

- 严重度：中
- 症状：`ticker` 所在 CoroutineScope 被取消，或 `delay` 抛错之后，Channel 仍保持打开。还在 `receive` 的收集方不再收到 tick，也不会因为 Channel 关闭而结束，界面上的周期刷新停在最后一次。

```9:24:common/src/main/java/com/github/kr328/clash/common/util/Ticker.kt
fun CoroutineScope.ticker(period: Long): Channel<Long> {
    val channel = Channel<Long>(Channel.RENDEZVOUS)

    launch {
        try {
            while (isActive) {
                channel.send(System.currentTimeMillis())

                delay(period)
            }
        } catch (ignored: Exception) {

        }
    }

    return channel
}
```

- 为什么是问题：`delay` 和 `send` 在作用域取消时抛出的 `CancellationException` 是 `Exception` 的子类，被空 `catch` 吃掉，协程以正常完成结束。两条退出路径（循环结束或异常）都没有 `channel.close()`。Rendezvous Channel 上，生产者停止而 Channel 未关闭时，另一个作用域里的 `receive()` 会一直挂起。
- 方向：在 `finally` 中关闭 Channel，并把 `CancellationException` 重新抛出。
