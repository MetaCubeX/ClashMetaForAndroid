package com.github.kr328.clash.service.webdav

data class ProfileIdentityProjection(
    val localProfiles: List<ProfilePayload>,
    val remoteProfiles: List<ProfilePayload>,
    val localUsage: List<ProfileUsagePayload>,
    val remoteUsage: List<ProfileUsagePayload>,
    val uuidByIdentity: Map<String, String>,
    val uuidAliases: Map<String, String>,
)

fun projectProfileIdentities(
    localProfiles: List<ProfilePayload>,
    remoteProfiles: List<ProfilePayload>,
    localUsage: List<ProfileUsagePayload> = emptyList(),
    remoteUsage: List<ProfileUsagePayload> = emptyList(),
): ProfileIdentityProjection {
    requireConsistentProfileIdentities(localProfiles + remoteProfiles)
    val local = localProfiles.groupBy(::profileIdentity)
    val remote = remoteProfiles.groupBy(::profileIdentity)
    val representativeProfiles = (local.keys + remote.keys).associateWith { identity ->
        profileRepresentative(remote[identity] ?: local.getValue(identity))
    }
    val representatives = representativeProfiles.mapValues { it.value.uuid }
    val aliases = (localProfiles + remoteProfiles).associate { profile ->
        profile.uuid to representatives.getValue(profileIdentity(profile))
    }
    fun project(profiles: List<ProfilePayload>): List<ProfilePayload> {
        return profiles.map {
            val representative = representativeProfiles.getValue(profileIdentity(it))
            it.copy(uuid = representative.uuid, preserved = representative.preserved)
        }
    }
    fun projectUsage(usage: List<ProfileUsagePayload>): List<ProfileUsagePayload> {
        return usage.map { it.copy(uuid = aliases[it.uuid] ?: it.uuid) }
    }
    val explicitlyVersionedUsage = (localUsage + remoteUsage).map { it.uuid }.toSet()
    fun withFallback(profiles: List<ProfilePayload>, usage: List<ProfileUsagePayload>): List<ProfileUsagePayload> {
        // Decide fallback before aliases lose their original UUID. Another alias's explicit
        // usage must not suppress this alias's independently versioned legacy counters.
        return usage + profiles.filter { it.uuid !in explicitlyVersionedUsage }.map(::usageFromProfile)
    }
    return ProfileIdentityProjection(
        localProfiles = project(localProfiles),
        remoteProfiles = project(remoteProfiles),
        localUsage = projectUsage(withFallback(localProfiles, localUsage)),
        remoteUsage = projectUsage(withFallback(remoteProfiles, remoteUsage)),
        uuidByIdentity = representatives,
        uuidAliases = aliases,
    )
}

data class ProfileIdentityChange(
    val profile: ProfilePayload,
    val tombstone: TombstonePayload,
)

fun planProfileIdentityChange(
    previous: ProfilePayload,
    next: ProfilePayload,
    newUuid: String,
    writerId: String,
    now: Long,
): ProfileIdentityChange? {
    if (profileIdentity(previous) == profileIdentity(next.copy(uuid = previous.uuid))) return null
    if (newUuid.isBlank() || newUuid == previous.uuid) throw WebdavException("identity_conflict")
    val rev = try {
        Math.addExact(maxOf(previous.syncRev, next.syncRev), 1)
    } catch (_: ArithmeticException) {
        throw WebdavException("bad_revision")
    }
    val changedAt = maxOf(now, previous.updatedAt, next.updatedAt)
    return ProfileIdentityChange(
        profile = next.copy(uuid = newUuid, syncRev = rev, updatedAt = changedAt, writerId = writerId),
        tombstone = TombstonePayload(
            uuid = previous.uuid,
            source = previous.source,
            type = previous.type,
            deletedAt = changedAt,
            rev = previous.syncRev,
            writerId = writerId,
        ),
    )
}

internal fun profileIdentity(profile: ProfilePayload): String {
    return subscriptionIdentity(profile.type, profile.source, profile.uuid)
}

internal fun profileRepresentative(profiles: List<ProfilePayload>): ProfilePayload {
    return profiles.maxWithOrNull(compareBy({ it.updatedAt }, { it.uuid }))!!
}

internal fun requireConsistentProfileIdentities(profiles: List<ProfilePayload>) {
    if (profiles.groupBy { it.uuid }.values.any { aliases -> aliases.map(::profileIdentity).distinct().size > 1 }) {
        throw WebdavException("identity_conflict")
    }
}
