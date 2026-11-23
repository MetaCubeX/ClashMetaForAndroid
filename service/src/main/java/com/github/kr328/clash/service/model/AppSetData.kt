package com.github.kr328.clash.service.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSetData(
    val id: String,
    val name: String,
    val packages: List<String>,
    val isBuiltin: Boolean = false,
    val description: String = "",
    /** Missing on older files. Built-in sets then stay on their saved package list. */
    val membershipFrozen: Boolean = true,
)
