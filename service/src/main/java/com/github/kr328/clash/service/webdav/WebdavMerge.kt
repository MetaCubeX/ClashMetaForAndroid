package com.github.kr328.clash.service.webdav

data class MergeResult(
    val profiles: List<ProfilePayload>,
    val tombstones: List<TombstonePayload>,
    val access: AccessControlPayload,
    val strategies: List<AppsStrategyPayload> = emptyList(),
    val strategyTombstones: List<TombstonePayload> = emptyList(),
    val activeAppsStrategy: String? = null,
    val activeAppsStrategyUpdatedAt: Long = 0,
)

fun mergeWebdav(
    localProfiles: List<ProfilePayload>,
    remoteProfiles: List<ProfilePayload>,
    localTombstones: List<TombstonePayload>,
    remoteTombstones: List<TombstonePayload>,
    localAccess: AccessControlPayload,
    remoteAccess: AccessControlPayload,
    now: Long,
    localStrategies: List<AppsStrategyPayload> = emptyList(),
    remoteStrategies: List<AppsStrategyPayload> = emptyList(),
    localStrategyTombstones: List<TombstonePayload> = emptyList(),
    remoteStrategyTombstones: List<TombstonePayload> = emptyList(),
    localActiveStrategy: String? = null,
    localActiveStrategyUpdatedAt: Long = 0,
    remoteActiveStrategy: String? = null,
    remoteActiveStrategyUpdatedAt: Long = 0,
): MergeResult {
    val tombstones = mergeTombstones(localTombstones + remoteTombstones, now)
    val localByIdentity = collapseProfiles(localProfiles)
    val remoteByIdentity = collapseProfiles(remoteProfiles)
    val keptTombstones = tombstones.toMutableList()
    val profiles = mutableListOf<ProfilePayload>()

    for (identity in localByIdentity.keys + remoteByIdentity.keys) {
        val local = localByIdentity[identity]
        val remote = remoteByIdentity[identity]
        val merged = when {
            local == null -> remote
            remote == null -> local
            else -> mergeProfile(local, remote)
        } ?: continue
        val tombstone = keptTombstones
            .filter { matchesTombstone(it, merged) }
            .maxByOrNull { it.deletedAt }
        if (tombstone != null && tombstone.deletedAt >= merged.updatedAt) {
            continue
        }
        if (tombstone != null) {
            keptTombstones.removeAll { matchesTombstone(it, merged) }
        }
        profiles.add(merged)
    }

    val strategyMerge = mergeStrategies(
        localStrategies,
        remoteStrategies,
        localStrategyTombstones,
        remoteStrategyTombstones,
        now,
    )
    val active = if (remoteActiveStrategyUpdatedAt > localActiveStrategyUpdatedAt) {
        remoteActiveStrategy to remoteActiveStrategyUpdatedAt
    } else {
        localActiveStrategy to localActiveStrategyUpdatedAt
    }
    val activeUuid = active.first?.takeIf { id -> strategyMerge.strategies.any { it.uuid == id } }
    return MergeResult(
        profiles = profiles.sortedBy { it.uuid },
        tombstones = keptTombstones.sortedBy { it.uuid },
        access = if (remoteAccess.updatedAt > localAccess.updatedAt) remoteAccess else localAccess,
        strategies = strategyMerge.strategies,
        strategyTombstones = strategyMerge.tombstones,
        activeAppsStrategy = activeUuid,
        activeAppsStrategyUpdatedAt = if (activeUuid == null) active.second else active.second,
    )
}

data class StrategyMerge(
    val strategies: List<AppsStrategyPayload>,
    val tombstones: List<TombstonePayload>,
)

