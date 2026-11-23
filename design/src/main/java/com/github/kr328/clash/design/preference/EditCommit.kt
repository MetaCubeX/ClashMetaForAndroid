package com.github.kr328.clash.design.preference

/**
 * 同一字段从进入到写回结束前只占一次。第二次 [tryEnter] 返回 false，调用方不得再读初值。
 * 占住必须发生在第一次挂起读之前。
 */
internal class FieldEditSessions {
    private val open = HashSet<Any>()

    fun tryEnter(field: Any): Boolean = open.add(field)

    fun exit(field: Any) {
        open.remove(field)
    }
}

internal sealed class EditCommit<out T> {
    data class Write<T>(val value: T) : EditCommit<T>()
    data object Skip : EditCommit<Nothing>()
}

internal fun <T> commitIfChanged(initial: T, updated: T): EditCommit<T> =
    if (updated == initial) EditCommit.Skip else EditCommit.Write(updated)

/** 非 null、非空、且不能按 Int 解析。空串和 null 不是这一支。 */
internal fun isUnparsedPortText(entered: String?): Boolean =
    entered != null && entered.isNotEmpty() && entered.toIntOrNull() == null

/** 确定键只接受空串或整数。不修剪空白。 */
internal fun acceptsEmptyOrInt(text: String): Boolean =
    text.isEmpty() || text.toIntOrNull() != null

internal fun <T> commitNullableText(
    initialText: String?,
    entered: String?,
    adapter: NullableTextAdapter<T>,
): EditCommit<T> {
    if (adapter === NullableTextAdapter.Port && isUnparsedPortText(entered)) {
        return EditCommit.Skip
    }
    if (entered == initialText) return EditCommit.Skip
    return EditCommit.Write(adapter.to(entered))
}
