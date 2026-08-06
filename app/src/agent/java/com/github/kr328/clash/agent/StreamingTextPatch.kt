package com.github.kr328.clash.agent

/** A bounded tail replacement. Stable rendered blocks before [start] are never touched. */
internal data class StreamingTextPatch(
    val start: Int,
    val oldEnd: Int,
    val newEnd: Int,
)

/**
 * Finds the smallest safe Markdown block tail to update.
 *
 * Replacing only this tail keeps already rendered blocks and their Android spans stable. A
 * paragraph boundary is used instead of the raw common prefix because list/quote/code paragraph
 * spans can change while the active block is still being streamed.
 */
internal object StreamingTextPatchPlanner {
    fun calculate(current: CharSequence, next: CharSequence): StreamingTextPatch? {
        if (current.contentEquals(next)) return null

        val commonLimit = minOf(current.length, next.length)
        var common = 0
        while (common < commonLimit && current[common] == next[common]) common++
        if (common > 0 && common < current.length && Character.isLowSurrogate(current[common]) &&
            Character.isHighSurrogate(current[common - 1])) {
            common--
        }

        var blockStart = 0
        var index = common - 1
        while (index > 0) {
            if (current[index] == '\n' && current[index - 1] == '\n') {
                blockStart = index + 1
                break
            }
            index--
        }
        return StreamingTextPatch(blockStart, current.length, next.length)
    }
}