fun mergeStrategies(
    local: List<AppsStrategyPayload>,
    remote: List<AppsStrategyPayload>,
    localTombstones: List<TombstonePayload>,
    remoteTombstones: List<TombstonePayload>,
    now: Long,
): StrategyMerge {
    val tombstones = mergeTombstones(localTombstones + remoteTombstones, now).toMutableList()
    val localById = collapseStrategies(local)
    val remoteById = collapseStrategies(remote)
    val kept = mutableListOf<AppsStrategyPayload>()
    for (uuid in localById.keys + remoteById.keys) {
        val merged = when {
            localById[uuid] == null -> remoteById[uuid]
            remoteById[uuid] == null -> localById[uuid]
            remoteById.getValue(uuid).updatedAt > localById.getValue(uuid).updatedAt -> remoteById[uuid]
            else -> localById[uuid]
        } ?: continue
        val tombstone = tombstones.filter { it.uuid == merged.uuid }.maxByOrNull { it.deletedAt }
        if (tombstone != null && tombstone.deletedAt >= merged.updatedAt) continue
        if (tombstone != null) tombstones.removeAll { it.uuid == merged.uuid }
        kept.add(merged)
    }
    return StrategyMerge(kept.sortedBy { it.uuid }, tombstones.sortedBy { it.uuid })
}

/**
 * Keep the merge result. Tombstones recorded after [snapshot] was taken are still new
 * and must be kept. Tombstones the merge already dropped are not written back.
 */
fun retainMergedTombstones(
    merged: List<TombstonePayload>,
    snapshot: List<TombstonePayload>,
    current: List<TombstonePayload>,
    now: Long,
): List<TombstonePayload> {
    val seen = snapshot.toSet()
    return mergeTombstones(merged + current.filter { it !in seen }, now)
}

fun mergeTombstones(tombstones: List<TombstonePayload>, now: Long): List<TombstonePayload> {
    val kept = linkedMapOf<String, TombstonePayload>()
    for (tombstone in tombstones) {
        if (tombstone.deletedAt <= 0 || now - tombstone.deletedAt > WEBDAV_TOMBSTONE_TTL_MS) continue
        val key = tombstoneKey(tombstone)
        val previous = kept[key]
        if (previous == null || tombstone.deletedAt > previous.deletedAt) {
            kept[key] = tombstone
        }
    }
    return kept.values.toList()
}

private fun collapseProfiles(profiles: List<ProfilePayload>): Map<String, ProfilePayload> {
    return profiles.preferNewer(
        identity = { subscriptionIdentity(it.type, it.source, it.uuid) },
        updatedAt = { it.updatedAt },
        tie = { it.uuid },
    )
}

private fun collapseStrategies(strategies: List<AppsStrategyPayload>): Map<String, AppsStrategyPayload> {
    return strategies.preferNewer(
        identity = { it.uuid },
        updatedAt = { it.updatedAt },
        tie = { it.uuid },
    )
}

private fun <T> List<T>.preferNewer(
    identity: (T) -> String,
    updatedAt: (T) -> Long,
    tie: (T) -> String,
): Map<String, T> {
    val best = linkedMapOf<String, T>()
    for (item in this) {
        val key = identity(item)
        val previous = best[key]
        val newer = previous == null ||
            updatedAt(item) > updatedAt(previous) ||
            (updatedAt(item) == updatedAt(previous) && tie(item) > tie(previous))
        if (newer) best[key] = item
    }
    return best
}

private fun mergeProfile(local: ProfilePayload, remote: ProfilePayload): ProfilePayload {
    val config = if (remote.updatedAt > local.updatedAt) remote else local
    val createdAt = listOf(local.createdAt, remote.createdAt).filter { it > 0 }.minOrNull()
        ?: config.createdAt
    return config.copy(
        uuid = local.uuid,
        createdAt = createdAt,
    )
}

fun matchesTombstone(tombstone: TombstonePayload, profile: ProfilePayload): Boolean {
    if (tombstone.uuid.isNotBlank() && tombstone.uuid == profile.uuid) return true
    if (profile.type != "Url" && profile.type != "External") return false
    val source = normalizeSubscriptionSource(profile.source)
    if (source.isEmpty() || normalizeSubscriptionSource(tombstone.source) != source) return false
    val type = if (tombstone.type.isBlank()) profile.type else tombstone.type
    return type == "Url" || type == "External"
}

private fun tombstoneKey(tombstone: TombstonePayload): String {
    val source = normalizeSubscriptionSource(tombstone.source)
    if ((tombstone.type == "Url" || tombstone.type == "External") && source.isNotEmpty()) {
        return "src:$source"
    }
    return "id:${tombstone.uuid}"
}
