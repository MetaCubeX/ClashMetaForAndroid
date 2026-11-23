---
title: "审查发现 r08 design-classic"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条；最严重的是覆盖端口遇到无法解析的输入会被写成不修改，原端口丢失。"
---

# 审查发现 r08 design-classic

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖 design 的 com 包、res、test 和 design/build.gradle.kts。下面 5 条都来自这次读到的源码。

## r08-1
- 严重度：高
- 症状：在覆盖设置里改 HTTP、SOCKS、Redirect、TProxy 或 Mixed 端口时，确认一段无法按整数解析的文本（多一个空格、换行，或超出 Int）后，这一项变成「不修改」，原来的端口覆盖没了。
- 代码：
```15:20:design/src/main/java/com/github/kr328/clash/design/preference/Value.kt
            override fun to(text: String?): Int? {
                if (text == null) return null
                if (text.isEmpty()) return 0

                return text.toIntOrNull()
            }
```
- 为什么是问题：`NullableTextAdapter.Port.to` 只把空字符串收成 0，其它解析失败都得到 null。`editNullable`（`OverrideSettingsDesign.kt` 506–515 行）没有端口校验，确认后总是 `write(adapter.to(entered))`。这里 null 表示不修改，于是一次失败的确认会清掉已经生效的端口。重置按钮本来才会写出 null。
- 方向：解析失败时保留打开对话框前的端口，只有空输入才写成禁用。

## r08-2
- 严重度：高
- 症状：覆盖项、嗅探列表或 WebDAV 文本还没弹出编辑框时再点一次，会叠两层编辑。后关掉的那一层用打开时的旧快照写回，刚保存的端口、名单或地址被盖掉。
- 代码：
```503:516:design/src/main/java/com/github/kr328/clash/design/OverrideSettingsDesign.kt
        launch(Dispatchers.Main) {
            val title = context.getText(titleRes)
            val initial = withContext(Dispatchers.IO) { adapter.from(read()) }
            val entered = context.requestModelTextInput(
                initial = initial,
                title = title,
                reset = context.getText(R.string.reset),
                hint = title,
                preserveUnedited = true,
            )
            withContext(Dispatchers.IO) {
                write(adapter.to(entered))
            }
            publish()
        }
```
- 为什么是问题：`launch(Dispatchers.Main)` 立刻返回，对话框要等 IO 读完初值才出现，这期间同一行还能再进一次。两层都握住旧初值。取消走 `Input.kt` 52–54 行，把初值交回去，而这里取消也会 `write`。同一结构还有 `OverrideSettingsDesign.kt` 525–530 行、`MetaFeatureSettingsDesign.kt` 351–356 行、`WebdavSettingsDesign.kt` 201–214 行。
- 方向：同一字段在编辑结束前不要再打开一层，取消时也不要写回打开时的快照。

## r08-3
- 严重度：高
- 症状：WebDAV 地址、局域网地址、用户名或远程目录处于未设置（null）时，打开编辑框不改内容直接确定，会把 null 存成空字符串。重置得到的 null 经这一步后不再保留。
- 代码：
```201:214:design/src/main/java/com/github/kr328/clash/design/WebdavSettingsDesign.kt
        launch(Dispatchers.Main) {
            val initial = withContext(Dispatchers.IO) { read() }
            val text = context.requestModelTextInput(
                initial = initial,
                title = context.getText(titleRes),
                reset = context.getText(R.string.reset),
                hint = context.getText(titleRes),
            )
            val shown = withContext(Dispatchers.IO) {
                val newValue = NullableTextAdapter.String.to(text)
                write(newValue)
                NullableTextAdapter.String.from(newValue)
            }
            show(shown)
        }
```
- 为什么是问题：`requestModelTextInput`（`Input.kt` 41–49 行）在 `preserveUnedited` 为默认 false 时，未改动点确定交回输入框里的 `""`，而 `setText(null)` 得到的就是空串。取消和重置交回的是初始 null。`NullableTextAdapter.String.to` 原样保留 null 和 `""`，随后无条件 `write`。空字符串和未设置因此变成两个已保存状态。
- 方向：未改动的确定应写回打开时的值，null 保持 null。

## r08-4
- 严重度：低
- 症状：编辑 DNS、hosts 或嗅探名单的全屏列表时，确定或重置被连点，主线程在已结束的协程上再次 resume，编辑过程崩溃。
- 代码：
```41:55:design/src/main/java/com/github/kr328/clash/design/preference/Overlay.kt
            binding.resetView.setOnClickListener {
                ctx.resume(EditableListOverlayResult.Reset)

                dialog.dismiss()
            }

            binding.cancelView.setOnClickListener {
                dialog.dismiss()
            }

            binding.okView.setOnClickListener {
                ctx.resume(EditableListOverlayResult.Apply)

                dialog.dismiss()
            }
```
- 为什么是问题：确定和重置不看 `ctx.isCompleted` 就 `resume`。对话框关掉前按钮还能再收到一次点击，第二次 `resume` 抛出 `IllegalStateException`。取消按钮只关对话框，由 59–62 行的 dismiss 收口，那条路径有完成判断。覆盖设置和 Meta 功能的名单编辑都走这个全屏层。
- 方向：确定和重置只在协程尚未完成时 resume 一次。

## r08-5
- 严重度：中
- 症状：打开 WebDAV 自动同步后，在开关写入完成前再点上传或下载，同步请求已经发出，确认框仍在。取消这个确认框会把页面的忙碌标记清掉，进行中的同步不再挡住下一次拉取或上传。
- 代码：
```149:186:design/src/main/java/com/github/kr328/clash/design/WebdavSettingsDesign.kt
    private fun onAutoSync(enabled: Boolean) {
        // A sync or test already owns the page. Turning auto-sync on must not stick or queue another run.
        if (enabled && busy) {
            autoSync = false
            return
        }
        val generation = autoSyncGeneration.incrementAndGet()
        autoSync = enabled
        launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                autoSyncMutex.withLock {
                    if (generation != autoSyncGeneration.get()) return@withLock
                    store.autoSync = enabled
                }
            }
            if (generation == autoSyncGeneration.get() && enabled) {
                if (requests.trySend(Request.Sync).isSuccess) busy = true
            }
        }
    }

    private fun confirmThenSend(@StringRes titleRes: Int, request: Request) {
        if (busy) return
        busy = true
        launch(Dispatchers.Main) {
            var sent = false
            try {
                val ok = iosConfirm(
                    context,
                    title = context.getString(titleRes),
                    message = context.getString(R.string.webdav_tips),
                    confirm = context.getString(R.string.ok),
                )
                if (ok) sent = requests.trySend(request).isSuccess
            } finally {
                if (!sent) busy = false
            }
        }
    }
```
- 为什么是问题：自动同步要等偏好写完才把 `busy` 设为 true，并在那时直接 `trySend(Request.Sync)`。这段空档里 `confirmThenSend` 看到的 `busy` 仍是 false，会再收下一次上传或下载。用户取消确认时 `sent` 为 false，`finally` 把 `busy` 清掉，已经发出的同步不再占住页面。
- 方向：拨动自动同步时立刻占住忙碌标记，取消别的确认框时不要清掉已经发出的同步。
