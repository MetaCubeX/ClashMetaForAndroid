package com.github.kr328.clash.service.webdav

import android.content.Context
import android.util.Base64
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.ProfileProcessor
import com.github.kr328.clash.service.ProfileReceiver
import com.github.kr328.clash.service.commitProfileDirectory
import com.github.kr328.clash.service.recoverImportedDirectory
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppsStrategyConfig
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.model.legacyAccessControlNeedsMigration
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendProfileChanged
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID

data class LocalSnapshot(
    val profiles: List<ProfilePayload>,
    val access: AccessControlPayload,
    val tombstones: List<TombstonePayload>,
    val strategies: List<AppsStrategyPayload> = emptyList(),
    val strategyTombstones: List<TombstonePayload> = emptyList(),
    val activeAppsStrategy: String? = null,
    val activeAppsStrategyUpdatedAt: Long = 0,
    val activeAppsStrategySyncRev: Long = 0,
    val activeAppsStrategyWriterId: String = "",
    val profileUsage: List<ProfileUsagePayload> = emptyList(),
)

class WebdavProfiles(private val context: Context) {
    private val state = WebdavLocalState(context)
    private val applyDirectory = context.filesDir.resolve(
        "webdav/scopes/${webdavStateScope(WebdavSettingsStore(context).load())}/apply",
    )

    suspend fun exportLocal(includeUnmanaged: Boolean = false): LocalSnapshot {
        recoverProfileApplies()
        return ProfileProcessor.guardImported { exportCommittedLocal(includeUnmanaged) }
    }

