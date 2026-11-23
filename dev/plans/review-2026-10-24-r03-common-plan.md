---
title: "审查方案 r03 common"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "hold 3、drop 0、weaken 2"
---

# 审查方案 r03 common

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。


核对范围是 common/src 与 common/build.gradle.kts。发现稿 5 条的引用都还在这些活码里。`common/build.gradle.kts` 没有对应条目。

hold 3、drop 0、weaken 2。

drop：无。

## r03-1

结论：weaken。严重度由高降为中。`createListFromParcelSlice` 仍在 `common/src/main/java/com/github/kr328/clash/common/util/Parcelable.kt` 第 43–89 行。`transact` 使用传入的 `flags`（第 62–67 行）；返回 false 或读到的 `size == 0` 时 `break`（第 68–70 行、第 80–81 行），第 88 行返回已经积累的 `result`，不与第 48 行读到的 `total` 比较。

反证：两条活调用传入的标志都是 0，读事务不会因此变成单向调用。`core/src/main/java/com/github/kr328/clash/core/model/ProxyGroup.kt` 第 16 行是 `Proxy.createListFromParcelSlice(parcel, 0, 50)`，`core/src/main/java/com/github/kr328/clash/core/model/ProviderList.kt` 第 9 行是 `Provider.createListFromParcelSlice(parcel, 0, 20)`。Android 34 `IBinder.FLAG_ONEWAY` 与 `Parcelable.PARCELABLE_WRITE_RETURN_VALUE` 的值都是 1，但后者没有进入这两处读取。`ProxyGroup.writeToParcel` 第 45 行调用切片写出时传入 0。`ProviderList.writeToParcel` 会把调用方的 flags 交给 `writeToParcelSlice`，那份值只留在 `SliceParcelableListBpBinder` 里供条目 `writeToParcel` 使用。另外，`transact` 抛出 `RemoteException` 时异常会冒出，半截列表不会被返回；静默成功只出现在上述 `break`。

问题类：切片读取在事务失败或块大小为 0 时，把不完整列表当作成功结果返回。

不变量：返回的列表长度等于 parcel 里写下的 `total`；对不上时抛出异常，调用方拿不到半截列表。`IBinder.transact` 的标志固定为 0。

要动的符号或文件：`Parcelable.Creator.createListFromParcelSlice`（`common/src/main/java/com/github/kr328/clash/common/util/Parcelable.kt`）。

明确不改什么：不改两处调用的 chunk（50 与 20），不改它们已经传入的 0，也不改函数签名。不改 `TRANSACTION_GET_ITEMS`。不在本条改 `writeToParcelSlice` 的快照，那是 r03-2。不改第 28 行 `super.onTransact` 使用的构造参数 `flags`。已经抛出的 `RemoteException` 继续向外抛。

怎么本地验证：common 没有现成测试。应新增的断言：块在 `offset < total` 时给出 `size == 0`，或读取用的 Binder 让 `transact` 返回 false 时，函数抛异常，且不把已读条目交给调用方；`flags` 参数为 1 时，实际 `transact` 仍使用 0；列表稳定且 chunk 为正时，读回长度等于写入的 `total`。`Parcel` 与 `Binder` 需要 androidTest 或 Robolectric。仓库里现有的 service、design 单测没有覆盖这个符号。

## r03-2

结论：hold。严重度维持中。`SliceParcelableListBpBinder.onTransact` 仍在第 16–22 行用活列表计算 `end`，`writeToParcelSlice` 仍在第 36–41 行只把当时的 `size` 和同一个列表引用写进 parcel。列表随后变短时，`end - offset` 可以为负；读端 `offset += size` 后下一轮得到 `size == 0` 并在 r03-1 的分支里停止，返回的列表短于写下的 `total`。Kotlin 2.1.0 的 `repeat` 在次数小于等于 0 时不执行循环体，负的 `size` 不会补上条目。

问题类：写出切片时只记下当时的长度，Binder 稍后仍读原列表，列表变短会使对端少收条目。

