package com.github.kr328.clash.service.util

/**
 * Parsed mihomo config document used by ClashFest UI/editing helpers.
 *
 * This is deliberately not a Kotlin clone of mihomo's Go RawConfig. The engine
 * remains the authority for full config semantics; this layer only gives local
 * tools a single structural AST and block-level patching for sections they edit.
 */
class MihomoConfigDocument private constructor(
    private val source: String,
    val root: MutableMap<String, Any?>,
) {
    val proxyProviders: Map<*, *>?
        get() = root["proxy-providers"] as? Map<*, *>

    val ruleProviders: Map<*, *>?
        get() = root["rule-providers"] as? Map<*, *>

    val proxies: List<*>?
        get() = root["proxies"] as? List<*>

    val proxyGroups: List<*>?
        get() = root["proxy-groups"] as? List<*>

    val rules: List<*>?
        get() = root["rules"] as? List<*>

    val listeners: List<*>?
        get() = root["listeners"] as? List<*>

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
    private val topLevelKeyPattern = Regex("^[A-Za-z0-9_.-]+\\s*:.*$")

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

    private fun appendBlock(text: String, replacement: String): String {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return "$replacement\n"
        return "$trimmed\n\n$replacement\n"
    }

    private fun findBlockRange(lines: List<String>, key: String): BlockRange? {
        val start = lines.indexOfFirst { isTopLevelKey(it, key) }
        if (start < 0) return null
        var end = start + 1
        while (end < lines.size) {
            if (isAnyTopLevelKey(lines[end])) break
            if (startsNextTopLevelBlockPreamble(lines, end)) break
            end++
        }
        return BlockRange(start, end)
    }

    private fun startsNextTopLevelBlockPreamble(lines: List<String>, index: Int): Boolean {
        val line = lines[index]
        if (line.isNotBlank() && !line.startsWith('#')) return false
        val next = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith('#') }
            ?: return false
        return isAnyTopLevelKey(next)
    }

    private fun isTopLevelKey(line: String, key: String): Boolean {
        if (line.startsWith(' ') || line.startsWith('\t')) return false
        return line == "$key:" || line.startsWith("$key:")
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
