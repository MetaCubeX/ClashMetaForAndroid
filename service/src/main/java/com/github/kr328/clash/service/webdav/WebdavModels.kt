package com.github.kr328.clash.service.webdav

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

internal val webdavJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class ProfileFilePayload(
    val path: String,
    val contentBase64: String,
)

@Serializable
data class ProfilePayload(
    val kind: String = WEBDAV_PROFILE_KIND,
    val version: Int = WEBDAV_SYNC_VERSION,
    val uuid: String,
    val name: String,
    val type: String,
    val source: String = "",
    val interval: Long = 0,
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val ageSecretKey: String? = null,
    val files: List<ProfileFilePayload> = emptyList(),
    val syncRev: Long = 0,
    val writerId: String = "",
    @SerialName("_syncPreserved") val preserved: JsonObject? = null,
)

@Serializable
data class TombstonePayload(
    val uuid: String,
    val source: String = "",
    val type: String = "",
    val deletedAt: Long = 0,
    val rev: Long = 0,
    val writerId: String = "",
)

@Serializable
data class AccessControlPayload(
    val mode: String = "AcceptAll",
    val packages: List<String> = emptyList(),
    val updatedAt: Long = 0,
    val syncRev: Long = 0,
    val writerId: String = "",
)

@Serializable
data class ProfileUsagePayload(
    val uuid: String,
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val updatedAt: Long = 0,
    val writerId: String = "",
)

@Serializable
data class AppsStrategyPayload(
    val uuid: String,
    val name: String,
    val mode: String = "AcceptAll",
    val packages: List<String> = emptyList(),
    val updatedAt: Long = 0,
    val syncRev: Long = 0,
    val writerId: String = "",
)

@Serializable
data class SyncMetaPayload(
    val kind: String = WEBDAV_SYNC_KIND,
    val version: Int = WEBDAV_SYNC_VERSION,
    val updatedAt: Long = 0,
    val accessControlMode: String = "AcceptAll",
    val accessControlPackages: List<String> = emptyList(),
    val accessControlUpdatedAt: Long = 0,
    val appsStrategies: List<AppsStrategyPayload> = emptyList(),
    val appsStrategyTombstones: List<TombstonePayload> = emptyList(),
    val activeAppsStrategy: String? = null,
    val activeAppsStrategyUpdatedAt: Long = 0,
    val activeAppsStrategySyncRev: Long = 0,
    val activeAppsStrategyWriterId: String = "",
    val accessControlSyncRev: Long = 0,
    val accessControlWriterId: String = "",
    val profileUsage: List<ProfileUsagePayload> = emptyList(),
    val tombstones: List<TombstonePayload> = emptyList(),
    @SerialName("_syncPreserved") val preserved: JsonObject? = null,
) {
    fun access(): AccessControlPayload {
        return AccessControlPayload(
            mode = accessControlMode,
            packages = accessControlPackages,
            updatedAt = accessControlUpdatedAt,
            syncRev = accessControlSyncRev,
            writerId = accessControlWriterId,
        )
    }
}

fun encodeProfile(payload: ProfilePayload): String {
    return preserveWireObject(payload.preserved, webdavJson.encodeToJsonElement(ProfilePayload.serializer(), payload).jsonObject).toString()
}

fun encodeMeta(payload: SyncMetaPayload): String {
    return preserveWireObject(payload.preserved, webdavJson.encodeToJsonElement(SyncMetaPayload.serializer(), payload).jsonObject).toString()
}

fun parseProfile(body: String): ProfilePayload? {
    return try {
        val raw = webdavJson.parseToJsonElement(body).jsonObject
        if (raw["kind"] != JsonPrimitive(WEBDAV_PROFILE_KIND) || raw["version"] != JsonPrimitive(WEBDAV_SYNC_VERSION)) return null
        val payload = webdavJson.decodeFromString(ProfilePayload.serializer(), body)
        if (payload.kind != WEBDAV_PROFILE_KIND || payload.version != WEBDAV_SYNC_VERSION) null
        else if (payload.uuid.isBlank() || payload.name.isBlank()) null
        else payload.copy(preserved = raw)
    } catch (_: Exception) {
        null
    }
}

fun parseMeta(body: String): SyncMetaPayload {
    if (body.isBlank()) throw WebdavException("broken_meta")
    val raw = try { webdavJson.parseToJsonElement(body).jsonObject }
    catch (_: Exception) { throw WebdavException("broken_meta") }
    if (raw["kind"] != JsonPrimitive(WEBDAV_SYNC_KIND) || raw["version"] != JsonPrimitive(WEBDAV_SYNC_VERSION)) {
        throw WebdavException("bad_version")
    }
    val payload = try {
        webdavJson.decodeFromString(SyncMetaPayload.serializer(), body)
    } catch (_: Exception) {
        throw WebdavException("broken_meta")
    }
    if (payload.kind != WEBDAV_SYNC_KIND || payload.version != WEBDAV_SYNC_VERSION) {
        throw WebdavException("bad_version")
    }
    return payload.copy(preserved = raw)
}

private fun preserveWireObject(previous: JsonObject?, known: JsonObject): JsonObject {
    val result = previous?.toMutableMap() ?: mutableMapOf()
    result.remove("_syncPreserved")
    for ((key, value) in known) {
        if (key == "_syncPreserved") continue
        val old = previous?.get(key)
        result[key] = if (value is JsonArray && old is JsonArray) {
            val identity = if (key == "files") "path" else "uuid"
            val oldById = old.filterIsInstance<JsonObject>().associateBy { it[identity] }
            JsonArray(value.map { item ->
                if (item is JsonObject && item[identity] != null) preserveWireObject(oldById[item[identity]], item)
                else item
            })
        } else if (value is JsonObject) preserveWireObject(old as? JsonObject, value)
        else value
    }
    return JsonObject(result)
}
