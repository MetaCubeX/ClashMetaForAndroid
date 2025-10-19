package com.github.kr328.clash.design.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kr328.clash.core.model.Provider

data class ProxyState(
    val name: String,
    var now: String,
    val delay: Int = 0
)

class ProviderState(
    val provider: Provider,
    updatedAt: Long,
    updating: Boolean,
) {
    var updatedAt: Long by mutableStateOf(updatedAt)

    var updating: Boolean by mutableStateOf(updating)
}

