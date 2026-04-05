package com.github.kr328.clash.service.util

import org.yaml.snakeyaml.Yaml

/**
 * Inserts a [ruleProvidersBlock] before the first top-level `rules:` and prepends [prependRuleLine]
 * to the rules list. Fails if [config] already contains `rule-providers:` (caller should handle).
 */
object RuleProviderYamlMerge {
    fun merge(config: String, ruleProvidersBlock: String, prependRuleLine: String): String {
        require(!config.contains("rule-providers:")) {
            "Config already defines rule-providers; merge not supported yet."
        }
        val providers = ruleProvidersBlock.trimEnd()
        val ruleLine = prependRuleLine.trim().let { line ->
            when {
                line.startsWith("-") -> "  $line".trimEnd()
                line.startsWith("  -") -> line.trimEnd()
                else -> "  - ${line.removePrefix("-").trim()}"
            }
        }
        val marker = Regex("^rules:\\s*$", RegexOption.MULTILINE)
        val m = marker.find(config)
        if (m == null) {
            return config.trimEnd() + "\n\n" + providers + "\n\nrules:\n" + ruleLine + "\n"
        }
        val head = config.substring(0, m.range.first).trimEnd()
        val tail = config.substring(m.range.last + 1)
        val body = if (tail.startsWith("\n")) tail else "\n" + tail
        return "$head\n\n$providers\n\nrules:\n$ruleLine$body"
    }

    /**
     * When the profile already has `rule-providers:`, merge new provider entries and prepend one
     * rules line without adding a second top-level `rules:` key (avoids YAML duplicate-key errors).
     */
    fun mergeWhenRuleProvidersExist(
        configText: String,
        ruleProvidersYaml: String,
        prependRuleLine: String,
    ): String {
        val dump = YamlFormatting.blockYaml()
        val root = YamlFormatting.parseRootMap(configText)
            ?: throw IllegalArgumentException("invalid config yaml")
        val incoming = Yaml().load<MutableMap<String, Any?>>(ruleProvidersYaml.trim())
            ?: throw IllegalArgumentException("invalid rule-providers yaml")
        val incomingRp = incoming["rule-providers"] as? Map<*, *> ?: emptyMap<Any?, Any?>()

        @Suppress("UNCHECKED_CAST")
        val existingRp =
            (root["rule-providers"] as? MutableMap<String, Any?>) ?: mutableMapOf()
        for ((k, v) in incomingRp) {
            existingRp[k.toString()] = v
        }
        root["rule-providers"] = existingRp

        val normalizedRule = prependRuleLine.trim().removePrefix("-").trim()
        if (normalizedRule.isNotEmpty()) {
            val incomingKey = extractRuleSetProviderKey(normalizedRule)
            @Suppress("UNCHECKED_CAST")
            val rulesList: MutableList<Any?> = when (val r = root["rules"]) {
                is MutableList<*> -> r as MutableList<Any?>
                is List<*> -> r.toMutableList().also { root["rules"] = it }
                null -> mutableListOf<Any?>().also { root["rules"] = it }
                else -> mutableListOf<Any?>(r as Any?).also { root["rules"] = it }
            }
            // Replace older RULE-SET lines for the same provider key (avoid stacked duplicates).
            if (incomingKey != null) {
                rulesList.removeAll { line ->
                    val k = extractRuleSetProviderKey(line?.toString()?.trim().orEmpty())
                    k != null && k == incomingKey
                }
            } else {
                val already = rulesList.any { it?.toString()?.trim() == normalizedRule }
                if (already) {
                    return dump.dump(root)
                }
            }
            rulesList.add(0, normalizedRule)
        }
        return dump.dump(root)
    }

    /** Second field in `RULE-SET,<key>,...` (provider name in rule-providers). */
    private fun extractRuleSetProviderKey(ruleLine: String): String? {
        val t = ruleLine.trim().removePrefix("-").trim()
        if (!t.startsWith("RULE-SET", ignoreCase = true)) return null
        val parts = t.split(",").map { it.trim() }
        if (parts.size < 2) return null
        return parts[1].takeIf { it.isNotEmpty() }
    }
}
