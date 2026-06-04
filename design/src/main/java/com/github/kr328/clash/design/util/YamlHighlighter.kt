package com.github.kr328.clash.design.util

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

/**
 * Lightweight, dependency-free YAML syntax highlighter that returns a spanned [CharSequence]
 * for a monospace TextView. Line-based and intentionally forgiving — it aims for a readable
 * view of a profile's config.yaml, not a strict YAML parser. Run it off the main thread.
 */
object YamlHighlighter {
    data class Colors(
        val key: Int,
        val string: Int,
        val number: Int,
        val keyword: Int,
        val comment: Int,
        val punctuation: Int,
        val plain: Int,
    )

    // key: value  /  - key: value  (colon must be followed by whitespace or end-of-line,
    // so "http://" inside a value is not mistaken for a key separator).
    private val KEY_REGEX = Regex("""^(\s*(?:-\s+)*)([^:#\n]+?)(:)(?=\s|$)""")
    private val LIST_REGEX = Regex("""^(\s*)(-)(\s+)(.*)$""")

    fun highlight(text: String, colors: Colors): CharSequence {
        val sb = SpannableStringBuilder()
        val lines = text.split("\n")
        for ((i, line) in lines.withIndex()) {
            appendLine(sb, line, colors)
            if (i < lines.size - 1) sb.append("\n")
        }
        return sb
    }

    private fun appendLine(sb: SpannableStringBuilder, line: String, colors: Colors) {
        if (line.isEmpty()) return
        if (line.trimStart().startsWith("#")) {
            appendColored(sb, line, colors.comment, italic = true)
            return
        }
        if (line.isBlank()) {
            sb.append(line)
            return
        }

        // Split off a trailing inline comment (# not inside quotes, preceded by whitespace).
        val commentIdx = findInlineComment(line)
        val code = if (commentIdx >= 0) line.substring(0, commentIdx) else line
        val comment = if (commentIdx >= 0) line.substring(commentIdx) else null

        val keyMatch = KEY_REGEX.find(code)
        if (keyMatch != null) {
            appendColored(sb, keyMatch.groupValues[1], colors.punctuation)
            appendColored(sb, keyMatch.groupValues[2], colors.key)
            appendColored(sb, keyMatch.groupValues[3], colors.punctuation)
            appendValue(sb, code.substring(keyMatch.range.last + 1), colors)
        } else {
            val listMatch = LIST_REGEX.find(code)
            if (listMatch != null) {
                appendColored(sb, listMatch.groupValues[1], colors.plain)
                appendColored(sb, listMatch.groupValues[2], colors.punctuation)
                appendColored(sb, listMatch.groupValues[3], colors.plain)
                appendValue(sb, listMatch.groupValues[4], colors)
            } else {
                appendValue(sb, code, colors)
            }
        }
        if (comment != null) appendColored(sb, comment, colors.comment, italic = true)
    }

    private fun appendValue(sb: SpannableStringBuilder, raw: String, colors: Colors) {
        if (raw.isEmpty()) return
        val leading = raw.takeWhile { it == ' ' || it == '\t' }
        if (leading.isNotEmpty()) sb.append(leading)
        val value = raw.substring(leading.length)
        if (value.isEmpty()) return
        val color = when {
            value == "true" || value == "false" || value == "null" || value == "~" -> colors.keyword
            value.startsWith("&") || value.startsWith("*") || value.startsWith("!") -> colors.keyword
            value.toDoubleOrNull() != null -> colors.number
            // Most YAML scalar values are unquoted (type: vmess, server: 1.2.3.4, name: Hong Kong);
            // colour them as strings so the view reads like a real editor, not mostly plain text.
            else -> colors.string
        }
        appendColored(sb, value, color)
    }

    private fun findInlineComment(line: String): Int {
        var inSingle = false
        var inDouble = false
        for (i in line.indices) {
            when (val c = line[i]) {
                '\'' -> if (!inDouble) inSingle = !inSingle
                '"' -> if (!inSingle) inDouble = !inDouble
                '#' -> if (!inSingle && !inDouble && (i == 0 || line[i - 1] == ' ' || line[i - 1] == '\t')) {
                    return i
                }
            }
        }
        return -1
    }

    private fun appendColored(
        sb: SpannableStringBuilder,
        text: CharSequence,
        color: Int,
        italic: Boolean = false,
    ) {
        if (text.isEmpty()) return
        val start = sb.length
        sb.append(text)
        sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (italic) sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
