---
title: "日志捕获"
type: architecture
status: accepted
phase: N/A
updated: 2026-10-15
summary: "LogcatService 在应用进程订阅 :background 的日志并写文件；LogcatReader 再读回来"
---

# 日志捕获

Clash 运行日志和崩溃页上的系统 logcat 是两条链路。前者由 `LogcatService` 订阅 `:background` 里的 `IClashManager.setLogObserver`，写成 `cacheDir/logs` 下的文件。后者是 `AppCrashedActivity` 执行一次 `logcat -d`。

## 谁打开哪一屏

`MainActivity` 收到 `OpenLogs` 时：`LogcatService.running` 为真就打开 `LogcatActivity`，否则打开 `LogsActivity`。

`LogsActivity` 在 `ActivityStart` 时列出 `cacheDir/logs` 里能被 `LogFile.parseFromFileName` 认出的文件，格式是 `clash-<毫秒>.log`。删除全部会 `logsDir.deleteRecursively()`，`logsDir` 就是这个目录。打开某个文件时给 `LogcatActivity` 带上文件名。开始捕获则打开不带文件名的 `LogcatActivity` 并结束自己。

`LogcatActivity.main` 看 Intent 里有没有文件名。有则读本地文件；没有则走实时捕获。文件名解析失败，或读取抛异常，toast 无效日志文件，并且不再 `setContentDesign`。

## 实时捕获

`mainStreaming` 用 `LogcatDesign(streaming = true)`，然后 `startForegroundService(LogcatService)`，再 `bindService`。绑定返回的 Binder 在 `queryLocalInterface` 里忽略 descriptor，直接交回 `LogcatService` 实例，所以这个绑定只在同一进程里有用。`LogcatService` 没有单独的 `android:process`，和 Activity 同在默认进程。

Activity 处于 started 时，每 500 毫秒调用 `snapshot`。第一次要全量；之后 `snapshot` 在没有增删时返回 null，界面不刷新。`Close` 时 `stopService`，再打开 `LogsActivity` 并结束自己。`onDestroy` 解除这次绑定。

`LogcatService.onCreate` 把 `running` 设为 true，创建通知渠道 `clash_logcat_channel`，以前台服务类型 `specialUse` 显示通知，通知点击打开 `LogcatActivity`。然后它自己 `bindService(RemoteService)`，`BIND_AUTO_CREATE`。这是对 `:background` 的另一次绑定，不经过 `Remote.service`，因此界面全部不可见、`Remote` 解除自己的绑定时，正在跑的日志服务仍可以连着 `RemoteService`。

`onServiceConnected` 后：

1. Binder 已死则 `stopSelf`。
2. `unwrap` 成 `IRemoteService`，取 `clash()`。
3. `logsDir.mkdirs()`，用 `LogcatWriter` 打开一个新文件。`LogFile.generate()` 的文件名是当前时间毫秒。
4. `setLogObserver`。观察者的 `newItem` 对容量 128 的通道 `trySend`。通道满时 `trySend` 不等待。
5. 循环 `receive`。每一条先 `LogcatWriter.appendMessage`，再 `LogcatCache.append`。

`:background` 侧 `ClashManager.setLogObserver` 在观察者非空时调用 `Clash.subscribeLogcat()`，再把收到的 `LogMessage` 交给 `observer.newItem`。`subscribeLogcat` 使用容量 32 的通道，原生回调里是 `trySend`。方向是：应用进程注册观察者，`:background` 再把条目回调进来。观察者换成 null，或注册一个新观察者时，`ClashManager` 取消上一段订阅并 `Clash.forceGc()`。

写文件或读取循环失败时，若 Binder 仍活着就 `setLogObserver(null)`，然后 `stopSelf`。`RemoteService` 断开也会 `stopSelf`。`onDestroy` 取消服务协程、解除到 `RemoteService` 的绑定、停止前台通知，并把 `running` 设为 false。

`LogcatCache` 是容量 128 的环形缓冲。放满时丢掉最旧的一条，并累计 `removed`。`snapshot(full)` 在非全量且 `removed` 和 `appended` 都是 0 时返回 null；否则交出当前列表和这两个计数，然后把计数清零。全量时，返回的 `appended` 是当前条数再加上未清零的 `appended`。

## 文件格式与 LogcatReader

`LogcatWriter` 每一行是：

```text
<time.time>:<Level 名>:<message>
```

`time.time` 是毫秒。级别名来自 `LogMessage.Level.name`。

`LogcatReader` 只读这种文件。它跳过空白修剪后以 `#` 开头的行，再按 `:` 分成至多三段：

- 第一段能解析成 Long：时间用这个毫秒，级别用 `Level.valueOf`，消息是第三段。
- 第一段不是 Long：时间沿用上一条，级别用 `Warning`，消息是整行重新用 `:` 拼回去。

`LogcatActivity` 读完后用 `LogcatDesign(streaming = false)`，一次 `patchMessages` 整表。删除会删掉该文件并 `finish()`。导出用 `CreateDocument("text/plain")`，由 `LogcatFilter` 写成另一种文本：第一行是 `# Capture on ...`，之后每行是对齐过的时间、级别名和消息。导出文件不是 `LogcatWriter` 的格式；`LogcatReader` 不会被用来读这个导出结果。

## 系统 logcat

`SystemLogcat.dumpCrash` 执行：

```text
logcat -d -s Go DEBUG AndroidRuntime ClashMetaForAndroid LwIP
```

它读完标准输出，丢掉以 `------` 开头的行。失败时返回空字符串。只有 `AppCrashedActivity` 调用它，用来填充崩溃页，不写 `logsDir`，也不经过 `LogcatService`。
