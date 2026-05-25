package com.github.kr328.clash.service.util

object RuleProvidersYamlEdit {
    /** Returns only the `rule-providers` subtree as YAML text, or null if missing. */
    fun extractBlock(configText: String): String? {
        return MihomoConfigDocument.parse(configText)
            ?.extractTopLevelBlock("rule-providers")
    }

    /**
     * Merges edited YAML into [configText]. [editedYaml] may be either the full
     * `rule-providers:` document or only the inner map (keys under rule-providers).
     */
    fun mergeIntoConfig(configText: String, editedYaml: String): String {
        return MihomoConfigDocument.mergeTopLevelMapBlock(
            configText = configText,
            key = "rule-providers",
            editedYaml = editedYaml,
        )
    }
}
