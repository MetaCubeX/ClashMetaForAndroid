package com.github.kr328.clash.service.util

/** Best-effort parse of `rules:` list entries from raw config YAML (for display only). */
object EffectiveRulesParser {
    fun parseRuleItems(yaml: String): List<String> {
        val lines = yaml.lines()
        var inRules = false
        val out = ArrayList<String>()
        for (raw in lines) {
            val t = raw.trim()
            if (!inRules) {
                if (t == "rules:" || t.startsWith("rules:")) {
                    inRules = true
                }
                continue
            }
            if (raw.isNotBlank() && !raw.startsWith(" ") && !raw.startsWith("\t")) {
                if (!t.startsWith("#") && t.contains(":")) {
                    break
                }
            }
            if (t.startsWith("#") || t.isEmpty()) continue
            if (t.startsWith("-")) {
                out.add(t.removePrefix("-").trim())
            }
        }
        return out
    }
}
