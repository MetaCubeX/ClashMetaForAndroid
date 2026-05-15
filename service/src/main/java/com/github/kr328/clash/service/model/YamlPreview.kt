package com.github.kr328.clash.service.model

import kotlinx.serialization.Serializable

@Serializable
data class YamlPreview(
    val id: String,
    val title: String,
    val currentYaml: String,
    val proposedYaml: String,
    val diff: String,
    val valid: Boolean,
    val error: String? = null,
)
