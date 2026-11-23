---
title: "审查发现 r03 common"
type: reference
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-30
summary: "5 条；最严重的是切片 Parcelable 列表在 Binder 读取失败时按成功返回，未读条目被丢掉。"
---

# 审查发现 r03 common

状态：草案。

覆盖需求：无（审查记录，不单列产品需求）。

范围只覆盖 common/src 与 common/build.gradle.kts。下面 5 条都来自这次读到的源码。

## r03-1
- 严重度：高
- 症状：跨进程读回的列表缺了后半段，调用方仍把它当成完整数据
- 代码：
```43:88:common/src/main/java/com/github/kr328/clash/common/util/Parcelable.kt
fun <T : Parcelable> Parcelable.Creator<T>.createListFromParcelSlice(
    parcel: Parcel,
    flags: Int,
    chunk: Int,
): List<T> {
    val total = parcel.readInt()
    val remote = parcel.readStrongBinder()
    val result = ArrayList<T>(total)

    var offset = 0

    while (offset < total) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            data.writeInt(offset)
            data.writeInt(chunk)

            if (!remote.transact(
                    SliceParcelableListBpBinder.TRANSACTION_GET_ITEMS,
                    data,
                    reply,
                    flags
                )
            ) {
                break
            }

            val size = reply.readInt()

            repeat(size) {
                result.add(createFromParcel(reply))
            }

            offset += size

            if (size == 0)
                break
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    return result
}
```
- 为什么是问题：`flags` 被原样交给 `IBinder.transact` 的第四个参数。Binder 里这一位是 `FLAG_ONEWAY`（值为 1），`Parcelable.PARCELABLE_WRITE_RETURN_VALUE` 也是 1。跨进程单向调用不会把条目写进 reply，随后的 `readInt` 读不到条数。`transact` 返回 false，或者读到的 `size` 为 0 时，循环直接 `break`，函数返回已经放进 `result` 的前半段。写入时记下的 `total` 没有再核对，缺掉的条目不会以异常形式暴露。
- 方向：事务失败时抛错并丢弃半截结果；`transact` 固定使用同步标志 0，Parcelable flags 只传给 `writeToParcel`。

## r03-2
- 严重度：中
- 症状：列表在写出之后被改短，对端得到的条目数少于当初写入的 total
- 代码：
```16:40:common/src/main/java/com/github/kr328/clash/common/util/Parcelable.kt
                val end = (offset + chunk).coerceAtMost(list.size)

                reply.writeInt(end - offset)

                for (i in offset until end) {
                    list[i].writeToParcel(reply, flags)
                }

                return true
            }
        }

        return super.onTransact(code, data, reply, flags)
    }

    companion object {
        const val TRANSACTION_GET_ITEMS = 10
    }
}

fun <T : Parcelable> List<T>.writeToParcelSlice(parcel: Parcel, flags: Int) {
    val bp = SliceParcelableListBpBinder(this, flags)

    parcel.writeInt(size)
    parcel.writeStrongBinder(bp)
}
```
- 为什么是问题：`writeToParcelSlice` 立刻写入的只有当时的 `size`，Binder 持有的是原列表引用。条目要等到对端回调 `onTransact` 才逐个 `writeToParcel`。这段时间列表变短后，`end` 被收成新的 `list.size`，`end - offset` 变成负数。读端把 offset 加上这个负数，下一轮读到 0 条就在 `createListFromParcelSlice` 里停止，返回的列表短于写入时的 total。
- 方向：写入时复制一份快照，条数和条目都从这份快照读取。

## r03-3
- 严重度：中
- 症状：清空文件名或 UUID，或读到非法 uuid data 时直接崩溃
- 代码：
```21:35:common/src/main/java/com/github/kr328/clash/common/util/Intent.kt
var Intent.fileName: String?
    get() {
        return data?.takeIf { it.scheme == "file" }?.schemeSpecificPart
    }
    set(value) {
        data = Uri.fromParts("file", value, null)
    }

var Intent.uuid: UUID?
    get() {
        return data?.takeIf { it.scheme == "uuid" }?.schemeSpecificPart?.let(UUID::fromString)
    }
    set(value) {
        data = Uri.fromParts("uuid", value.toString(), null)
    }
```
- 为什么是问题：`fileName` 声明为 `String?`，赋 null 会进入 `Uri.fromParts`，ssp 为 null 时抛 NullPointerException。`uuid` 声明为 `UUID?`，赋 null 时 `value.toString()` 得到字符串 `"null"` 并写进 data；下次读取走 `UUID.fromString("null")`，抛 IllegalArgumentException。scheme 为 uuid 但内容不是合法 UUID 时，读取同样抛异常。
- 方向：null 应清除 Intent 的 data；uuid 解析失败时返回 null。

## r03-4
- 严重度：中
- 症状：ticker 发送失败后，同一个协程作用域里的其他工作一起停掉
- 代码：
```9:24:common/src/main/java/com/github/kr328/clash/common/util/Ticker.kt
fun CoroutineScope.ticker(period: Long): Channel<Long> {
    val channel = Channel<Long>(Channel.RENDEZVOUS)

    launch {
        try {
            while (isActive) {
                channel.send(System.currentTimeMillis())

                delay(period)
            }
        } finally {
            channel.close()
        }
    }

    return channel
}
```
- 为什么是问题：消费端 `close()` 之后，`send` 抛出 `ClosedSendChannelException`，它不是 `CancellationException`。`launch` 没有接住这个异常，父作用域会被取消。`period` 为负时 `delay` 抛 `IllegalArgumentException`，结果相同。`Global` 由 `CoroutineScope(Dispatchers.IO)` 委托而来，用的是普通 Job；`init` 只保存 Application，作用域一旦取消就不会再建。
- 方向：ticker 内部捕获非取消异常并关闭 channel，避免取消父作用域。

## r03-5
- 严重度：低
- 症状：配置里没有语言时，读取首选语言会得到 null，使用处崩溃
- 代码：
```9:16:common/src/main/java/com/github/kr328/clash/common/compat/Resource.kt
val Configuration.preferredLocale: Locale
    get() {
        return if (Build.VERSION.SDK_INT >= 24) {
            locales[0]
        } else {
            locale
        }
    }
```
- 为什么是问题：API 24 及以上使用 `locales[0]`。`LocaleList.get` 在下标超出范围时返回 null，空列表会走到这里。`Configuration.setToDefaults()` 会把语言列表置空。API 24 以下返回的 `locale` 字段同样可以为 null。属性的返回类型是非空 `Locale`。
- 方向：语言列表为空时返回一个明确的默认 Locale。
