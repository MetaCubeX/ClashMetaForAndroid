package com.github.kr328.clash.service.util

/**
 * Block-level YAML patcher used by the WRITE pipeline for partial config
 * edits (proxy-groups, rule-providers, etc).
 *
 * This is deliberately not a Kotlin clone of mihomo's Go RawConfig. READ
 * operations route through Clash.parseProfileSnapshot (engine-parsed JSON);
 * this layer only exists so writes can replace a single top-level YAML block
 * without re-dumping the whole file (which would lose comments, anchors,
 * and quoting).
 */
class MihomoConfigDocument private constructor(
    private val source: String,
    val root: MutableMap<String, Any?>,
) {
    fun extractTopLevelBlock(key: String): String? {
        val value = root[key] ?: return null
        return YamlFormatting.blockYaml().dump(mapOf(key to value)).trimEnd()
    }

    fun renderReplacing(vararg keys: String): String {
        var rendered = source
        for (key in keys) {
            if (!root.containsKey(key)) continue
            rendered = TopLevelYamlBlockPatcher.replace(
                text = rendered,
                key = key,
                value = root[key],
            )
        }
        return rendered
    }

    /**
     * Strip the named top-level blocks from the rendered output. Used by
     * sanitisers that need to *delete* a section (e.g. dropping `listeners:`
     * outright in security hardening) rather than rewrite it — [renderReplacing]
     * intentionally short-circuits when the root key is absent, so a plain
     * `root.remove(key)` followed by replacement render leaves the block
     * untouched in the source text.
     */
    fun renderRemoving(vararg keys: String): String {
        var rendered = source
        for (key in keys) {
            rendered = TopLevelYamlBlockPatcher.remove(text = rendered, key = key)
        }
        return rendered
    }

    fun requireSupportedShape() {
        root["rule-providers"]?.let {
            require(it is Map<*, *>) { "Generated rule-providers must be a map" }
        }
        root["proxy-providers"]?.let {
            require(it is Map<*, *>) { "Generated proxy-providers must be a map" }
        }
        root["rules"]?.let { rules ->
            require(rules is List<*>) { "Generated rules must be a list" }
            rules.forEach {
                require(it is String) { "Generated rules entries must be strings" }
            }
        }
        root["proxy-groups"]?.let { groups ->
            require(groups is List<*>) { "Generated proxy-groups must be a list" }
            groups.forEach {
                require(it is Map<*, *>) { "Generated proxy-groups entries must be maps" }
            }
        }
        root["proxies"]?.let { proxies ->
            require(proxies is List<*>) { "Generated proxies must be a list" }
            proxies.forEach {
                require(it is Map<*, *>) { "Generated proxies entries must be maps" }
            }
        }
        root["listeners"]?.let { listeners ->
            require(listeners is List<*>) { "Generated listeners must be a list" }
            listeners.forEach {
                require(it is Map<*, *>) { "Generated listeners entries must be maps" }
            }
        }
    }

    companion object {
        fun parse(text: String): MihomoConfigDocument? {
            val root = YamlFormatting.parseRootMap(text) ?: return null
            return MihomoConfigDocument(text, root)
        }

        fun parseOrThrow(text: String): MihomoConfigDocument =
            parse(text) ?: throw IllegalArgumentException("invalid config")

        fun parseOrEmpty(text: String): MihomoConfigDocument {
            val root = YamlFormatting.parseRootMap(text)
                ?: return MihomoConfigDocument("", linkedMapOf())
            return MihomoConfigDocument(text, root)
        }

        fun mergeTopLevelMapBlock(configText: String, key: String, editedYaml: String): String {
            val document = parseOrThrow(configText)
            val parsed = YamlFormatting.parseRootMap(editedYaml)
                ?: throw IllegalArgumentException("invalid $key yaml")
            val replacement = when (val explicit = parsed[key]) {
                null -> parsed
                else -> explicit
            }
            require(replacement is Map<*, *>) { "invalid $key yaml" }
            document.root[key] = replacement
            document.requireSupportedShape()
            return document.renderReplacing(key)
        }
    }
}

private object TopLevelYamlBlockPatcher {
    private val topLevelKeyPattern = Regex("""^(?:[A-Za-z0-9_.-]+|"(?:[^"\\]|\\.)+"|'(?:[^']|'')+')\s*:.*$""")

    fun replace(text: String, key: String, value: Any?): String {
        val replacement = YamlFormatting.blockYaml()
            .dump(mapOf(key to value))
            .trimEnd()
        val lineSeparator = if (text.contains("\r\n")) "\r\n" else "\n"
        val normalized = text.replace("\r\n", "\n")
        val lines = normalized.split('\n')
        val range = findBlockRange(lines, key)

        val patched = if (range == null) {
            appendBlock(normalized, replacement)
        } else {
            buildString {
                append(lines.take(range.first).joinToString("\n"))
                if (range.first > 0) append('\n')
                append(replacement)
                if (range.lastExclusive < lines.size) {
                    append('\n')
                    append(lines.drop(range.lastExclusive).joinToString("\n"))
                }
            }
        }
        return if (lineSeparator == "\r\n") patched.replace("\n", "\r\n") else patched
    }

    /** Drop the named top-level block from [text] entirely. No-op if absent. */
    fun remove(text: String, key: String): String {
        val lineSeparator = if (text.contains("\r\n")) "\r\n" else "\n"
        val normalized = text.replace("\r\n", "\n")
        val lines = normalized.split('\n')
        val range = findBlockRange(lines, key) ?: return text
        val before = lines.take(range.first).joinToString("\n")
        val after = lines.drop(range.lastExclusive).joinToString("\n")
        val joined = when {
            before.isEmpty() -> after
            after.isEmpty() -> before
            else -> "$before\n$after"
        }
        return if (lineSeparator == "\r\n") joined.replace("\n", "\r\n") else joined
    }

    private fun appendBlock(text: String, replacement: String): String {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return "$replacement\n"
        return "$trimmed\n\n$replacement\n"
    }

    private fun findBlockRange(lines: List<String>, key: String): BlockRange? {
        val start = lines.indexOfFirst { isTopLevelKey(it, key) }
        if (start < 0) return null
        var end = start + 1
        var preambleStart = -1
        while (end < lines.size) {
            val line = lines[end]
            if (isAnyTopLevelKey(line)) {
                return BlockRange(start, if (preambleStart >= 0) preambleStart else end)
            }
            if (line.isBlank() || line.startsWith('#')) {
                if (preambleStart < 0) preambleStart = end
            } else {
                // An indented/content line means preceding comments belong to
                // the current block, not to a future top-level key.
                preambleStart = -1
            }
            end++
        }
        return BlockRange(start, end)
    }

    private fun isTopLevelKey(line: String, key: String): Boolean {
        if (line.startsWith(' ') || line.startsWith('\t')) return false
        val escapedKey = Regex.escape(key)
        return Regex("^(?:$escapedKey|\"$escapedKey\"|'$escapedKey')\\s*:.*$").matches(line)
    }

    private fun isAnyTopLevelKey(line: String): Boolean {
        if (line.isBlank() || line.startsWith(' ') || line.startsWith('\t') || line.startsWith('#')) {
            return false
        }
        return topLevelKeyPattern.matches(line)
    }

    private data class BlockRange(
        val first: Int,
        val lastExclusive: Int,
    )
}