不变量：parcel 里写下的条数，以及之后每次 `onTransact` 写出的条目，都来自 `writeToParcelSlice` 进入时的那一份列表；此后缩短原列表，对端仍能按写入时的条数和顺序读完。

要动的符号或文件：`List.writeToParcelSlice` 与 `SliceParcelableListBpBinder`（同一文件 `Parcelable.kt`）。

明确不改什么：不改事务号 10，不改分块的 offset/chunk 协议，不改 r03-1 的失败策略。不改 `ProxyGroup`、`ProviderList` 的调用方式。不深拷贝每个 `Parcelable` 的字段；固定的是列表结构和元素引用。`Clash.queryProviders` 与 `Clash.queryGroup` 当前会新建列表，本条不改那两处生产路径。

怎么本地验证：common 没有现成测试。应新增的断言：用可变列表调用 `writeToParcelSlice` 并记下 `size` 后缩短该列表，再按现有 chunk 读回，条数等于缩短前的 `size`，元素与缩短前一致。需要 androidTest 或 Robolectric 来构造 `Parcel`。

## r03-3

结论：hold。严重度维持中。`Intent.fileName` 与 `Intent.uuid` 仍在 `common/src/main/java/com/github/kr328/clash/common/util/Intent.kt` 第 21–35 行。`fileName` 的类型是 `String?`，setter 把值直接交给 `Uri.fromParts`；Android 34 `Uri.fromParts` 在 ssp 为 null 时抛 `NullPointerException`。`uuid` 的类型是 `UUID?`，setter 使用 `value.toString()` 再交给 `Uri.fromParts`；可空接收者的 `toString()` 在值为 null 时得到字符串 `"null"`，这次赋值能写进 data。下一次读取走 `UUID.fromString`。JDK 上 `UUID.fromString("null")`、空字符串和 `"not-a-uuid"` 都抛 `IllegalArgumentException`。scheme 为 `uuid` 但内容非法时，getter 同样没有接住这个异常。

问题类：可空的 fileName 与 uuid 在清空或 uuid 内容非法时抛异常，而不是得到 null。

不变量：赋 null 清除 `Intent.data`，且 setter 不抛。读到的 scheme 不是对应类型时返回 null。scheme 为 `uuid` 但内容不是合法 UUID 时，`uuid` 返回 null。合法文件名和合法 UUID 仍能写回再读回。

要动的符号或文件：`Intent.fileName`、`Intent.uuid` 的 getter 与 setter（`common/src/main/java/com/github/kr328/clash/common/util/Intent.kt`）。

明确不改什么：不改 `grantPermissions`。不改 `setUUID`、`setFileName` 的非空参数。不改 scheme 字符串 `"file"` 和 `"uuid"`。不改现有调用点：`setUUID` 传入的是非空 UUID；`PropertiesActivity`、`FilesActivity`、`ProfileWorker`、`AccessControlActivity` 只读取 `intent.uuid`。

怎么本地验证：common 没有现成测试。应新增的断言：`fileName = null` 后 `data` 为 null 且不抛；`uuid = null` 后读取为 null 且不抛；data 的 scheme 为 `uuid`、ssp 为 `"null"` 或 `"not-a-uuid"` 时读取为 null；`setFileName("a")` 与 `setUUID` 一个合法 UUID 后能读回原值。需要 androidTest 或 Robolectric。

## r03-4

结论：weaken。严重度由中降为低。`CoroutineScope.ticker` 仍在 `common/src/main/java/com/github/kr328/clash/common/util/Ticker.kt` 第 9–24 行。`launch` 只在 `finally` 里关闭 channel，没有接住 `send` 的失败。依赖是 `gradle/libs.versions.toml` 里的 `kotlinx-coroutines` 1.10.1。该类的 `ClosedSendChannelException` 继承 `IllegalStateException`，`getSendException` 在没有 close cause 时构造它。`JobSupport.childCancelled` 只对 `CancellationException` 直接返回；其他异常会进入 `cancelImpl`。因此消费端 `close()` 之后，`send` 的这个异常会使普通父 Job 失败。

