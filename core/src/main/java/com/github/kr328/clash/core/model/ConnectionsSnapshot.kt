package com.github.kr328.clash.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

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
    val start: String = "",
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
    @SerialName("remoteDestination") val remoteDestination: String = "",
    @SerialName("processPath") val processPath: String = "",
    @Serializable(with = FlexibleStringListSerializer::class)
    @SerialName("sourceGeoIP") val sourceGeoIP: List<String> = emptyList(),
    @Serializable(with = FlexibleStringListSerializer::class)
    @SerialName("destinationGeoIP") val destinationGeoIP: List<String> = emptyList(),
    @SerialName("sourceIPASN") val sourceIPASN: String = "",
    @SerialName("destinationIPASN") val destinationIPASN: String = "",
)

object FlexibleStringListSerializer :
    JsonTransformingSerializer<List<String>>(ListSerializer(String.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        JsonNull -> JsonArray(emptyList())
        is JsonArray -> element
        is JsonPrimitive -> {
            if (element.isString) JsonArray(listOf(element)) else JsonArray(emptyList())
        }
        else -> JsonArray(emptyList())
    }
}
