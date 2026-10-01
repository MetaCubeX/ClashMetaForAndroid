package com.github.kr328.clash.service.webdav

import android.content.Context
import android.util.Base64
import com.github.kr328.clash.service.ProfileProcessor
import com.github.kr328.clash.service.ProfileReceiver
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendProfileChanged
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class LocalSnapshot(
    val profiles: List<ProfilePayload>,
    val access: AccessControlPayload,
    val tombstones: List<TombstonePayload>,
)

class WebdavProfiles(private val context: Context) {
    private val state = WebdavLocalState(context)

    suspend fun exportLocal(): LocalSnapshot {
        val local = state.read()
        val profiles = ImportedDao().queryAllUUIDs().mapNotNull { uuid ->
            if (PendingDao().exists(uuid)) return@mapNotNull null
            val imported = ImportedDao().queryByUUID(uuid) ?: return@mapNotNull null
            val files = readFiles(context.importedDir.resolve(uuid.toString()))
            val fingerprint = fingerprint(files)
            val storedFingerprint = local.fingerprint[uuid.toString()]
            val storedUpdatedAt = local.configUpdatedAt[uuid.toString()] ?: 0L
            val fileUpdatedAt = files.maxOfOrNull { fileUpdatedAt(context, uuid, it.path) } ?: imported.createdAt
            val updatedAt = if (fingerprint == storedFingerprint && storedUpdatedAt > 0) {
                storedUpdatedAt
            } else {
                fileUpdatedAt.coerceAtLeast(1)
            }
            ProfilePayload(
                uuid = uuid.toString(),
                name = imported.name,
                type = imported.type.name,
                source = imported.source,
                interval = imported.interval,
                upload = imported.upload,
                download = imported.download,
                total = imported.total,
                expire = imported.expire,
                createdAt = imported.createdAt,
                updatedAt = updatedAt,
                ageSecretKey = imported.ageSecretKey,
                files = files,
            )
        }
        val store = ServiceStore(context)
        val packages = store.accessControlPackages.sorted()
        val mode = store.accessControlMode.name
        var accessUpdatedAt = local.accessControlUpdatedAt
        if (accessUpdatedAt == 0L && (packages.isNotEmpty() || mode != AccessControlMode.AcceptAll.name)) {
            val stamped = System.currentTimeMillis()
            state.update { current ->
                if (current.accessControlUpdatedAt > 0) current
                else current.copy(accessControlUpdatedAt = stamped)
            }
            accessUpdatedAt = state.read().accessControlUpdatedAt
        }
        return LocalSnapshot(
            profiles = profiles,
            access = AccessControlPayload(
                mode = mode,
                packages = packages,
                updatedAt = accessUpdatedAt,
            ),
            tombstones = local.tombstones,
        )
    }

    suspend fun apply(result: MergeResult, local: LocalSnapshot): ApplyCounts {
        var downloaded = 0
        var removed = 0
        var skipped = 0
        val localByUuid = local.profiles.associateBy { it.uuid }
        val mergedIds = result.profiles.map { it.uuid }.toSet()

        for (profile in result.profiles) {
            val previous = localByUuid[profile.uuid]
                ?: local.profiles.find { sameSubscription(it, profile) }
            if (previous != null && sameContent(previous, profile)) continue
            val installed = install(profile)
            if (!installed) {
                skipped += 1
            } else if (previous == null || profile.updatedAt > previous.updatedAt) {
                downloaded += 1
            }
        }

        for (profile in local.profiles) {
            if (profile.uuid in mergedIds) continue
            if (local.profiles.any { it.uuid != profile.uuid && sameSubscription(it, profile) && it.uuid in mergedIds }) {
                continue
            }
            val stillThere = result.profiles.any { sameSubscription(it, profile) }
            if (stillThere) continue
            if (PendingDao().exists(UUID.fromString(profile.uuid))) {
                skipped += 1
                continue
            }
            remove(UUID.fromString(profile.uuid))
            removed += 1
        }

        applyAccess(result.access, local.access)
        return ApplyCounts(downloaded = downloaded, removed = removed, skipped = skipped)
    }

