package com.github.kr328.clash.service.model

import kotlinx.serialization.Serializable

@Serializable
enum class RuleSource {
    MANUAL,
    PROVIDER,
}

@Serializable
data class RuleItem(
    val id: String,
    val raw: String = "",
    val type: String,
    val value: String = "",
    val policy: String,
    val enabled: Boolean = true,
    val deleted: Boolean = false,
    val source: RuleSource = RuleSource.MANUAL,
    val providerName: String? = null,
    val isRestorable: Boolean = false,
    val order: Int = 0,
)

@Serializable
data class RuleProviderItem(
    val id: String,
    val name: String,
    val type: String = "http",
    val behavior: String = "classical",
    val url: String,
    val interval: Int = 86400,
    val path: String = "",
    /**
     * mihomo's optional `format:` field. Empty means "not set" — mihomo will
     * treat it as `yaml`. For `.mrs` providers (binary rule-set) the value
     * MUST be `"mrs"`, otherwise mihomo will try to parse the binary file as
     * YAML and fail with `file must have a 'payload' field`. Round-tripped
     * through state file → mergeStateIntoConfig so user edits don't strip it.
     */
    val format: String = "",
    val enabled: Boolean = true,
    val source: RuleSource = RuleSource.MANUAL,
)

@Serializable
data class RuleState(
    val providers: List<RuleProviderItem> = emptyList(),
    val rules: List<RuleItem> = emptyList(),
)