反证：1.10.1 的 `DelayKt.delay` 在毫秒数小于等于 0 时直接返回 `Unit`，不抛 `IllegalArgumentException`。现有调用的周期都是正数，并且没有关闭返回的 channel：`MainActivity` 第 80 行 1 秒，`ProfilesShell` 第 73 行 1 分钟，`ProxyShell` 第 190 行 1 分钟，`FilesActivity` 第 45 行 1 分钟，`LogcatActivity` 第 102 行 500 毫秒，`ProvidersActivity` 第 21 行 1 分钟，`DynamicNotificationModule` 第 85 行 1 秒。它们停在外层作用域取消，不在 `Global` 上调用 `ticker`。`Global` 仍是 `CoroutineScope(Dispatchers.IO)` 委托出的普通 Job，`init` 只保存 Application（`Global.kt` 第 8–16 行）；这条本身还在，但当前 ticker 调用不会取消 `Global`。

问题类：消费端关闭 ticker 的 channel 后，发送异常会失败掉父协程作用域。

不变量：消费端关闭 channel 后，ticker 协程结束并关闭 channel，父 Job 仍保持 active。父作用域自己的取消仍能结束这个子协程，取消异常继续按取消传播。

要动的符号或文件：`CoroutineScope.ticker`（`common/src/main/java/com/github/kr328/clash/common/util/Ticker.kt`）。

明确不改什么：不改 `Global.init` 和 `Global.destroy`。不改上述调用点的周期，也不让它们改为显式 `close()`。不按「负数 `delay` 会抛 `IllegalArgumentException`」增加抛错。不把 channel 从 rendezvous 改成带缓冲。

怎么本地验证：common 没有现成测试。应新增的断言：父作用域使用普通 Job，调用 `ticker` 后立刻关闭返回的 channel，父 Job 仍为 active，且 channel 已关闭；取消父作用域后，ticker 子协程结束，父作用域的取消原因仍是取消而不是 `ClosedSendChannelException`。这个符号不依赖 Android，可在 JVM 上用协程测试跑。`common/build.gradle.kts` 目前没有测试依赖，加测试依赖只为验证，不改变生产依赖。

## r03-5

结论：hold。严重度维持低。`Configuration.preferredLocale` 仍在 `common/src/main/java/com/github/kr328/clash/common/compat/Resource.kt` 第 9–16 行，返回类型是非空 `Locale`。API 24 及以上走 `locales[0]`。Android 34 `LocaleList.get` 在下标越界时返回 null，空列表的 `get(0)` 就是 null。`Configuration.setToDefaults` 把 `mLocaleList` 设为空列表，并把 `locale` 设为 null。API 24 以下返回的 `locale` 字段因此也可以是 null。

问题类：语言列表为空时，非空类型的 preferredLocale 会得到 null。

不变量：`locales` 为空，或 API 24 以下 `locale` 为 null 时，`preferredLocale` 仍返回一个确定的非空 `Locale`。列表非空时仍返回第一个语言。

要动的符号或文件：`Configuration.preferredLocale`（`common/src/main/java/com/github/kr328/clash/common/compat/Resource.kt`）。

明确不改什么：不改 `design` 里的三处读取：`I18n.kt` 的 `Date.format`、`LogcatScreen.kt`、`AppCrashedScreen.kt`。它们读的是 `context.resources.configuration`。不改按语言选择中英文文案的条件。

怎么本地验证：common 没有现成测试。应新增的断言：`Configuration().setToDefaults()` 之后读取 `preferredLocale`，结果非空，并且可以读取 `language`；在能模拟 API 24 以下的环境里，`locale == null` 时同样返回非空 `Locale`；列表里已有语言时，返回值仍是第一个。需要 androidTest 或 Robolectric。
