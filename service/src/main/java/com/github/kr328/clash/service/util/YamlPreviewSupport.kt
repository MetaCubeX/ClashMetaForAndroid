package com.github.kr328.clash.service.util

import java.security.MessageDigest

object YamlPreviewSupport {
    private const val MAX_DIFF_LINES_PER_SIDE = 160
    private const val MAX_DIFF_CHARS = 32 * 1024
    private const val MAX_PREVIEW_TEXT_CHARS = 64 * 1024

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
        var commonPrefix = 0
        val sharedSize = minOf(oldLines.size, newLines.size)
        while (commonPrefix < sharedSize && oldLines[commonPrefix] == newLines[commonPrefix]) {
            commonPrefix++
        }
        var commonSuffix = 0
        while (
            commonSuffix < sharedSize - commonPrefix &&
            oldLines[oldLines.lastIndex - commonSuffix] == newLines[newLines.lastIndex - commonSuffix]
        ) {
            commonSuffix++
        }

        val out = StringBuilder(minOf(MAX_DIFF_CHARS, oldText.length + newText.length))
        fun appendLine(prefix: Char, line: String): Boolean {
            if (out.length >= MAX_DIFF_CHARS) return false
            val remaining = MAX_DIFF_CHARS - out.length
            if (remaining <= 2) return false
            out.append(prefix)
            val allowed = minOf(line.length, remaining - 2)
            out.append(line, 0, allowed).append('\n')
            return allowed == line.length
        }
        fun appendRange(prefix: Char, lines: List<String>, start: Int, end: Int) {
            val count = end - start
            if (count <= 0 || out.length >= MAX_DIFF_CHARS) return
            val shown = minOf(count, MAX_DIFF_LINES_PER_SIDE)
            for (idx in start until start + shown) {
                if (!appendLine(prefix, lines[idx])) return
            }
            if (shown < count) appendLine(' ', "... ${count - shown} lines omitted ...")
        }

        out.append("--- current\n+++ proposed\n@@\n")
        val prefixStart = (commonPrefix - context.coerceAtLeast(0)).coerceAtLeast(0)
        appendRange(' ', oldLines, prefixStart, commonPrefix)
        appendRange('-', oldLines, commonPrefix, oldLines.size - commonSuffix)
        appendRange('+', newLines, commonPrefix, newLines.size - commonSuffix)
        appendRange(' ', oldLines, oldLines.size - commonSuffix, minOf(oldLines.size, oldLines.size - commonSuffix + context.coerceAtLeast(0)))
        if (out.length >= MAX_DIFF_CHARS) {
            out.setLength((MAX_DIFF_CHARS - 24).coerceAtLeast(0))
            out.append("\n ... diff truncated ...")
        }
        return out.toString().trimEnd()
    }

    /** Bound JSON/Binder preview payloads; the full proposal remains in the server-side cache. */
    fun boundedPreviewText(text: String): String {
        if (text.length <= MAX_PREVIEW_TEXT_CHARS) return text
        val marker = "\n... ${text.length - MAX_PREVIEW_TEXT_CHARS} characters omitted ...\n"
        val available = MAX_PREVIEW_TEXT_CHARS - marker.length
        val head = available / 2
        val tail = available - head
        return text.take(head) + marker + text.takeLast(tail)
    }
}
