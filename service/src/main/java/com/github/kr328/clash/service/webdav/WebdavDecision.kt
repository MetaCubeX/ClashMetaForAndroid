package com.github.kr328.clash.service.webdav

import java.security.MessageDigest

data class SyncRank(
    val syncRev: Long,
    val updatedAt: Long,
    val writerId: String,
    val contentHash: String,
)

data class WebdavRouteMemo(
    val key: String,
    val base: String,
    val at: Long,
)

sealed class ProfileBodyRead {
    data class Ok(val profile: ProfilePayload, val fingerprint: String) : ProfileBodyRead()
    data object Unreadable : ProfileBodyRead()
}

fun syncRankCompare(left: SyncRank, right: SyncRank): Int {
    if (left.syncRev != right.syncRev) return left.syncRev.compareTo(right.syncRev)
    if (left.updatedAt != right.updatedAt) return left.updatedAt.compareTo(right.updatedAt)
    val writer = left.writerId.compareTo(right.writerId)
    if (writer != 0) return writer
    return left.contentHash.compareTo(right.contentHash)
}

fun profileRank(profile: ProfilePayload): SyncRank {
    return SyncRank(profile.syncRev, profile.updatedAt, profile.writerId, profileContentHash(profile))
}

fun strategyRank(strategy: AppsStrategyPayload): SyncRank {
    return SyncRank(strategy.syncRev, strategy.updatedAt, strategy.writerId, strategyContentHash(strategy))
}

fun accessRank(access: AccessControlPayload): SyncRank {
    return SyncRank(access.syncRev, access.updatedAt, access.writerId, accessContentHash(access))
}

fun usageRank(usage: ProfileUsagePayload): SyncRank {
    return SyncRank(0, usage.updatedAt, usage.writerId, usageContentHash(usage))
}

fun fingerprint(files: List<ProfileFilePayload>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (file in files.sortedBy { it.path }) {
        digest.update(file.path.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(file.contentBase64.toByteArray(Charsets.UTF_8))
        digest.update(0)
    }
    return digest.digest().toHex()
}

fun profileContentHash(profile: ProfilePayload): String {
    return sha256Hex(
        listOf(
            profile.name,
            profile.type,
            normalizeSubscriptionSource(profile.source),
            profile.interval.toString(),
            profile.ageSecretKey.orEmpty(),
            fingerprint(profile.files),
        ).joinToString("\u0000"),
    )
}

fun profileBodyFingerprint(profile: ProfilePayload): String {
    return sha256Hex(
        listOf(
            profileContentHash(profile),
            profile.syncRev.toString(),
            profile.writerId,
            profile.updatedAt.toString(),
        ).joinToString("\u0000"),
    )
}

fun strategyContentHash(strategy: AppsStrategyPayload): String {
    return sha256Hex(
        listOf(
            strategy.name,
            strategy.mode,
            strategy.packages.sorted().joinToString("\n"),
        ).joinToString("\u0000"),
    )
}

fun accessContentHash(access: AccessControlPayload): String {
    return sha256Hex(
        listOf(
            access.mode,
            access.packages.sorted().joinToString("\n"),
        ).joinToString("\u0000"),
    )
}

fun usageContentHash(usage: ProfileUsagePayload): String {
    return sha256Hex("${usage.upload}\u0000${usage.download}\u0000${usage.total}\u0000${usage.expire}")
}

fun preferUsage(left: ProfileUsagePayload, right: ProfileUsagePayload): ProfileUsagePayload {
    return if (syncRankCompare(usageRank(left), usageRank(right)) >= 0) left else right
}

fun usageFromProfile(profile: ProfilePayload): ProfileUsagePayload {
    return ProfileUsagePayload(
        uuid = profile.uuid,
        upload = profile.upload,
        download = profile.download,
        total = profile.total,
        expire = profile.expire,
        updatedAt = profile.updatedAt,
        writerId = profile.writerId,
    )
}

fun overlayUsage(profile: ProfilePayload, candidates: List<ProfileUsagePayload>): ProfilePayload {
    if (candidates.isEmpty()) return profile
    val best = candidates.reduce(::preferUsage)
    return profile.copy(
        upload = best.upload,
        download = best.download,
        total = best.total,
        expire = best.expire,
    )
}

fun sameProfileUsage(left: ProfilePayload, right: ProfilePayload): Boolean {
    return left.upload == right.upload &&
        left.download == right.download &&
        left.total == right.total &&
        left.expire == right.expire
}

fun readProfileBody(body: String): ProfileBodyRead {
    if (body.isBlank()) return ProfileBodyRead.Unreadable
    val parsed = parseProfile(body) ?: return ProfileBodyRead.Unreadable
    return ProfileBodyRead.Ok(parsed, profileBodyFingerprint(parsed))
}

fun shouldSkipProfileGet(
    listedEtag: String?,
    cachedEtag: String?,
    cachedFingerprint: String?,
    presentLocally: Boolean,
): Boolean {
    if (!presentLocally || cachedFingerprint.isNullOrBlank()) return false
    return webdavEtagsMatch(listedEtag, cachedEtag)
}

fun isDeletableRemoteProfile(name: String, kept: Set<String>, unreadable: Set<String>): Boolean {
    return isProfileFileName(name) && name !in kept && name !in unreadable
}

fun webdavRouteKey(url: String, lanUrl: String, remotePath: String, allowLan: Boolean): String {
    return listOf(
        url.trim().trimEnd('/'),
        lanUrl.trim().trimEnd('/'),
        remotePath.trim(),
        if (allowLan) "lan" else "wan",
    ).joinToString("\n")
}

fun freshWebdavRoute(
    memo: WebdavRouteMemo?,
    key: String,
    now: Long,
    ttlMs: Long = WEBDAV_ROUTE_MEMO_MS,
): String? {
    if (memo == null || memo.key != key) return null
    if (now - memo.at >= ttlMs) return null
    return memo.base
}

fun canonicalMeta(meta: SyncMetaPayload): SyncMetaPayload {
    return meta.copy(
        preserved = null,
        updatedAt = 0,
        accessControlPackages = meta.accessControlPackages.sorted(),
        appsStrategies = meta.appsStrategies
            .map { it.copy(packages = it.packages.sorted()) }
            .sortedBy { it.uuid },
        appsStrategyTombstones = meta.appsStrategyTombstones.sortedWith(
            compareBy({ it.uuid }, { it.deletedAt }, { it.rev }),
        ),
        tombstones = meta.tombstones.sortedWith(
            compareBy({ it.uuid }, { it.source }, { it.deletedAt }, { it.rev }),
        ),
        profileUsage = meta.profileUsage.sortedBy { it.uuid },
    )
}

fun sameSyncMeta(left: SyncMetaPayload, right: SyncMetaPayload): Boolean {
    return canonicalMeta(left) == canonicalMeta(right)
}

private fun sha256Hex(text: String): String {
    return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).toHex()
}

private fun ByteArray.toHex(): String {
    return joinToString("") { "%02x".format(it) }
}
