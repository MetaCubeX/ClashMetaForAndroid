package com.github.kr328.clash.service

import android.content.Context
import android.net.Uri
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.SubscriptionUpdateMerge
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.processingDir
import com.github.kr328.clash.service.util.sendProfileChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.math.BigDecimal
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit

object ProfileProcessor {
    private val profileLock = Mutex()
    private val processLock = Mutex()

    suspend fun apply(context: Context, uuid: UUID, callback: IFetchObserver? = null) {
        withContext(Dispatchers.IO) {
            processLock.withLock {
                val snapshot = profileLock.withLock {
                    val pending = PendingDao().queryByUUID(uuid)
                        ?: throw IllegalArgumentException("profile $uuid not found")

                    pending.enforceFieldValid()

                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.pendingDir.resolve(pending.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    pending
                }

                val force = snapshot.type != Profile.Type.File
                var cb = callback

                Clash.fetchAndValid(context.processingDir, snapshot.source, force) {
                    try {
                        cb?.updateStatus(it)
                    } catch (e: Exception) {
                        cb = null

                        Log.w("Report fetch status: $e", e)
                    }
                }.await()

                withContext(NonCancellable) {
                    profileLock.withLock {
                        if (PendingDao().queryByUUID(snapshot.uuid) != snapshot) return@withLock
                        context.importedDir.resolve(snapshot.uuid.toString())
                            .deleteRecursively()
                        context.processingDir
                            .copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))

                        val old = ImportedDao().queryByUUID(snapshot.uuid)
                        var upload: Long = 0
                        var download: Long = 0
                        var total: Long = 0
                        var expire: Long = 0
                        if (snapshot?.type == Profile.Type.Url) {
                            if (snapshot.source.startsWith("https://", true)) {
                                val client = OkHttpClient()
                                val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                                val request = Request.Builder()
                                    .url(snapshot.source)
                                    .header("User-Agent", "ClashMetaForAndroid/$versionName")
                                    .build()

                                client.newCall(request).execute().use { response ->
                                    val userinfo = response.headers["subscription-userinfo"]
                                    if (response.isSuccessful && userinfo != null) {
                                        val flags = userinfo.split(";")
                                        for (flag in flags) {
                                            val info = flag.split("=", limit = 2)
                                            val key = info.getOrNull(0)?.trim().orEmpty()
                                            val value = info.getOrNull(1)?.trim().orEmpty()
                                            if (value.isEmpty()) continue
                                            when {
                                                key.contains("upload") -> upload =
                                                    value.toLongOrNull()
                                                        ?: BigDecimal(value.split('.').first()).longValueExact()

                                                key.contains("download") -> download =
                                                    value.toLongOrNull()
                                                        ?: BigDecimal(value.split('.').first()).longValueExact()

                                                key.contains("total") -> total =
                                                    value.toLongOrNull()
                                                        ?: BigDecimal(value.split('.').first()).longValueExact()

                                                key.contains("expire") -> expire =
                                                    (value.toDoubleOrNull()?.times(1000.0))?.toLong() ?: 0L
                                            }
                                        }
                                    }
                                }
                            }
                            val new = Imported(
                                snapshot.uuid,
                                snapshot.name,
                                snapshot.type,
                                snapshot.source,
                                snapshot.interval,
                                upload,
                                download,
                                total,
                                expire,
                                old?.createdAt ?: System.currentTimeMillis()
                            )
                            if (old != null) {
                                ImportedDao().update(new)
                            } else {
                                ImportedDao().insert(new)
                            }

                            PendingDao().remove(snapshot.uuid)

                            context.pendingDir.resolve(snapshot.uuid.toString())
                                .deleteRecursively()

                            context.sendProfileChanged(snapshot.uuid)
                        } else if (snapshot?.type == Profile.Type.File) {
                            val new = Imported(
                                snapshot.uuid,
                                snapshot.name,
                                snapshot.type,
                                snapshot.source,
                                snapshot.interval,
                                upload,
                                download,
                                total,
                                expire,
                                old?.createdAt ?: System.currentTimeMillis()
                            )
                            if (old != null) {
                                ImportedDao().update(new)
                            } else {
                                ImportedDao().insert(new)
                            }

                            PendingDao().remove(snapshot.uuid)

                            context.pendingDir.resolve(snapshot.uuid.toString())
                                .deleteRecursively()

                            context.sendProfileChanged(snapshot.uuid)
                        }
                    }
                }
            }
        }
    }

    suspend fun update(context: Context, uuid: UUID, callback: IFetchObserver?) {
        withContext(Dispatchers.IO) {
            processLock.withLock {
                val snapshot = profileLock.withLock {
                    val imported = ImportedDao().queryByUUID(uuid)
                        ?: throw IllegalArgumentException("profile $uuid not found")

                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.importedDir.resolve(imported.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    imported
                }

                val configFile = File(context.processingDir, "config.yaml")
                val preserved = if (configFile.isFile) {
                    SubscriptionUpdateMerge.extractPreserved(configFile.readText())
                } else {
                    SubscriptionUpdateMerge.PreservedOverlay.EMPTY
                }

                var cb = callback

                Clash.fetchAndValid(context.processingDir, snapshot.source, true) {
                    try {
                        cb?.updateStatus(it)
                    } catch (e: Exception) {
                        cb = null

                        Log.w("Report fetch status: $e", e)
                    }
                }.await()

                if (!preserved.isEmpty() && configFile.isFile) {
                    val merged = SubscriptionUpdateMerge.mergeAfterFetch(configFile.readText(), preserved)
                    configFile.writeText(merged)
                    Log.d("Subscription merge preserved local overlays: rules/rule-providers/proxy-providers reapplied for ${snapshot.uuid}")
                }

                withContext(NonCancellable) {
                    profileLock.withLock {
                        if (ImportedDao().exists(snapshot.uuid)) {
                            context.importedDir.resolve(snapshot.uuid.toString()).deleteRecursively()
                            context.processingDir
                                .copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))

                            context.sendProfileChanged(snapshot.uuid)
                        }
                    }
                }
            }
        }
    }

    suspend fun delete(context: Context, uuid: UUID) {
        withContext(NonCancellable) {
            profileLock.withLock {
                ImportedDao().remove(uuid)
                PendingDao().remove(uuid)

                val pending = context.pendingDir.resolve(uuid.toString())
                val imported = context.importedDir.resolve(uuid.toString())

                pending.deleteRecursively()
                imported.deleteRecursively()

                context.sendProfileChanged(uuid)
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
            name.isBlank() ->
                throw IllegalArgumentException("Empty name")

            source.isEmpty() && type != Profile.Type.File ->
                throw IllegalArgumentException("Invalid url")

            source.isNotEmpty() && scheme != "https" && scheme != "http" && scheme != "content" ->
                throw IllegalArgumentException("Unsupported url $source")

            interval != 0L && TimeUnit.MILLISECONDS.toMinutes(interval) < 15 ->
                throw IllegalArgumentException("Invalid interval")
        }
    }
}