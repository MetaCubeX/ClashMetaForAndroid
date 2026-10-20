---
title: "审查发现 r07 design-shell"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "覆盖项点确定会丢掉「不修改」；键值表取消或空确定后应用挂起，改动不写回；同键保存丢旧值；WebDAV 自动同步能写反并发同步。"
---

# 审查发现 r07 design-shell

审查范围是 `design` 里除 `ios/`、`nota/`、`universe/` 以外的活码，以各 `*Design.kt` 和它们直接调用的输入、列表与开关逻辑为准。

## r07-1

- 严重度：高
- 症状：覆盖设置里端口、字符串和密钥当前是「不修改」（值为 null）时，打开输入框不改内容直接点确定，该项变成「已禁用」或「默认」。非法端口文本同样会写成 0。

```494:513:design/src/main/java/com/github/kr328/clash/design/OverrideSettingsDesign.kt
    private fun <T> editNullable(
        @StringRes titleRes: Int,
        adapter: NullableTextAdapter<T>,
        read: () -> T,
        write: (T) -> Unit,
    ) {
        launch(Dispatchers.Main) {
            val title = context.getText(titleRes)
            val initial = withContext(Dispatchers.IO) { adapter.from(read()) }
            val entered = context.requestModelTextInput(
                initial = initial,
                title = title,
                reset = context.getText(R.string.reset),
                hint = title,
            )
            withContext(Dispatchers.IO) {
                write(adapter.to(entered))
            }
            publish()
        }
    }
```

```39:45:design/src/main/java/com/github/kr328/clash/design/dialog/Input.kt
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = binding.textField.text?.toString() ?: ""

                if (validator(text))
                    it.resume(text)
                else
                    it.resume(initial)
            }
```

```15:28:design/src/main/java/com/github/kr328/clash/design/preference/Value.kt
            override fun to(text: String?): Int? {
                if (text == null) return null

                return text.toIntOrNull() ?: 0
            }
        }

        val String = object : NullableTextAdapter<String?> {
            override fun from(value: String?): String? {
                return value
            }

            override fun to(text: String?): String? {
                return text
            }
```

同文件 `portSummary`（461–467 行）和 `secretSummary`（479–484 行）把 null 定义成「不修改」，把空串或小于等于 0 定义成「已禁用 / 默认」。`from(null)` 得到 null，输入框因此是空的；确定按钮把空框恢复成 `""` 而不是 null。`Port.to("")` 得到 0，`String.to("")` 得到 `""`，`editNullable` 随即写回配置并刷新摘要。取消会恢复 `initial`，所以只有点确定会丢这个哨兵值；密钥从「不修改」变成空串后，覆盖语义不再是沿用配置文件。

- 方向：未改动的确定应写回原来的 null；只有用户明确清空或点重置才写成空串或 0，端口无法解析时不要落成 0。

## r07-2

- 严重度：高
- 症状：编辑 hosts 或 nameserver policy 时，新增条目对话框点「取消」，或在空键/空值上点「确定」，对话框会关掉，但这次添加一直不结束。随后在列表上点应用或重置，覆盖配置不会更新，列表里已经做的增删全部丢掉。

```596:618:design/src/main/java/com/github/kr328/clash/design/OverrideSettingsDesign.kt
    private suspend fun requestMapEntry(title: CharSequence): Pair<String, String>? {
        return suspendCancellableCoroutine { ctx ->
            val binding = DialogEditableMapTextFieldBinding
                .inflate(context.layoutInflater, context.root, false)
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setNegativeButton(R.string.cancel) { _, _ -> }
                .setPositiveButton(R.string.ok) { _, _ ->
                    val key = binding.keyView.text?.toString()?.trim() ?: ""
                    val value = binding.valueView.text?.toString()?.trim() ?: ""
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        ctx.resume(key to value)
                    }
                }
                .setView(binding.root)
                .create()
            dialog.setOnCancelListener {
                if (!ctx.isCompleted) {
                    ctx.resume(null)
                }
            }
            dialog.show()
        }
    }
```

