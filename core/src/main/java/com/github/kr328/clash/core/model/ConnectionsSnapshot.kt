package com.github.kr328.clash.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionsSnapshot(
    @SerialName("downloadTotal") val downloadTotal: Long = 0,
    @SerialName("uploadTotal") val uploadTotal: Long = 0,
    val memory: Long = 0,
    val connections: List<ConnectionTracker> = emptyList(),
)

@Serializable
data class ConnectionTracker(
    val id: String = "",
    val upload: Long = 0,
    val download: Long = 0,
    val rule: String = "",
    val rulePayload: String = "",
    val chains: List<String> = emptyList(),
    @SerialName("providerChains") val providerChains: List<String> = emptyList(),
    val metadata: ConnectionMetadata? = null,
)

@Serializable
data class ConnectionMetadata(
    val host: String = "",
    val network: String = "",
    val process: String = "",
    val uid: Int = 0,
    @SerialName("sourceIP") val sourceIP: String = "",
    @SerialName("destinationIP") val destinationIP: String = "",
    @SerialName("sourcePort") val sourcePort: String = "",
    @SerialName("destinationPort") val destinationPort: String = "",
    @SerialName("inboundName") val inboundName: String = "",
    @SerialName("sniffHost") val sniffHost: String = "",
)