    // ProfileProcessor commits use this same lock; row, files and remembered revision form one snapshot.
    private suspend fun exportCommittedLocal(includeUnmanaged: Boolean): LocalSnapshot {
        val local = state.read()
        val clientId = local.clientId.ifBlank { createWebdavClientId() }
        val usageByProfile = linkedMapOf<String, String>()
        val usageAtByProfile = linkedMapOf<String, Long>()
        val usageWriterByProfile = linkedMapOf<String, String>()
        val fingerprintByProfile = linkedMapOf<String, String>()
        val contentByProfile = linkedMapOf<String, String>()
        val updatedAtByProfile = linkedMapOf<String, Long>()
        val revByProfile = linkedMapOf<String, Long>()
        val writerByProfile = linkedMapOf<String, String>()
        val usagePayloads = mutableListOf<ProfileUsagePayload>()
        val exports = linkedMapOf<String, ProfilePayload>()
        val wireIds = linkedMapOf<String, String>()
        val identityTombstones = mutableListOf<TombstonePayload>()
        fun rememberedProfile(id: String): ProfilePayload? {
            val previous = local.previousExport[id] ?: return null
            usagePayloads.add(
                usageFromProfile(previous).copy(
                    updatedAt = local.profileUsageUpdatedAt[id] ?: previous.updatedAt,
                    writerId = local.profileUsageWriter[id] ?: previous.writerId,
                ),
            )
            return previous
        }
        val profiles = ImportedDao().queryAllUUIDs().mapNotNull { uuid ->
            val id = uuid.toString()
            if (!includeUnmanaged && id !in local.managedProfiles) return@mapNotNull null
            if (PendingDao().exists(uuid)) {
                // Keep the last committed candidate in merge while an editor owns the local ID.
                // The remote snapshot remains separately cached, so an unchanged ETag still retries apply.
                return@mapNotNull rememberedProfile(id)
            }
            val imported = ImportedDao().queryByUUID(uuid) ?: return@mapNotNull null
            recoverImportedDirectory(context.importedDir.resolve(id))
            val localFiles = readFiles(context.importedDir.resolve(id))
            val files = retainUnexecutedFiles(localFiles, local.previousExport[id])
            val fileFp = fingerprint(files)
            val storedFileFp = local.fingerprint[id]
            val storedContent = local.contentHash[id]
            val storedUpdatedAt = local.configUpdatedAt[id] ?: 0L
            val storedRev = local.profileSyncRev[id] ?: 0L
            val storedWriter = local.profileWriterId[id].orEmpty()
            val usage = usageStamp(imported.upload, imported.download, imported.total, imported.expire)
            val storedUsage = local.profileUsage[id]
            val draft = ProfilePayload(
                uuid = id,
                name = imported.name,
                type = imported.type.name,
                source = imported.source,
                interval = imported.interval,
                ageSecretKey = imported.ageSecretKey,
                files = files,
                preserved = local.previousExport[id]?.preserved,
            )
            if (!hasUsableConfig(draft)) return@mapNotNull rememberedProfile(id)
            val content = profileContentHash(draft)
            val changed = storedContent != null && storedContent != content
            val unchanged = storedUpdatedAt > 0 && (
                storedContent == content ||
                    (storedContent == null && storedFileFp != null && storedFileFp == fileFp)
                )
            val fileUpdatedAt = localFiles.maxOfOrNull { fileUpdatedAt(context, uuid, it.path) } ?: imported.createdAt
            val fileAt = fileUpdatedAt.coerceAtLeast(1)
            val updatedAt = if (unchanged) storedUpdatedAt else Math.addExact(maxOf(fileAt, storedUpdatedAt), 1)
            val rev = if (changed) Math.addExact(storedRev, 1) else storedRev
            val writer = if (changed) clientId else storedWriter
            val storedUsageAt = local.profileUsageUpdatedAt[id] ?: 0L
            val usageUnchanged = storedUsage == usage && storedUsageAt > 0
            val usageAt = when {
                usageUnchanged -> storedUsageAt
                storedUsage == null -> maxOf(updatedAt, 1L)
                else -> maxOf(System.currentTimeMillis(), Math.addExact(storedUsageAt, 1))
            }
            val usageWriter = when {
                usageUnchanged -> local.profileUsageWriter[id].orEmpty()
                storedUsage == null -> ""
                else -> clientId
            }
            if (storedUsage != usage) usageByProfile[id] = usage
            if (storedUsageAt != usageAt) usageAtByProfile[id] = usageAt
            if (local.profileUsageWriter[id].orEmpty() != usageWriter) usageWriterByProfile[id] = usageWriter
            if (storedFileFp != fileFp) fingerprintByProfile[id] = fileFp
            if (storedContent != content) contentByProfile[id] = content
            if (updatedAt > storedUpdatedAt) updatedAtByProfile[id] = updatedAt
            if (rev != storedRev) revByProfile[id] = rev
            if (writer != storedWriter) writerByProfile[id] = writer
            var exported = draft.copy(
                uuid = local.wireUuidByLocalId[id] ?: id,
                upload = imported.upload,
                download = imported.download,
                total = imported.total,
                expire = imported.expire,
                createdAt = imported.createdAt,
                updatedAt = updatedAt,
                syncRev = rev,
                writerId = writer,
            )
            local.previousExport[id]?.let { previous ->
                planProfileIdentityChange(previous, exported.copy(uuid = previous.uuid), UUID.randomUUID().toString(), clientId, System.currentTimeMillis())?.let { change ->
                    exported = change.profile
                    identityTombstones.add(change.tombstone)
                    revByProfile[id] = exported.syncRev
                    writerByProfile[id] = exported.writerId
                    updatedAtByProfile[id] = exported.updatedAt
                }
            }
            exports[id] = exported
            wireIds[id] = exported.uuid
            usagePayloads.add(
                ProfileUsagePayload(
                    uuid = exported.uuid,
                    upload = imported.upload,
                    download = imported.download,
                    total = imported.total,
                    expire = imported.expire,
                    updatedAt = usageAt,
                    writerId = usageWriter,
                ),
            )
            exported
        }
        if (
            usageByProfile.isNotEmpty() ||
            usageAtByProfile.isNotEmpty() ||
            usageWriterByProfile.isNotEmpty() ||
            fingerprintByProfile.isNotEmpty() ||
            contentByProfile.isNotEmpty() ||
            updatedAtByProfile.isNotEmpty() ||
            revByProfile.isNotEmpty() ||
            writerByProfile.isNotEmpty() ||
            local.clientId.isBlank() || exports.isNotEmpty()
        ) {
            state.update { current ->
                val nextUpdated = current.configUpdatedAt.toMutableMap()
                for ((id, stamp) in updatedAtByProfile) {
                    if (stamp > (nextUpdated[id] ?: 0L)) nextUpdated[id] = stamp
                }
                current.copy(
                    clientId = current.clientId.ifBlank { clientId },
                    configUpdatedAt = nextUpdated,
                    fingerprint = current.fingerprint + fingerprintByProfile,
                    contentHash = current.contentHash + contentByProfile,
                    profileSyncRev = current.profileSyncRev + revByProfile,
                    profileWriterId = current.profileWriterId + writerByProfile,
                    profileUsage = current.profileUsage + usageByProfile,
                    profileUsageUpdatedAt = current.profileUsageUpdatedAt + usageAtByProfile,
                    profileUsageWriter = current.profileUsageWriter + usageWriterByProfile,
                    managedProfiles = current.managedProfiles + exports.keys,
                    wireUuidByLocalId = current.wireUuidByLocalId + wireIds,
                    previousExport = current.previousExport + exports,
                    tombstones = mergeTombstones(current.tombstones + identityTombstones, System.currentTimeMillis()),
                )
            }
        }
        val store = ServiceStore(context)
        val legacyAccess = AccessControlPayload(
            updatedAt = local.accessControlUpdatedAt,
            syncRev = local.accessSyncRev,
            writerId = local.accessWriterId,
        )
        val strategies = exportStrategies(store, local, includeUnmanaged)
        val active = exportActiveStrategy(store, local, strategies.map { it.uuid }.toSet())
        return LocalSnapshot(
            profiles = profiles,
            access = legacyAccess,
            tombstones = state.read().tombstones,
            strategies = strategies,
            strategyTombstones = local.strategyTombstones,
            activeAppsStrategy = active.first,
            activeAppsStrategyUpdatedAt = active.second,
            activeAppsStrategySyncRev = if (active.second > 0) local.activeStrategySyncRev else 0,
            activeAppsStrategyWriterId = if (active.second > 0) local.activeStrategyWriterId else "",
            profileUsage = usagePayloads.sortedBy { it.uuid },
        )
    }

