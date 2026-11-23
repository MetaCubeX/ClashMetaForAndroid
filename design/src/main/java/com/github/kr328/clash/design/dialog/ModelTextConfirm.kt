package com.github.kr328.clash.design.dialog

/**
 * 确定键交回的文本。重置仍由中性按钮交回 null，取消仍由关闭交回初值，不经过这里。
 * [preserveUnedited] 为真且最终文本等于 `initial ?: ""` 时交回 [initial]，因此未设置仍是 null。
 */
internal fun confirmedModelText(
    initial: String?,
    text: String,
    accepted: Boolean,
    preserveUnedited: Boolean,
): String? {
    val edited = text != (initial ?: "")
    return when {
        !accepted -> initial
        preserveUnedited && !edited -> initial
        else -> text
    }
}
