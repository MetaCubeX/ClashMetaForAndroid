package com.github.kr328.clash.service.model

import com.github.kr328.clash.service.util.UUIDSerializer
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.util.*

@OptIn(InternalSerializationApi::class)
@Serializable
class AppsStrategyConfig(
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID,
    val name: String,
    val mode: AccessControlMode,
    val packages: List<String>
) {
    companion object {
        fun create(
            name: String,
            mode: AccessControlMode,
            packages: List<String>
        ): AppsStrategyConfig {
            return AppsStrategyConfig(UUID.randomUUID(), name, mode, packages)
        }
    }
}
