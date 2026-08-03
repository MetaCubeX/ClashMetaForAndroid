package com.github.kr328.clash.service

import android.content.Context
import android.os.ParcelFileDescriptor
import com.github.kr328.clash.service.data.Database
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.remote.IProfileManager
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.directoryLastModified
import com.github.kr328.clash.service.util.generateProfileUUID
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.sendProfileChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.*

class ProfileManager(private val context: Context) : IProfileManager,
    CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val store = ServiceStore(context)
    private val configurationLock = Mutex()

    init {
        launch {
            Database.database //.init

            ProfileReceiver.rescheduleAll(context)
        }
    }

    override suspend fun create(type: Profile.Type, name: String, source: String, ageSecretKey: String?): UUID {
        val uuid = generateProfileUUID()
        val pending = Pending(
            uuid = uuid,
            name = name,
            type = type,
            source = source,
            interval = 0,
            upload = 0,
            total = 0,
            download = 0,
            expire = 0,
            ageSecretKey = ageSecretKey,
        )

        PendingDao().insert(pending)

        context.pendingDir.resolve(uuid.toString()).apply {
            deleteRecursively()
            mkdirs()

            @Suppress("BlockingMethodInNonBlockingContext")
            resolve("config.yaml").createNewFile()
            resolve("providers").mkdir()
        }

        return uuid
    }

    override suspend fun clone(uuid: UUID): UUID {
        val newUUID = generateProfileUUID()

        val imported = ImportedDao().queryByUUID(uuid)
            ?: throw FileNotFoundException("profile $uuid not found")

        val pending = Pending(
            uuid = newUUID,
            name = imported.name,
            type = Profile.Type.File,
            source = imported.source,
            interval = imported.interval,
            upload = imported.upload,
            total = imported.total,
            download = imported.download,
            expire = imported.expire,
            ageSecretKey = imported.ageSecretKey
        )

        cloneImportedFiles(uuid, newUUID)

        PendingDao().insert(pending)

        return newUUID
    }

    override suspend fun patch(uuid: UUID, name: String, source: String, interval: Long, ageSecretKey: String?) {
        val pending = PendingDao().queryByUUID(uuid)

        if (pending == null) {
            val imported = ImportedDao().queryByUUID(uuid)
                ?: throw FileNotFoundException("profile $uuid not found")

            cloneImportedFiles(uuid)

            PendingDao().insert(
                Pending(
                    uuid = imported.uuid,
                    name = name,
                    type = imported.type,
                    source = source,
                    interval = interval,
                    upload = 0,
                    total = 0,
                    download = 0,
                    expire = 0,
                    ageSecretKey = ageSecretKey,
                )
            )
        } else {
            val newPending = pending.copy(
                name = name,
                source = source,
                interval = interval,
                upload = 0,
                total = 0,
                download = 0,
                expire = 0,
                ageSecretKey = ageSecretKey,
            )

            PendingDao().update(newPending)
        }
    }

    override suspend fun update(uuid: UUID) {
        scheduleUpdate(uuid, true)
    }

    override suspend fun commit(uuid: UUID, callback: IFetchObserver?) {
        ProfileProcessor.apply(context, uuid, callback)

        scheduleUpdate(uuid, false)
    }

    override suspend fun release(uuid: UUID) {
        ProfileProcessor.release(context, uuid)
    }

    override suspend fun delete(uuid: UUID) {
        ImportedDao().queryByUUID(uuid)?.also {
            ProfileReceiver.cancelNext(context, it)
        }

        ProfileProcessor.delete(context, uuid)
    }

    override suspend fun queryByUUID(uuid: UUID): Profile? {
        return resolveProfile(uuid)
    }

    override suspend fun queryAll(): List<Profile> {
        val uuids = withContext(Dispatchers.IO) {
            (ImportedDao().queryAllUUIDs() + PendingDao().queryAllUUIDs()).distinct()
        }

        return uuids.mapNotNull { resolveProfile(it) }
    }

    override suspend fun queryActive(): Profile? {
        val active = store.activeProfile ?: return null

        return if (ImportedDao().exists(active)) {
            resolveProfile(active)
        } else {
            null
        }
    }

    override suspend fun setActive(profile: Profile) {
        ProfileProcessor.active(context, profile.uuid)
    }

    override suspend fun readConfiguration(uuid: UUID): String = withContext(Dispatchers.IO) {
        val directory = configurationDirectory(uuid)

        directory.resolve("config.yaml").readText()
    }

    override suspend fun copyConfiguration(uuid: UUID, destination: ParcelFileDescriptor) =
        withContext(Dispatchers.IO) {
            ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                val configuration = configurationDirectory(uuid).resolve("config.yaml")
                configuration.inputStream().use { input -> input.copyTo(output) }
            }
            Unit
        }

    override suspend fun replaceConfiguration(uuid: UUID, source: ParcelFileDescriptor, expectedSha256: String?) =
        withContext(Dispatchers.IO) {
            ParcelFileDescriptor.AutoCloseInputStream(source).use { input -> configurationLock.withLock {
                ensurePending(uuid)
                val configuration = context.pendingDir.resolve(uuid.toString()).resolve("config.yaml")
                if (expectedSha256 != null) {
                    val actual = MessageDigest.getInstance("SHA-256").digest(configuration.readBytes())
                        .joinToString("") { "%02x".format(it) }
                    require(actual.equals(expectedSha256, ignoreCase = true)) {
                        "configuration changed concurrently (expected $expectedSha256, actual $actual)"
                    }
                }
                val temporary = configuration.resolveSibling("config.yaml.agent.tmp")
                try {
                    temporary.outputStream().use { output -> input.copyTo(output) }
                    if (!temporary.renameTo(configuration)) {
                        temporary.copyTo(configuration, overwrite = true)
                        temporary.delete()
                    }
                } finally {
                    temporary.delete()
                }
            } }
            Unit
        }

    override suspend fun writeConfiguration(uuid: UUID, content: String) = withContext(Dispatchers.IO) {
        configurationLock.withLock {
            ensurePending(uuid)

            val configuration = context.pendingDir.resolve(uuid.toString()).resolve("config.yaml")
            val temporary = configuration.resolveSibling("config.yaml.agent.tmp")

            temporary.writeText(content)
            if (!temporary.renameTo(configuration)) {
                temporary.copyTo(configuration, overwrite = true)
                temporary.delete()
            }
        }
    }

    private suspend fun configurationDirectory(uuid: UUID) = when {
        PendingDao().queryByUUID(uuid) != null -> context.pendingDir.resolve(uuid.toString())
        ImportedDao().queryByUUID(uuid) != null -> context.importedDir.resolve(uuid.toString())
        else -> throw FileNotFoundException("profile $uuid not found")
    }

    private suspend fun ensurePending(uuid: UUID) {
        if (PendingDao().queryByUUID(uuid) != null) return
        val imported = ImportedDao().queryByUUID(uuid)
            ?: throw FileNotFoundException("profile $uuid not found")
        cloneImportedFiles(uuid)
        PendingDao().insert(
            Pending(
                uuid = imported.uuid,
                name = imported.name,
                type = imported.type,
                source = imported.source,
                interval = imported.interval,
                upload = imported.upload,
                total = imported.total,
                download = imported.download,
                expire = imported.expire,
                ageSecretKey = imported.ageSecretKey,
            )
        )
    }

    private suspend fun resolveProfile(uuid: UUID): Profile? {
        val imported = ImportedDao().queryByUUID(uuid)
        val pending = PendingDao().queryByUUID(uuid)

        val active = store.activeProfile
        val name = pending?.name ?: imported?.name ?: return null
        val type = pending?.type ?: imported?.type ?: return null
        val source = pending?.source ?: imported?.source ?: return null
        val interval = pending?.interval ?: imported?.interval ?: return null
        val upload = pending?.upload ?: imported?.upload ?: return null
        val download = pending?.download ?: imported?.download ?: return null
        val total = pending?.total ?: imported?.total ?: return null
        val expire = pending?.expire ?: imported?.expire ?: return null

        return Profile(
            uuid = uuid,
            name = name,
            type = type,
            source = source,
            active = active != null && imported?.uuid == active,
            interval = interval,
            upload = upload,
            download = download,
            total = total,
            expire = expire,
            updatedAt = resolveUpdatedAt(uuid),
            imported = imported != null,
            pending = pending != null,
            ageSecretKey = if (pending != null) pending.ageSecretKey else imported?.ageSecretKey,
        )
    }

    private fun resolveUpdatedAt(uuid: UUID): Long {
        return context.pendingDir.resolve(uuid.toString()).directoryLastModified
            ?: context.importedDir.resolve(uuid.toString()).directoryLastModified
            ?: -1
    }

    private fun cloneImportedFiles(source: UUID, target: UUID = source) {
        val s = context.importedDir.resolve(source.toString())
        val t = context.pendingDir.resolve(target.toString())

        if (!s.exists())
            throw FileNotFoundException("profile $source not found")

        t.deleteRecursively()

        s.copyRecursively(t)
    }

    private suspend fun scheduleUpdate(uuid: UUID, startImmediately: Boolean) {
        val imported = ImportedDao().queryByUUID(uuid) ?: return

        if (startImmediately) {
            ProfileReceiver.schedule(context, imported)
        } else {
            ProfileReceiver.scheduleNext(context, imported)
        }
    }
}