    private suspend fun install(payload: ProfilePayload): Boolean {
        val uuid = UUID.fromString(payload.uuid)
        val type = runCatching { Profile.Type.valueOf(payload.type) }.getOrElse { Profile.Type.File }
        return ProfileProcessor.guardImported {
            if (PendingDao().exists(uuid)) return@guardImported false
            val dir = context.importedDir.resolve(uuid.toString())
            val staging = context.importedDir.resolve("$uuid.webdav-tmp")
            staging.deleteRecursively()
            staging.mkdirs()
            var total = 0
            for (file in payload.files) {
                val rel = safeRelativePath(file.path)
                if (!isSyncedProfileFile(rel)) continue
                val bytes = Base64.decode(file.contentBase64, Base64.DEFAULT)
                if (bytes.size > WEBDAV_MAX_FILE_BYTES || total + bytes.size > WEBDAV_MAX_PROFILE_BYTES) {
                    throw WebdavException("too_large", detail = payload.name)
                }
                total += bytes.size
                val dest = staging.resolve(rel)
                dest.parentFile?.mkdirs()
                val rootPath = staging.canonicalFile.path + File.separator
                if (!dest.canonicalFile.path.startsWith(rootPath)) {
                    throw WebdavException("bad_path")
                }
                dest.writeBytes(bytes)
            }
            if (!staging.resolve("config.yaml").exists()) {
                staging.resolve("config.yaml").writeText("")
            }
            dir.deleteRecursively()
            if (!staging.renameTo(dir)) {
                staging.copyRecursively(dir, overwrite = true)
                staging.deleteRecursively()
            }
            val createdAt = payload.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            val row = Imported(
                uuid = uuid,
                name = payload.name.ifBlank { "Profile" },
                type = type,
                source = payload.source,
                interval = payload.interval,
                upload = payload.upload,
                download = payload.download,
                total = payload.total,
                expire = payload.expire,
                createdAt = createdAt,
                ageSecretKey = payload.ageSecretKey,
            )
            if (ImportedDao().exists(uuid)) ImportedDao().update(row) else ImportedDao().insert(row)
            context.sendProfileChanged(uuid)
            true
        }.also { installed ->
            if (!installed) return@also
            state.update { current ->
                current.copy(
                    configUpdatedAt = current.configUpdatedAt + (uuid.toString() to payload.updatedAt),
                    fingerprint = current.fingerprint + (uuid.toString() to fingerprint(payload.files)),
                )
            }
            if (type != Profile.Type.File) {
                ImportedDao().queryByUUID(uuid)?.let { ProfileReceiver.scheduleNext(context, it) }
            }
        }
    }

    private suspend fun remove(uuid: UUID) {
        ImportedDao().queryByUUID(uuid)?.let { ProfileReceiver.cancelNext(context, it) }
        ProfileProcessor.delete(context, uuid)
        state.update { current ->
            current.copy(
                configUpdatedAt = current.configUpdatedAt - uuid.toString(),
                fingerprint = current.fingerprint - uuid.toString(),
            )
        }
    }

    private fun applyAccess(remote: AccessControlPayload, local: AccessControlPayload) {
        val fresh = state.read().accessControlUpdatedAt
        if (remote.updatedAt <= maxOf(local.updatedAt, fresh)) return
        val mode = AccessControlMode.values().find { it.name == remote.mode } ?: return
        val store = ServiceStore(context)
        store.accessControlMode = mode
        store.accessControlPackages = remote.packages.toSet()
        state.update { current ->
            if (current.accessControlUpdatedAt > remote.updatedAt) current
            else current.copy(accessControlUpdatedAt = remote.updatedAt)
        }
    }
}

data class ApplyCounts(
    val downloaded: Int,
    val removed: Int,
    val skipped: Int,
)

private fun sameSubscription(left: ProfilePayload, right: ProfilePayload): Boolean {
    return subscriptionIdentity(left.type, left.source, left.uuid) ==
        subscriptionIdentity(right.type, right.source, right.uuid)
}

private fun sameContent(left: ProfilePayload, right: ProfilePayload): Boolean {
    return left.uuid == right.uuid &&
        left.name == right.name &&
        left.type == right.type &&
        left.source == right.source &&
        left.interval == right.interval &&
        left.updatedAt == right.updatedAt &&
        left.ageSecretKey == right.ageSecretKey &&
        left.files == right.files
}

private fun readFiles(dir: File): List<ProfileFilePayload> {
    if (!dir.exists()) return emptyList()
    var total = 0
    return dir.walkTopDown().filter { it.isFile }.mapNotNull { file ->
        val rel = dir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
        if (!isSyncedProfileFile(rel)) return@mapNotNull null
        if (file.length() > WEBDAV_MAX_FILE_BYTES) throw WebdavException("too_large", detail = rel)
        total += file.length().toInt()
        if (total > WEBDAV_MAX_PROFILE_BYTES) throw WebdavException("too_large", detail = rel)
        ProfileFilePayload(rel, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
    }.sortedBy { it.path }.toList()
}

private fun fileUpdatedAt(context: Context, uuid: UUID, path: String): Long {
    return context.importedDir.resolve(uuid.toString()).resolve(path).lastModified()
}

internal fun fingerprint(files: List<ProfileFilePayload>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (file in files.sortedBy { it.path }) {
        digest.update(file.path.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(file.contentBase64.toByteArray(Charsets.UTF_8))
        digest.update(0)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