```25:54:design/src/main/java/com/github/kr328/clash/design/preference/Overlay.kt
    return coroutineScope {
        suspendCancellableCoroutine { ctx ->
            val dialog = FullScreenDialog(context)
            val binding = DialogPreferenceListBinding
                .inflate(context.layoutInflater, context.root, false)

            binding.surface = dialog.surface
            binding.mainList.applyLinearAdapter(context, adapter)
            binding.titleView.text = title

            binding.newView.setOnClickListener {
                launch {
                    addNewItem()
                }
            }

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

取消按钮的监听是空的，空白确定也不 `resume`。这两个按钮走的是 `dismiss()`，不会触发只挂在 `setOnCancelListener` 上的恢复。添加协程停在 `requestMapEntry`。它是 `requestEditableListOverlay` 里 `coroutineScope` 的子协程，应用按钮虽然恢复了外层续体，作用域仍要等这个子协程。`editMap` 因此到不了 `write`，对话框已经消失，用户看到的是保存成功，配置仍是旧的。返回键会 `cancel()`，那条路径可以恢复。

- 方向：取消、空白确定和任何关闭都要 `resume`；空白确定留在对话框里，等添加协程结束后再让应用或重置返回。

## r07-3

- 严重度：高
- 症状：hosts 或 nameserver policy 里为同一个键加了两条不同的值，列表上能看到两条；点应用后只留下后一条，先填的值没有了，摘要个数也会少一。

```583:593:design/src/main/java/com/github/kr328/clash/design/OverrideSettingsDesign.kt
        val result = requestEditableListOverlay(context, recyclerAdapter, title) {
            val created = requestMapEntry(title)
            if (created != null) {
                recyclerAdapter.addElement(created.first, created.second)
            }
        }
        return when (result) {
            EditableListOverlayResult.Cancel -> initialValue
            EditableListOverlayResult.Apply -> recyclerAdapter.values.toMap()
            EditableListOverlayResult.Reset -> null
        }
```

```18:24:design/src/main/java/com/github/kr328/clash/design/adapter/EditableTextMapAdapter.kt
    fun addElement(key: String, value: String) {
        val keyValue = keyAdapter.to(key)
        val valueValue = valueAdapter.to(value)

        notifyItemInserted(values.size)
        values.add(keyValue to valueValue)
    }
```

`addElement` 不看键是否已存在，列表可以同时拿着两条同键记录。应用时 `toMap()` 按键折叠，同键只保留最后一次 `put`。用户在列表里确认过的前一条值不会进入 `configuration.hosts` 或 `configuration.dns.nameserverPolicy`。

- 方向：加入同键时当场替换旧行或拒绝新增，不要等到 `toMap()` 再静默丢掉旧值。

## r07-4

- 严重度：中
- 症状：WebDAV 自动同步刚打开又马上关掉时，开关显示关，存储里仍可能是开，并且仍会发出一次 Sync。

```144:160:design/src/main/java/com/github/kr328/clash/design/WebdavSettingsDesign.kt
    private fun onAutoSync(enabled: Boolean) {
        if (enabled && busy) return
        autoSync = enabled
        if (!enabled) {
            launch(Dispatchers.IO) { store.autoSync = false }
            return
        }
        busy = true
        launch(Dispatchers.Main) {
            var sent = false
            try {
                withContext(Dispatchers.IO) { store.autoSync = true }
                sent = requests.trySend(Request.Sync).isSuccess
            } finally {
                if (!sent) busy = false
            }
        }
    }
```

打开时先把界面设为开并置 `busy`，真正写入和 `Request.Sync` 在后面的协程里。关闭时的条件是 `enabled && busy`，所以忙碌中仍然允许关掉：界面立刻变成关，并另起一个协程写 `false`。打开那次协程不受影响，仍会写 `true` 再 `trySend(Sync)`。两次写入谁后完成谁留在 `store.autoSync`，Sync 只要已经发出就不会撤回。界面状态没有把 `busy` 交给屏幕，开关在这段时间里可以点。

- 方向：以最后一次开关为准串行写 `store.autoSync`；关闭时如果 Sync 还没发出就不要发。
