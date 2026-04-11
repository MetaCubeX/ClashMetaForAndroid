package com.github.kr328.clash.service.util

import org.yaml.snakeyaml.Yaml

/** Merge/edit `proxy-providers` in a Clash config.yaml (same idea as [RuleProvidersYamlEdit]). */
object ProxyProvidersYamlEdit {
    private val parseYaml = Yaml()
    private val dumpYaml = YamlFormatting.blockYaml()

    /** Returns only the `proxy-providers` subtree as YAML text, or null if missing. */
    fun extractBlock(configText: String): String? {
        val root = try {
            parseYaml.load<MutableMap<String, Any?>>(configText) ?: return null
        } catch (_: Exception) {
            return null
        }
        val pp = root["proxy-providers"] ?: return null
        return dumpYaml.dump(mapOf("proxy-providers" to pp)).trimEnd()
    }

    /**
     * Merges edited YAML into [configText]. [editedYaml] may be either the full
     * `proxy-providers:` document or only the inner map (keys under proxy-providers).
     */
    fun mergeIntoConfig(configText: String, editedYaml: String): String {
        val root = parseYaml.load<MutableMap<String, Any?>>(configText)
            ?: throw IllegalArgumentException("invalid config")
        val parsed = parseYaml.load<Any>(editedYaml)
        val newPp: Any = when (parsed) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val m = parsed as Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                (m["proxy-providers"] ?: m) as Any
            }
            else -> throw IllegalArgumentException("invalid proxy-providers yaml")
        }
        root["proxy-providers"] = newPp
        return dumpYaml.dump(root)
    }
}
