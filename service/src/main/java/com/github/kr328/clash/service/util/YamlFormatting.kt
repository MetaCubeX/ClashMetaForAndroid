package com.github.kr328.clash.service.util

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/** Block-style YAML to avoid inline `{ key: value }` dumps that break readability. */
object YamlFormatting {
    fun blockYaml(): Yaml {
        val options = DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        options.isPrettyFlow = true
        // SnakeYAML requires indicatorIndent < indent (not equal).
        options.indent = 4
        options.indicatorIndent = 2
        return Yaml(options)
    }

    fun parseRootMap(text: String): MutableMap<String, Any?>? =
        try {
            Yaml().load<MutableMap<String, Any?>>(text)
        } catch (_: Exception) {
            null
        }
}
