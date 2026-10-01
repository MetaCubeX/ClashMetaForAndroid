package com.github.kr328.clash.service.webdav

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
)

@Serializable
data class TombstonePayload(
    val uuid: String,
    val source: String = "",
    val type: String = "",
    val deletedAt: Long = 0,
)

@Serializable
data class AccessControlPayload(
    val mode: String = "AcceptAll",
    val packages: List<String> = emptyList(),
    val updatedAt: Long = 0,
)

@Serializable
data class AppsStrategyPayload(
    val uuid: String,
    val name: String,
    val mode: String = "AcceptAll",
    val packages: List<String> = emptyList(),
    val updatedAt: Long = 0,
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
    val tombstones: List<TombstonePayload> = emptyList(),
) {
    fun access(): AccessControlPayload {
        return AccessControlPayload(accessControlMode, accessControlPackages, accessControlUpdatedAt)
    }
}

fun encodeProfile(payload: ProfilePayload): String {
    return webdavJson.encodeToString(ProfilePayload.serializer(), payload)
}

fun encodeMeta(payload: SyncMetaPayload): String {
    return webdavJson.encodeToString(SyncMetaPayload.serializer(), payload)
}

fun parseProfile(body: String): ProfilePayload? {
    return try {
        val payload = webdavJson.decodeFromString(ProfilePayload.serializer(), body)
        if (payload.kind != WEBDAV_PROFILE_KIND || payload.version != WEBDAV_SYNC_VERSION) null
        else if (payload.uuid.isBlank() || payload.name.isBlank()) null
        else payload
    } catch (_: Exception) {
        null
    }
}

fun parseMeta(body: String): SyncMetaPayload {
    if (body.isBlank()) throw WebdavException("broken_meta")
    val payload = try {
        webdavJson.decodeFromString(SyncMetaPayload.serializer(), body)
    } catch (_: Exception) {
        throw WebdavException("broken_meta")
    }
    if (payload.kind != WEBDAV_SYNC_KIND || payload.version != WEBDAV_SYNC_VERSION) {
        throw WebdavException("bad_version")
    }
    return payload
}
