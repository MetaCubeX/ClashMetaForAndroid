package com.github.kr328.clash.service.webdav

data class MergeResult(
    val profiles: List<ProfilePayload>,
    val tombstones: List<TombstonePayload>,
    val access: AccessControlPayload,
    val strategies: List<AppsStrategyPayload> = emptyList(),
    val strategyTombstones: List<TombstonePayload> = emptyList(),
    val activeAppsStrategy: String? = null,
    val activeAppsStrategyUpdatedAt: Long = 0,
    val activeAppsStrategySyncRev: Long = 0,
    val activeAppsStrategyWriterId: String = "",
    val profileUsage: List<ProfileUsagePayload> = emptyList(),
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
    localActiveStrategySyncRev: Long = 0,
    localActiveStrategyWriterId: String = "",
    remoteActiveStrategySyncRev: Long = 0,
    remoteActiveStrategyWriterId: String = "",
    localUsage: List<ProfileUsagePayload> = emptyList(),
    remoteUsage: List<ProfileUsagePayload> = emptyList(),
): MergeResult {
    val tombstones = mergeTombstones(localTombstones + remoteTombstones, now)
    requireConsistentProfileIdentities(localProfiles + remoteProfiles)
    val localByIdentity = localProfiles.groupBy(::profileIdentity)
    val remoteByIdentity = remoteProfiles.groupBy(::profileIdentity)
    val keptTombstones = tombstones.toMutableList()
    val localUsageByUuid = localUsage.groupBy { it.uuid }
    val remoteUsageByUuid = remoteUsage.groupBy { it.uuid }
    val profiles = mutableListOf<ProfilePayload>()
    val usageOut = mutableListOf<ProfileUsagePayload>()

    for (identity in localByIdentity.keys + remoteByIdentity.keys) {
        val local = localByIdentity[identity].orEmpty()
        val remote = remoteByIdentity[identity].orEmpty()
        val all = local + remote
        val representative = profileRepresentative(local.ifEmpty { remote })
        val winner = all.reduce { left, right ->
            if (syncRankCompare(profileRank(right), profileRank(left)) > 0) right else left
        }
        val merged = winner.copy(
            uuid = representative.uuid,
            createdAt = all.map { it.createdAt }.filter { it > 0 }.minOrNull() ?: winner.createdAt,
            preserved = remote.takeIf { it.isNotEmpty() }?.let(::profileRepresentative)?.preserved ?: winner.preserved,
        )
        val tombstone = keptTombstones
            .filter { tombstone -> all.any { matchesTombstone(tombstone, it) } }
            .maxWithOrNull(compareBy({ it.rev }, { it.deletedAt }))
        if (tombstone != null && tombstoneBeats(tombstone, merged.updatedAt, merged.syncRev)) {
            continue
        }
        if (tombstone != null) {
            keptTombstones.removeAll { tombstone -> all.any { matchesTombstone(tombstone, it) } }
        }
        val candidates = ArrayList<ProfileUsagePayload>()
        for (profile in all) {
            val explicit = localUsageByUuid[profile.uuid].orEmpty() + remoteUsageByUuid[profile.uuid].orEmpty()
            if (explicit.isEmpty()) candidates.add(usageFromProfile(profile)) else candidates.addAll(explicit)
        }
        val withUsage = overlayUsage(merged, candidates)
        val recorded = candidates.reduce(::preferUsage).copy(uuid = withUsage.uuid)
        usageOut.add(recorded)
        profiles.add(withUsage)
    }

    val strategyMerge = mergeStrategies(
        localStrategies,
        remoteStrategies,
        localStrategyTombstones,
        remoteStrategyTombstones,
        now,
    )
    val access = pickAccess(localAccess, remoteAccess)
    val active = pickActive(
        localId = localActiveStrategy,
        localAt = localActiveStrategyUpdatedAt,
        localRev = localActiveStrategySyncRev,
        localWriter = localActiveStrategyWriterId,
        remoteId = remoteActiveStrategy,
        remoteAt = remoteActiveStrategyUpdatedAt,
        remoteRev = remoteActiveStrategySyncRev,
        remoteWriter = remoteActiveStrategyWriterId,
    )
    val activeUuid = active.id?.takeIf { id -> strategyMerge.strategies.any { it.uuid == id } }
    return MergeResult(
        profiles = profiles.sortedBy { it.uuid },
        tombstones = keptTombstones.sortedBy { it.uuid },
        access = pickAccess(localAccess, remoteAccess),
        strategies = strategyMerge.strategies,
        strategyTombstones = strategyMerge.tombstones,
        activeAppsStrategy = activeUuid,
        activeAppsStrategyUpdatedAt = active.updatedAt,
        activeAppsStrategySyncRev = active.syncRev,
        activeAppsStrategyWriterId = active.writerId,
        profileUsage = usageOut.sortedBy { it.uuid },
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
        val localItem = localById[uuid]
        val remoteItem = remoteById[uuid]
        val merged = when {
            localItem == null -> remoteItem
            remoteItem == null -> localItem
            syncRankCompare(strategyRank(remoteItem), strategyRank(localItem)) > 0 -> remoteItem
            else -> localItem
        } ?: continue
        val tombstone = tombstones.filter { it.uuid == merged.uuid }.maxWithOrNull(compareBy({ it.rev }, { it.deletedAt }))
        if (tombstone != null && tombstoneBeats(tombstone, merged.updatedAt, merged.syncRev)) continue
        if (tombstone != null) tombstones.removeAll { it.uuid == merged.uuid }
        kept.add(merged)
    }
    return StrategyMerge(kept.sortedBy { it.uuid }, tombstones.sortedBy { it.uuid })
}