    suspend fun apply(
        result: MergeResult,
        local: LocalSnapshot,
        startedAt: Long,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): ApplyCounts {
        var downloaded = 0
        var removed = 0
        var skipped = 0
        val deferred = mutableSetOf<String>()
        val baseline = state.read()
        val rows = ImportedDao().queryAllUUIDs().mapNotNull { ImportedDao().queryByUUID(it) }
        val present = rows.associateBy { it.uuid.toString() }
        val localByUuid = local.profiles.associateBy { it.uuid }
        val total = result.profiles.size

        for ((index, profile) in result.profiles.withIndex()) {
            onProgress(index + 1, total, profile.name)
            val localId = resolveLocalUuid(profile, baseline, present)
            if (localId == null || Profile.Type.values().none { it.name == profile.type } || !hasUsableConfig(profile)) {
                skipped += 1
                deferred.add(profile.uuid)
                continue
            }
            val previous = localByUuid[profile.uuid]
                ?: local.profiles.find { sameSubscription(it, profile) }
            val usage = result.profileUsage.find { it.uuid == profile.uuid }
            val config = context.importedDir.resolve(localId.toString()).resolve("config.yaml")
            val sameContent = previous != null && config.isFile && config.length() > 0 &&
                profileContentHash(previous) == profileContentHash(profile)
            val installed = if (sameContent) patchUsage(localId, profile, usage, previous)
                else install(ProfileApplyRecord(localId.toString(), profile, usage, previous))
            if (!installed) {
                skipped += 1
                deferred.add(profile.uuid)
            } else if (!sameContent) {
                downloaded += 1
            }
        }

        for ((id, previous) in baseline.previousExport) {
            if (id !in baseline.managedProfiles || id !in present) continue
            if (result.profiles.any { sameSubscription(it, previous) }) continue
            // Absence from a snapshot is not a user deletion. Only a winning tombstone removes a local asset.
            val tombstone = result.tombstones.filter { matchesTombstone(it, previous) }
                .maxWithOrNull(compareBy({ it.rev }, { it.deletedAt })) ?: continue
            if (!tombstoneBeats(tombstone, previous.updatedAt, previous.syncRev)) continue
            val exported = local.profiles.find { sameSubscription(it, previous) } ?: previous
            if (!remove(UUID.fromString(id), exported)) {
                skipped += 1
                deferred.add(previous.uuid)
                deferred.addAll(result.tombstones.filter { matchesTombstone(it, previous) }.map { it.uuid })
                continue
            }
            removed += 1
        }

        applyLegacyAccess(result.access)
        val strategyCounts = applyStrategies(result, local, startedAt)
        return ApplyCounts(
            downloaded = downloaded + strategyCounts.first,
            removed = removed + strategyCounts.second,
            skipped = skipped,
            deferredProfiles = deferred,
        )
    }

