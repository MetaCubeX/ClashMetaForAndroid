package com.github.kr328.clash.service

import android.content.Context
import android.net.Uri
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.webdav.WebdavAutoSync
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.processingDir
import com.github.kr328.clash.service.util.sendProfileChanged
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

object ProfileProcessor {
    private val profileLock = Mutex()
    private val processLock = Mutex()

    suspend fun apply(context: Context, uuid: UUID, callback: IFetchObserver? = null) {
        withContext(NonCancellable) {
            processLock.withLock {
                val snapshot = profileLock.withLock {
                    val pending =
                        PendingDao().queryByUUID(uuid) ?: throw IllegalArgumentException("profile $uuid not found")

                    pending.enforceFieldValid()

                    recoverImportedDirectory(context.importedDir.resolve(pending.uuid.toString()))
                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.pendingDir.resolve(pending.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    pending
                }

                Clash.setAgeSecretKey(snapshot.ageSecretKey?.takeIf { it.isNotBlank() })

                val force = snapshot.type != Profile.Type.File
                val subscriptionInfo = fetchProfile(context, snapshot.source, force, callback)

                profileLock.withLock {
                    val imported = context.importedDir.resolve(snapshot.uuid.toString())
                    recoverImportedDirectory(imported)
                    if (PendingDao().queryByUUID(snapshot.uuid) == snapshot) {
                        commitProfileDirectory(context.processingDir, imported)

                        val old = ImportedDao().queryByUUID(snapshot.uuid)
                        val updateInterval = subscriptionInfo?.subUpdateInterval
                            ?.takeIf { old == null && snapshot.interval == 0L }
                            ?: snapshot.interval
                        val info = subscriptionInfo
                        val headerUpload = info?.subUpload
                        val upload: Long
                        val download: Long
                        val total: Long
                        val expire: Long
                        if (headerUpload != null) {
                            upload = headerUpload
                            download = info?.subDownload ?: 0
                            total = info?.subTotal ?: 0
                            expire = info?.subExpire ?: 0
                        } else if (old != null) {
                            upload = old.upload
                            download = old.download
                            total = old.total
                            expire = old.expire
                        } else {
                            upload = snapshot.upload
                            download = snapshot.download
                            total = snapshot.total
                            expire = snapshot.expire
                        }
                        val new = Imported(
                            snapshot.uuid,
                            snapshot.name,
                            snapshot.type,
                            snapshot.source,
                            updateInterval,
                            upload,
                            download,
                            total,
                            expire,
                            old?.createdAt ?: System.currentTimeMillis(),
                            ageSecretKey = snapshot.ageSecretKey
                        )
                        if (old != null) {
                            ImportedDao().update(new)
                        } else {
                            ImportedDao().insert(new)
                        }

                        PendingDao().remove(snapshot.uuid)

                        context.pendingDir.resolve(snapshot.uuid.toString()).deleteRecursively()

                        WebdavAutoSync.noteProfile(context, snapshot.uuid)
                        context.sendProfileChanged(snapshot.uuid)
                    }
                }
            }
        }
    }

    suspend fun update(context: Context, uuid: UUID, callback: IFetchObserver?) {
        withContext(NonCancellable) {
            processLock.withLock {
                val snapshot = profileLock.withLock {
                    val imported =
                        ImportedDao().queryByUUID(uuid) ?: throw IllegalArgumentException("profile $uuid not found")

                    recoverImportedDirectory(context.importedDir.resolve(imported.uuid.toString()))
                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.importedDir.resolve(imported.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    imported
                }

                Clash.setAgeSecretKey(snapshot.ageSecretKey?.takeIf { it.isNotBlank() })

                val subscriptionInfo = fetchProfile(context, snapshot.source, true, callback)

                profileLock.withLock {
                    val imported = ImportedDao().queryByUUID(snapshot.uuid)
                    if (imported != null) {
                        commitProfileDirectory(
                            context.processingDir,
                            context.importedDir.resolve(snapshot.uuid.toString()),
                        )

                        val upload = subscriptionInfo?.subUpload
                        if (upload != null) {
                            ImportedDao().update(
                                imported.copy(
                                    upload = upload,
                                    download = subscriptionInfo.subDownload ?: 0,
                                    total = subscriptionInfo.subTotal ?: 0,
                                    expire = subscriptionInfo.subExpire ?: 0,
                                )
                            )
                        }

                        if (WebdavAutoSync.noteProfile(context, snapshot.uuid, onlyManaged = true)) {
                            WebdavAutoSync.enqueue(context)
                        }
                        context.sendProfileChanged(snapshot.uuid)
                    }
                }
            }
        }
    }

    suspend fun <T> guardImported(block: suspend () -> T): T {
        return withContext(NonCancellable) {
            processLock.withLock {
                profileLock.withLock {
                    block()
                }
            }
        }
    }

    private suspend fun fetchProfile(
        context: Context,
        source: String,
        force: Boolean,
        callback: IFetchObserver?,
    ): FetchStatus? {
        var subscriptionInfo: FetchStatus? = null
        var cb = callback

        Clash.fetchAndValid(context.processingDir, source, force) {
            if (it.action == FetchStatus.Action.SubscriptionInfo) {
                subscriptionInfo = it
                return@fetchAndValid
            }

            try {
                cb?.updateStatus(it)
            } catch (e: Exception) {
                cb = null

                Log.w("Report fetch status: $e", e)
            }
        }.await()

        return subscriptionInfo
    }

    suspend fun delete(context: Context, uuid: UUID) {
        deleteIf(context, uuid)
    }

    /** Runs the final deletion check under the same lock as profile commits. */
    suspend fun deleteIf(
        context: Context,
        uuid: UUID,
        shouldDelete: suspend () -> Boolean = { true },
        afterDelete: suspend () -> Unit = {},
    ): Boolean {
        return withContext(NonCancellable) {
            processLock.withLock {
                profileLock.withLock check@ {
                    if (!shouldDelete()) return@check false
                    ImportedDao().remove(uuid)
                    PendingDao().remove(uuid)

                    val pending = context.pendingDir.resolve(uuid.toString())
                    val imported = context.importedDir.resolve(uuid.toString())

                    if (!pending.deleteRecursively()) throw java.io.IOException("Cannot delete pending profile")
                    if (!imported.deleteRecursively()) throw java.io.IOException("Cannot delete imported profile")

                    val store = ServiceStore(context)
                    if (store.activeProfile == uuid) {
                        store.activeProfile = null
                    }

                    afterDelete()
                    context.sendProfileChanged(uuid)
                    true
                }
            }
        }
    }

    suspend fun release(context: Context, uuid: UUID): Boolean {
        return withContext(NonCancellable) {
            profileLock.withLock {
                PendingDao().remove(uuid)

                context.pendingDir.resolve(uuid.toString()).deleteRecursively()
            }
        }
    }

    suspend fun active(context: Context, uuid: UUID) {
        withContext(NonCancellable) {
            profileLock.withLock {
                if (ImportedDao().exists(uuid)) {
                    val store = ServiceStore(context)

                    store.activeProfile = uuid

                    context.sendProfileChanged(uuid)
                }
            }
        }
    }

    private fun Pending.enforceFieldValid() {
        val scheme = Uri.parse(source)?.scheme?.lowercase(Locale.getDefault())

        when {
            name.isBlank() -> throw IllegalArgumentException("Empty name")

            source.isEmpty() && type != Profile.Type.File -> throw IllegalArgumentException("Invalid url")

            source.isNotEmpty() && scheme != "https" && scheme != "http" && scheme != "content" -> throw IllegalArgumentException(
                "Unsupported url $source"
            )

            interval != 0L && TimeUnit.MILLISECONDS.toMinutes(interval) < 15 -> throw IllegalArgumentException("Invalid interval")
        }
    }

}