internal fun pickAccess(local: AccessControlPayload, remote: AccessControlPayload): AccessControlPayload {
    return if (syncRankCompare(accessRank(remote), accessRank(local)) > 0) remote else local
}

internal fun mergeProfile(local: ProfilePayload, remote: ProfilePayload): ProfilePayload {
    val winner = if (syncRankCompare(profileRank(remote), profileRank(local)) > 0) remote else local
    val createdAt = listOf(local.createdAt, remote.createdAt).filter { it > 0 }.minOrNull() ?: winner.createdAt
    return winner.copy(uuid = local.uuid, createdAt = createdAt, preserved = remote.preserved ?: winner.preserved)
}

internal fun mergeMeta(local: SyncMetaPayload, remote: SyncMetaPayload, now: Long): SyncMetaPayload {
    val strategies = mergeStrategies(
        local.appsStrategies,
        remote.appsStrategies,
        local.appsStrategyTombstones,
        remote.appsStrategyTombstones,
        now,
    )
    val access = pickAccess(local.access(), remote.access())
    val active = pickActive(
        localId = local.activeAppsStrategy,
        localAt = local.activeAppsStrategyUpdatedAt,
        localRev = local.activeAppsStrategySyncRev,
        localWriter = local.activeAppsStrategyWriterId,
        remoteId = remote.activeAppsStrategy,
        remoteAt = remote.activeAppsStrategyUpdatedAt,
        remoteRev = remote.activeAppsStrategySyncRev,
        remoteWriter = remote.activeAppsStrategyWriterId,
    )
    val activeUuid = active.id?.takeIf { id -> strategies.strategies.any { it.uuid == id } }
    return metaFrom(
        MergeResult(
            profiles = emptyList(),
            tombstones = mergeTombstones(local.tombstones + remote.tombstones, now),
            access = access,
            strategies = strategies.strategies,
            strategyTombstones = strategies.tombstones,
            activeAppsStrategy = activeUuid,
            activeAppsStrategyUpdatedAt = active.updatedAt,
            activeAppsStrategySyncRev = active.syncRev,
            activeAppsStrategyWriterId = active.writerId,
            profileUsage = mergeUsageLists(local.profileUsage, remote.profileUsage),
        ),
        updatedAt = now,
        legacyAccess = access,
    ).copy(preserved = remote.preserved ?: local.preserved)
}

internal fun mergeUsageLists(
    left: List<ProfileUsagePayload>,
    right: List<ProfileUsagePayload>,
): List<ProfileUsagePayload> {
    val ids = (left.map { it.uuid } + right.map { it.uuid }).toSet()
    return ids.map { id ->
        (left.filter { it.uuid == id } + right.filter { it.uuid == id }).reduce(::preferUsage)
    }.sortedBy { it.uuid }
}

fun metaFrom(
    merged: MergeResult,
    updatedAt: Long = 0,
    legacyAccess: AccessControlPayload = AccessControlPayload(),
): SyncMetaPayload {
    return SyncMetaPayload(
        updatedAt = updatedAt,
        accessControlMode = legacyAccess.mode,
        accessControlPackages = legacyAccess.packages,
        accessControlUpdatedAt = legacyAccess.updatedAt,
        accessControlSyncRev = legacyAccess.syncRev,
        accessControlWriterId = legacyAccess.writerId,
        appsStrategies = merged.strategies,
        appsStrategyTombstones = merged.strategyTombstones,
        activeAppsStrategy = merged.activeAppsStrategy,
        activeAppsStrategyUpdatedAt = merged.activeAppsStrategyUpdatedAt,
        activeAppsStrategySyncRev = merged.activeAppsStrategySyncRev,
        activeAppsStrategyWriterId = merged.activeAppsStrategyWriterId,
        profileUsage = merged.profileUsage.sortedBy { it.uuid },
        tombstones = merged.tombstones,
    )
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
        val replace = previous == null || when {
            tombstone.rev != previous.rev -> tombstone.rev > previous.rev
            else -> tombstone.deletedAt > previous.deletedAt
        }
        if (replace) kept[key] = tombstone
    }
    return kept.values.toList()
}

internal fun tombstoneBeats(tombstone: TombstonePayload, updatedAt: Long, syncRev: Long): Boolean {
    if (syncRev != tombstone.rev) return tombstone.rev > syncRev
    return tombstone.deletedAt >= updatedAt
}

private data class ActivePick(
    val id: String?,
    val updatedAt: Long,
    val syncRev: Long,
    val writerId: String,
)

private fun pickActive(
    localId: String?,
    localAt: Long,
    localRev: Long,
    localWriter: String,
    remoteId: String?,
    remoteAt: Long,
    remoteRev: Long,
    remoteWriter: String,
): ActivePick {
    val local = ActivePick(localId, localAt, localRev, localWriter)
    val remote = ActivePick(remoteId, remoteAt, remoteRev, remoteWriter)
    val remoteWins = syncRankCompare(
        SyncRank(remote.syncRev, remote.updatedAt, remote.writerId, remote.id.orEmpty()),
        SyncRank(local.syncRev, local.updatedAt, local.writerId, local.id.orEmpty()),
    ) > 0
    return if (remoteWins) remote else local
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
