package com.github.kr328.clash.service.util

import java.security.MessageDigest

object YamlPreviewSupport {
    fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun validateConfigYaml(text: String) {
        val document = MihomoConfigDocument.parse(text)
            ?: throw IllegalArgumentException("Generated YAML is invalid")
        document.requireSupportedShape()
    }

    fun unifiedDiff(oldText: String, newText: String, context: Int = 3): String {
        if (oldText == newText) return "No changes"
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val lcs = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
        for (i in oldLines.indices.reversed()) {
            for (j in newLines.indices.reversed()) {
                lcs[i][j] = if (oldLines[i] == newLines[j]) {
                    lcs[i + 1][j + 1] + 1
                } else {
                    maxOf(lcs[i + 1][j], lcs[i][j + 1])
                }
            }
        }

        data class Op(val prefix: Char, val line: String)
        val ops = ArrayList<Op>()
        var i = 0
        var j = 0
        while (i < oldLines.size && j < newLines.size) {
            when {
                oldLines[i] == newLines[j] -> {
                    ops.add(Op(' ', oldLines[i]))
                    i++
                    j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> {
                    ops.add(Op('-', oldLines[i]))
                    i++
                }
                else -> {
                    ops.add(Op('+', newLines[j]))
                    j++
                }
            }
        }
        while (i < oldLines.size) ops.add(Op('-', oldLines[i++]))
        while (j < newLines.size) ops.add(Op('+', newLines[j++]))

        val changed = ops.indices.filter { ops[it].prefix != ' ' }
        if (changed.isEmpty()) return "No changes"
        val include = BooleanArray(ops.size)
        for (idx in changed) {
            val start = (idx - context).coerceAtLeast(0)
            val end = (idx + context).coerceAtMost(ops.lastIndex)
            for (k in start..end) include[k] = true
        }

        return buildString {
            appendLine("--- current")
            appendLine("+++ proposed")
            var last = -2
            for (idx in ops.indices) {
                if (!include[idx]) continue
                if (last != idx - 1) appendLine("@@")
                append(ops[idx].prefix)
                appendLine(ops[idx].line)
                last = idx
            }
        }.trimEnd()
    }
}
