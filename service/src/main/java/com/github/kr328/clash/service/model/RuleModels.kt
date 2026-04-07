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
    val enabled: Boolean = true,
    val source: RuleSource = RuleSource.MANUAL,
)

@Serializable
data class RuleState(
    val providers: List<RuleProviderItem> = emptyList(),
    val rules: List<RuleItem> = emptyList(),
)