    private fun resolveLocalUuid(payload: ProfilePayload, baseline: LocalState, present: Map<String, Imported>): UUID? {
        val candidates = baseline.managedProfiles.filter { id ->
            baseline.wireUuidByLocalId[id] == payload.uuid ||
                baseline.previousExport[id]?.let { sameSubscription(it, payload) } == true
        }
        if (candidates.size > 1) return null
        candidates.singleOrNull()?.let { return runCatching { UUID.fromString(it) }.getOrNull() }
        val conflict = present.any { (id, row) ->
            id == payload.uuid || subscriptionIdentity(row.type.name, row.source, id) == profileIdentity(payload)
        }
        if (conflict) return null
        return runCatching { UUID.fromString(payload.uuid) }.getOrNull()
    }

    private suspend fun patchUsage(uuid: UUID, payload: ProfilePayload, usage: ProfileUsagePayload?, previous: ProfilePayload?): Boolean {
        return ProfileProcessor.guardImported {
            state.ensureScopeCurrent()
            if (PendingDao().exists(uuid)) return@guardImported false
            val row = ImportedDao().queryByUUID(uuid) ?: return@guardImported false
            if (!matchesSnapshot(uuid, row, previous)) return@guardImported false
            if (row.upload != payload.upload || row.download != payload.download || row.total != payload.total || row.expire != payload.expire) {
                ImportedDao().update(row.copy(upload = payload.upload, download = payload.download, total = payload.total, expire = payload.expire))
            }
            recordAppliedProfile(uuid, payload, usage)
            context.sendProfileChanged(uuid)
            true
        }
    }

