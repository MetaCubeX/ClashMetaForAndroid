package com.github.kr328.clash.service.util

/**
 * After a subscription fetch, native code replaces [config.yaml] with the remote file.
 * Local additions under [rule-providers] and [rules] (e.g. online rules / RULE-SET) must be
 * merged back so a refresh does not wipe them.
 */
object SubscriptionUpdateMerge {

    data class PreservedOverlay(
        val ruleProviders: Any?,
        val rules: Any?,
    ) {
        fun isEmpty(): Boolean {
            val rpEmpty =
                ruleProviders == null || (ruleProviders is Map<*, *> && ruleProviders.isEmpty())
            val rEmpty = rules == null || (rules is List<*> && rules.isEmpty())
            return rpEmpty && rEmpty
        }

        companion object {
            val EMPTY = PreservedOverlay(null, null)
        }
    }

    fun extractPreserved(configYaml: String): PreservedOverlay {
        val root = YamlFormatting.parseRootMap(configYaml) ?: return PreservedOverlay.EMPTY
        val rp = root["rule-providers"]
        val rules = root["rules"]
        if (rp == null && rules == null) return PreservedOverlay.EMPTY
        return PreservedOverlay(rp, rules)
    }

    fun mergeAfterFetch(fetchedYaml: String, preserved: PreservedOverlay): String {
        if (preserved.isEmpty()) return fetchedYaml
        val root = YamlFormatting.parseRootMap(fetchedYaml) ?: return fetchedYaml
        if (preserved.ruleProviders != null) {
            root["rule-providers"] = mergeRuleProviderMaps(root["rule-providers"], preserved.ruleProviders)
        }
        if (preserved.rules != null) {
            root["rules"] = mergeRulesLists(preserved.rules, root["rules"])
        }
        return YamlFormatting.blockYaml().dump(root)
    }

    /** Start from fetched map, overlay preserved entries (local wins on same key). */
    private fun mergeRuleProviderMaps(fetched: Any?, preserved: Any?): Any? {
        if (preserved == null) return fetched
        val merged = LinkedHashMap<String, Any?>()
        (fetched as? Map<*, *>)?.forEach { (k, v) -> merged[k.toString()] = v }
        (preserved as? Map<*, *>)?.forEach { (k, v) -> merged[k.toString()] = v }
        return merged
    }

    /**
     * Prepends rules that existed locally but are missing from the fetched list (string match),
     * then appends the full fetched rules list to preserve subscription order for the rest.
     */
    private fun mergeRulesLists(preservedRules: Any?, fetchedRules: Any?): List<Any?> {
        val newList: List<Any?> = when (fetchedRules) {
            is List<*> -> fetchedRules
            null -> emptyList()
            else -> listOf(fetchedRules)
        }
        val oldList: List<Any?> = when (preservedRules) {
            is List<*> -> preservedRules
            null -> emptyList()
            else -> listOf(preservedRules)
        }
        val newTrimmed = newList.map { it?.toString()?.trim() }.toSet()
        val prefix = oldList.filter {
            val t = it?.toString()?.trim()
            !t.isNullOrEmpty() && t !in newTrimmed
        }
        return prefix + newList
    }
}
