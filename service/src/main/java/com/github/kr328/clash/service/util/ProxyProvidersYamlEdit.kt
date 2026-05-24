package com.github.kr328.clash.service.util

/** Merge/edit `proxy-providers` in a Clash config.yaml (same idea as [RuleProvidersYamlEdit]). */
object ProxyProvidersYamlEdit {
    /** Returns only the `proxy-providers` subtree as YAML text, or null if missing. */
    fun extractBlock(configText: String): String? {
        return MihomoConfigDocument.parse(configText)
            ?.extractTopLevelBlock("proxy-providers")
    }

    /**
     * Merges edited YAML into [configText]. [editedYaml] may be either the full
     * `proxy-providers:` document or only the inner map (keys under proxy-providers).
     */
    fun mergeIntoConfig(configText: String, editedYaml: String): String {
        return MihomoConfigDocument.mergeTopLevelMapBlock(
            configText = configText,
            key = "proxy-providers",
            editedYaml = editedYaml,
        )
    }
}