    private suspend fun install(record: ProfileApplyRecord, recovering: Boolean = false): Boolean {
        if (record.version != 1) throw WebdavException("bad_apply_state")
        val payload = record.payload
        val uuid = runCatching { UUID.fromString(record.localUuid) }.getOrNull() ?: return false
        val type = Profile.Type.values().find { it.name == payload.type } ?: return false
        if (!hasUsableConfig(payload)) return false
        return ProfileProcessor.guardImported {
            state.ensureScopeCurrent()
            if (PendingDao().exists(uuid)) return@guardImported false
            val current = ImportedDao().queryByUUID(uuid)
            if (recovering) {
                if (!matchesRecovery(uuid, current, record)) return@guardImported false
            } else if (!matchesSnapshot(uuid, current, record.previous)) return@guardImported false
            val dir = context.importedDir.resolve(uuid.toString())
            val staging = context.importedDir.resolve("$uuid.webdav-tmp")
            if (staging.exists() && !staging.deleteRecursively()) throw java.io.IOException("Cannot clear WebDAV staging")
            if (!staging.mkdirs()) throw java.io.IOException("Cannot create WebDAV staging")
            var total = 0L
            val paths = mutableSetOf<String>()
            for (file in payload.files) {
                if (!isSyncedProfileFile(file.path)) continue
                val rel = safeRelativePath(file.path)
                if (rel != file.path || rel.startsWith('/') || rel.contains('\\') || Regex("^[A-Za-z]:").containsMatchIn(rel)) {
                    throw WebdavException("bad_path")
                }
                if (!paths.add(rel)) throw WebdavException("bad_path")
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
            if (staging.resolve("config.yaml").length() == 0L) {
                staging.deleteRecursively()
                return@guardImported false
            }
            writeApplyRecord(record)
            state.ensureScopeCurrent()
            commitProfileDirectory(staging, dir)
            val createdAt = payload.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            val row = Imported(
                uuid = uuid,
                name = payload.name,
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
            recordAppliedProfile(uuid, payload, record.usage)
            staging.deleteRecursively()
            applyRecordFile(uuid).delete()
            context.sendProfileChanged(uuid)
            true
        }.also { installed ->
            if (!installed) return@also
            if (type != Profile.Type.File) {
                ImportedDao().queryByUUID(uuid)?.let { ProfileReceiver.scheduleNext(context, it) }
            }
        }
    }

    private fun recordAppliedProfile(uuid: UUID, payload: ProfilePayload, usage: ProfileUsagePayload?) {
        val id = uuid.toString()
        state.update { current ->
            current.copy(
                configUpdatedAt = current.configUpdatedAt + (id to payload.updatedAt),
                fingerprint = current.fingerprint + (id to fingerprint(payload.files)),
                contentHash = current.contentHash + (id to profileContentHash(payload)),
                profileSyncRev = current.profileSyncRev + (id to payload.syncRev),
                profileWriterId = current.profileWriterId + (id to payload.writerId),
                profileUsage = current.profileUsage + (id to usageStamp(payload.upload, payload.download, payload.total, payload.expire)),
                profileUsageUpdatedAt = current.profileUsageUpdatedAt + (id to (usage?.updatedAt ?: payload.updatedAt)),
                profileUsageWriter = current.profileUsageWriter + (id to (usage?.writerId ?: payload.writerId)),
                managedProfiles = current.managedProfiles + id,
                wireUuidByLocalId = current.wireUuidByLocalId + (id to payload.uuid),
                previousExport = current.previousExport + (id to payload),
            )
        }
    }

    private fun importedPayload(uuid: UUID, row: Imported, fresh: LocalState): ProfilePayload = ProfilePayload(
        uuid = fresh.wireUuidByLocalId[uuid.toString()] ?: uuid.toString(),
        name = row.name, type = row.type.name, source = row.source, interval = row.interval,
        upload = row.upload, download = row.download, total = row.total, expire = row.expire,
        ageSecretKey = row.ageSecretKey,
        files = retainUnexecutedFiles(readFiles(context.importedDir.resolve(uuid.toString())), fresh.previousExport[uuid.toString()]),
        syncRev = fresh.profileSyncRev[uuid.toString()] ?: 0L,
        updatedAt = fresh.configUpdatedAt[uuid.toString()] ?: 0L,
        writerId = fresh.profileWriterId[uuid.toString()].orEmpty(),
    )

    private fun matchesSnapshot(uuid: UUID, row: Imported?, previous: ProfilePayload?): Boolean {
        if (previous == null) return row == null
        if (row == null) return false
        val actual = importedPayload(uuid, row, state.read())
        if (!sameProfileUsage(actual, previous)) return false
        if (profileBodyFingerprint(actual) == profileBodyFingerprint(previous)) return true
        // Missing assets can be repaired from the remembered package, but never overwrite
        // changed metadata or a surviving file edited since the download snapshot.
        if (context.importedDir.resolve(uuid.toString()).resolve("config.yaml").exists()) return false
        return profileBodyFingerprint(actual.copy(files = previous.files)) == profileBodyFingerprint(previous) &&
            actual.files.all { file -> previous.files.any { it == file } }
    }

    private fun matchesRecovery(uuid: UUID, row: Imported?, record: ProfileApplyRecord): Boolean {
        val fresh = state.read()
        if (fresh.tombstones.any { matchesTombstone(it, record.payload) && tombstoneBeats(it, record.payload.updatedAt, record.payload.syncRev) }) return false
        if (row == null) return record.previous == null
        val actual = importedPayload(uuid, row, fresh)
        val candidates = listOfNotNull(record.previous, record.payload)
        // An interrupted directory/DB/state sequence can contain either side of each step.
        // A later local edit with different content remains untouched and will merge normally.
        return candidates.any { fingerprint(it.files.filter { file -> isSyncedProfileFile(file.path) }) == fingerprint(actual.files.filter { file -> isSyncedProfileFile(file.path) }) } &&
            candidates.any { profileContentHash(it.copy(files = emptyList())) == profileContentHash(actual.copy(files = emptyList())) } &&
            candidates.any { sameProfileUsage(it, actual) } &&
            (candidates.any { it.syncRev == actual.syncRev && it.updatedAt == actual.updatedAt && it.writerId == actual.writerId } ||
                (record.previous == null && uuid.toString() !in fresh.profileSyncRev && uuid.toString() !in fresh.configUpdatedAt))
    }

    private fun applyRecordFile(uuid: UUID): File = applyDirectory.resolve("$uuid.json")

    private fun writeApplyRecord(record: ProfileApplyRecord) {
        if (!applyDirectory.isDirectory && !applyDirectory.mkdirs()) throw java.io.IOException("Cannot create WebDAV apply journal")
        val destination = applyRecordFile(UUID.fromString(record.localUuid))
        val temporary = File(applyDirectory, "${destination.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(webdavJson.encodeToString(ProfileApplyRecord.serializer(), record).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (!temporary.renameTo(destination)) throw java.io.IOException("Cannot commit WebDAV apply journal")
    }

    private suspend fun recoverProfileApplies() {
        for (file in applyDirectory.listFiles().orEmpty().filter { it.name.endsWith(".json") }.sortedBy { it.name }) {
            val record = try { webdavJson.decodeFromString(ProfileApplyRecord.serializer(), file.readText()) }
            catch (error: Exception) { throw IllegalStateException("Cannot read WebDAV apply journal; the file has been preserved", error) }
            install(record, recovering = true)
        }
    }

    private suspend fun remove(uuid: UUID, previous: ProfilePayload): Boolean {
        val row = ImportedDao().queryByUUID(uuid)
        val removed = ProfileProcessor.deleteIf(context, uuid, shouldDelete = {
            state.ensureScopeCurrent()
            !PendingDao().exists(uuid) && matchesSnapshot(uuid, ImportedDao().queryByUUID(uuid), previous)
        })
        if (!removed) return false
        row?.let { ProfileReceiver.cancelNext(context, it) }
        val id = uuid.toString()
        state.update { current ->
            current.copy(
                configUpdatedAt = current.configUpdatedAt - id,
                fingerprint = current.fingerprint - id,
                contentHash = current.contentHash - id,
                profileSyncRev = current.profileSyncRev - id,
                profileWriterId = current.profileWriterId - id,
                profileUsage = current.profileUsage - id,
                profileUsageUpdatedAt = current.profileUsageUpdatedAt - id,
                profileUsageWriter = current.profileUsageWriter - id,
                managedProfiles = current.managedProfiles - id,
                wireUuidByLocalId = current.wireUuidByLocalId - id,
                previousExport = current.previousExport - id,
            )
        }
        applyRecordFile(uuid).delete()
        return true
    }

    private suspend fun applyLegacyAccess(remote: AccessControlPayload) {
        val store = ServiceStore(context)
        if (store.legacyAccessControlMigrated) return
        if (remote.mode == AccessControlMode.AcceptAll.name && remote.packages.isEmpty()) {
            store.legacyAccessControlMigrated = true
            return
        }
        val mode = AccessControlMode.values().find { it.name == remote.mode } ?: return
        if (!legacyAccessControlNeedsMigration(mode, remote.packages)) return
        val active = store.activeAppsStrategyConfigUuid
            ?.let { id -> store.appsStrategyConfigs.find { it.uuid == id } }
        if (active != null) {
            store.legacyAccessControlMigrated = true
            return
        }
        val existing = store.appsStrategyConfigs.find {
            it.mode.name == remote.mode && it.packages.sorted() == remote.packages.sorted()
        }
        if (existing != null) {
            store.activeAppsStrategyConfigUuid = existing.uuid
            store.legacyAccessControlMigrated = true
            return
        }
        val migrated = AppsStrategyConfig.create(
            name = context.getString(R.string.legacy_access_control_name),
            mode = mode,
            packages = remote.packages,
        )
        store.appsStrategyConfigs = store.appsStrategyConfigs + migrated
        store.activeAppsStrategyConfigUuid = migrated.uuid
        store.legacyAccessControlMigrated = true
    }

    private fun exportStrategies(store: ServiceStore, local: LocalState, includeUnmanaged: Boolean): List<AppsStrategyPayload> {
        val configs = try {
            store.appsStrategyConfigs
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
        return configs.filter { includeUnmanaged || it.uuid.toString() in local.strategyUpdatedAt }.map { config ->
            val id = config.uuid.toString()
            var stamp = local.strategyUpdatedAt[id] ?: 0L
            if (stamp == 0L) {
                val stamped = System.currentTimeMillis()
                state.update { current ->
                    if ((current.strategyUpdatedAt[id] ?: 0L) > 0) current
                    else current.copy(strategyUpdatedAt = current.strategyUpdatedAt + (id to stamped))
                }
                stamp = state.read().strategyUpdatedAt[id] ?: stamped
            }
            AppsStrategyPayload(
                uuid = id,
                name = config.name,
                mode = config.mode.name,
                packages = config.packages.sorted(),
                updatedAt = stamp,
                syncRev = local.strategySyncRev[id] ?: 0L,
                writerId = local.strategyWriterId[id].orEmpty(),
            )
        }
    }

    private fun exportActiveStrategy(store: ServiceStore, local: LocalState, managedStrategies: Set<String>): Pair<String?, Long> {
        val active = store.activeAppsStrategyConfigUuid?.toString()
        if (active != null && active !in managedStrategies) return null to 0L
        var stamp = local.activeStrategyUpdatedAt
        if (stamp == 0L && active != null) {
            val stamped = System.currentTimeMillis()
            state.update { current ->
                if (current.activeStrategyUpdatedAt > 0) current
                else current.copy(activeStrategyUpdatedAt = stamped)
            }
            stamp = state.read().activeStrategyUpdatedAt
        }
        return active to stamp
    }

    private fun applyStrategies(result: MergeResult, local: LocalSnapshot, startedAt: Long): Pair<Int, Int> {
        val store = ServiceStore(context)
        val fresh = state.read()
        val current = try {
            store.appsStrategyConfigs.associateBy { it.uuid.toString() }.toMutableMap()
        } catch (_: Exception) {
            return 0 to 0
        }
        var downloaded = 0
        var removed = 0
        val installed = linkedMapOf<String, AppsStrategyPayload>()
        val deleted = mutableSetOf<String>()
        val keep = result.strategies.map { it.uuid }.toSet()
        for (remote in result.strategies) {
            val previous = local.strategies.find { it.uuid == remote.uuid }
            val freshRev = fresh.strategySyncRev[remote.uuid] ?: 0L
            val freshAt = fresh.strategyUpdatedAt[remote.uuid] ?: 0L
            val freshWriter = fresh.strategyWriterId[remote.uuid].orEmpty()
            val localRank = if (previous == null) {
                SyncRank(freshRev, freshAt, freshWriter, "")
            } else {
                SyncRank(
                    maxOf(previous.syncRev, freshRev),
                    maxOf(previous.updatedAt, freshAt),
                    if (freshRev > previous.syncRev || (freshRev == previous.syncRev && freshAt > previous.updatedAt)) {
                        freshWriter
                    } else {
                        previous.writerId
                    },
                    strategyContentHash(previous),
                )
            }
            if (syncRankCompare(strategyRank(remote), localRank) <= 0 && (previous != null || current.containsKey(remote.uuid))) {
                continue
            }
            if (previous == null && remote.uuid in current && remote.uuid !in fresh.strategyUpdatedAt) continue
            val mode = AccessControlMode.values().find { it.name == remote.mode } ?: continue
            val uuid = runCatching { UUID.fromString(remote.uuid) }.getOrNull() ?: continue
            current[remote.uuid] = AppsStrategyConfig(
                uuid = uuid,
                name = remote.name.ifBlank { remote.uuid },
                mode = mode,
                packages = remote.packages,
            )
            installed[remote.uuid] = remote
            if (previous == null || syncRankCompare(strategyRank(remote), strategyRank(previous)) > 0) downloaded += 1
        }
        for (id in current.keys.toList()) {
            if (id in keep) continue
            val previous = local.strategies.find { it.uuid == id } ?: continue
            val tombstone = result.strategyTombstones.filter { it.uuid == id }
                .maxWithOrNull(compareBy({ it.rev }, { it.deletedAt })) ?: continue
            if (!tombstoneBeats(tombstone, previous.updatedAt, previous.syncRev)) continue
            val stamp = fresh.strategyUpdatedAt[id] ?: 0L
            val rev = fresh.strategySyncRev[id] ?: 0L
            if (stamp > startedAt || rev > previous.syncRev) continue
            current.remove(id)
            removed += 1
            deleted.add(id)
        }
        if (installed.isNotEmpty() || deleted.isNotEmpty()) {
            // A failed content write must not acknowledge a version that was never applied.
            // Activity/Shell writers still need a shared durable strategy transaction before P3/P4.
            store.appsStrategyConfigs = current.values.toList()
            state.update { item ->
                item.copy(
                    strategyUpdatedAt = (item.strategyUpdatedAt - deleted) + installed.mapValues { it.value.updatedAt },
                    strategySyncRev = (item.strategySyncRev - deleted) + installed.mapValues { it.value.syncRev },
                    strategyWriterId = (item.strategyWriterId - deleted) + installed.mapValues { it.value.writerId },
                )
            }
        }
        applyActiveStrategy(store, result)
        return downloaded to removed
    }

    private fun applyActiveStrategy(store: ServiceStore, result: MergeResult) {
        val fresh = state.read()
        val currentId = store.activeAppsStrategyConfigUuid?.toString().orEmpty()
        val remoteRank = SyncRank(
            result.activeAppsStrategySyncRev,
            result.activeAppsStrategyUpdatedAt,
            result.activeAppsStrategyWriterId,
            result.activeAppsStrategy.orEmpty(),
        )
        val localRank = SyncRank(
            fresh.activeStrategySyncRev,
            fresh.activeStrategyUpdatedAt,
            fresh.activeStrategyWriterId,
            currentId,
        )
        if (syncRankCompare(remoteRank, localRank) <= 0) return
        if (result.activeAppsStrategy != null && store.appsStrategyConfigs.none { it.uuid.toString() == result.activeAppsStrategy }) return
        store.activeAppsStrategyConfigUuid = result.activeAppsStrategy?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        state.update { current ->
            if (current.activeStrategySyncRev > result.activeAppsStrategySyncRev) current
            else current.copy(
                activeStrategyUpdatedAt = result.activeAppsStrategyUpdatedAt,
                activeStrategySyncRev = result.activeAppsStrategySyncRev,
                activeStrategyWriterId = result.activeAppsStrategyWriterId,
            )
        }
    }
}

data class ApplyCounts(
    val downloaded: Int,
    val removed: Int,
    val skipped: Int,
    val deferredProfiles: Set<String> = emptySet(),
)

@Serializable
private data class ProfileApplyRecord(
    val localUuid: String,
    val payload: ProfilePayload,
    val usage: ProfileUsagePayload? = null,
    val previous: ProfilePayload? = null,
    val version: Int = 1,
)

private fun retainUnexecutedFiles(files: List<ProfileFilePayload>, previous: ProfilePayload?): List<ProfileFilePayload> {
    val previousByPath = previous?.files.orEmpty().associateBy { it.path }
    val retained = files.map { file ->
        val old = previousByPath[file.path]
        if (old != null && runCatching {
                Base64.decode(old.contentBase64, Base64.DEFAULT).contentEquals(Base64.decode(file.contentBase64, Base64.DEFAULT))
            }.getOrDefault(false)
        ) old else file
    }
    return (retained + previous?.files.orEmpty().filter { !isSyncedProfileFile(it.path) }).sortedBy { it.path }
}

private fun hasUsableConfig(profile: ProfilePayload): Boolean {
    val config = profile.files.singleOrNull { it.path == "config.yaml" } ?: return false
    return runCatching { Base64.decode(config.contentBase64, Base64.DEFAULT).toString(Charsets.UTF_8).isNotBlank() }.getOrDefault(false)
}

private fun sameSubscription(left: ProfilePayload, right: ProfilePayload): Boolean {
    return subscriptionIdentity(left.type, left.source, left.uuid) ==
        subscriptionIdentity(right.type, right.source, right.uuid)
}

private fun usageStamp(upload: Long, download: Long, total: Long, expire: Long): String {
    return "$upload,$download,$total,$expire"
}

internal fun readFiles(dir: File): List<ProfileFilePayload> {
    if (!dir.exists()) return emptyList()
    if (Files.isSymbolicLink(dir.toPath())) throw WebdavException("bad_path")
    val rootPath = dir.canonicalFile.path + File.separator
    fun checkPath(file: File) {
        if (Files.isSymbolicLink(file.toPath()) || (file != dir && !file.canonicalFile.path.startsWith(rootPath))) {
            throw WebdavException("bad_path")
        }
    }
    var total = 0
    return dir.walkTopDown().onEnter { checkPath(it); true }.filter { it.isFile }.mapNotNull { file ->
        checkPath(file)
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
