package com.github.kr328.clash.service.util

import org.yaml.snakeyaml.Yaml

object RuleProvidersYamlEdit {
    private val parseYaml = Yaml()
    private val dumpYaml = YamlFormatting.blockYaml()

    /** Returns only the `rule-providers` subtree as YAML text, or null if missing. */
    fun extractBlock(configText: String): String? {
        val root = try {
            parseYaml.load<MutableMap<String, Any?>>(configText) ?: return null
        } catch (_: Exception) {
            return null
        }
        val rp = root["rule-providers"] ?: return null
        return dumpYaml.dump(mapOf("rule-providers" to rp)).trimEnd()
    }

    /**
     * Merges edited YAML into [configText]. [editedYaml] may be either the full
     * `rule-providers:` document or only the inner map (keys under rule-providers).
     */
    fun mergeIntoConfig(configText: String, editedYaml: String): String {
        val root = parseYaml.load<MutableMap<String, Any?>>(configText)
            ?: throw IllegalArgumentException("invalid config")
        val parsed = parseYaml.load<Any>(editedYaml)
        val newRp: Any = when (parsed) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val m = parsed as Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                (m["rule-providers"] ?: m) as Any
            }
            else -> throw IllegalArgumentException("invalid rule-providers yaml")
        }
        root["rule-providers"] = newRp
        return dumpYaml.dump(root)
    }
}
