package com.github.kr328.clash.service.webdav

import android.content.Context
import com.github.kr328.clash.service.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WebdavSync(
    private val context: Context,
    private val client: WebdavClient = WebdavClient(),
) {
    suspend fun perform(action: String, settings: StoredWebdavSettings): String {
        return gate.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val message = execute(action, settings)
                    WebdavLocalState(context).update {
                        it.copy(lastSyncAt = System.currentTimeMillis(), lastMessage = message)
                    }
                    message
                } catch (err: WebdavException) {
                    val message = err.localize(context)
                    WebdavLocalState(context).update { it.copy(lastMessage = message) }
                    throw IllegalStateException(message)
                } catch (err: IllegalStateException) {
                    throw err
                } catch (err: Exception) {
                    val message = err.message?.ifBlank { null } ?: context.getString(R.string.webdav_error_generic, err.javaClass.simpleName)
                    WebdavLocalState(context).update { it.copy(lastMessage = message) }
                    throw IllegalStateException(message)
                }
            }
        }
    }

    private suspend fun execute(action: String, settings: StoredWebdavSettings): String {
        val config = config(settings)
        if (action == ACTION_TEST) {
            client.ensureParents(config)
            client.list(config)
            return context.getString(R.string.webdav_connected)
        }
        var lastConflict: WebdavConflictException? = null
        repeat(3) { attempt ->
            try {
                return runOnce(config, action)
            } catch (err: WebdavConflictException) {
                lastConflict = err
                if (attempt == 2) throw err
                Thread.sleep(50L * (attempt + 1))
            }
        }
        throw lastConflict ?: WebdavConflictException()
    }

    private suspend fun runOnce(config: WebdavConfig, action: String): String {
        val remote = download(config)
        val profiles = WebdavProfiles(context)
        val local = profiles.exportLocal()
        val now = System.currentTimeMillis()
        val merged = mergeWebdav(
            localProfiles = local.profiles,
            remoteProfiles = remote.profiles,
            localTombstones = local.tombstones,
            remoteTombstones = remote.meta.tombstones,
            localAccess = local.access,
            remoteAccess = remote.meta.access(),
            now = now,
            localStrategies = local.strategies,
            remoteStrategies = remote.meta.appsStrategies,
            localStrategyTombstones = local.strategyTombstones,
            remoteStrategyTombstones = remote.meta.appsStrategyTombstones,
            localActiveStrategy = local.activeAppsStrategy,
            localActiveStrategyUpdatedAt = local.activeAppsStrategyUpdatedAt,
            remoteActiveStrategy = remote.meta.activeAppsStrategy,
            remoteActiveStrategyUpdatedAt = remote.meta.activeAppsStrategyUpdatedAt,
        )
        val counts = profiles.apply(merged, local, now)
        val uploaded = if (action == ACTION_PULL) 0 else upload(config, remote, merged)
        WebdavLocalState(context).update { current ->
            current.copy(
                tombstones = mergeTombstones(merged.tombstones + current.tombstones, now),
                strategyTombstones = mergeTombstones(merged.strategyTombstones + current.strategyTombstones, now),
            )
        }
        if (counts.downloaded == 0 && uploaded == 0 && counts.removed == 0) {
            return context.getString(R.string.webdav_sync_idle)
        }
        return context.getString(R.string.webdav_sync_done, counts.downloaded, uploaded, counts.removed)
    }

    private fun download(config: WebdavConfig): RemoteBundle {
        client.ensureParents(config)
        val now = System.currentTimeMillis()
        val root = client.list(config)
        val nested = client.list(config, WEBDAV_PROFILES_DIR)
        sweep(config, root.map { it.name to it } + nested.map { "$WEBDAV_PROFILES_DIR/${it.name}" to it }, now)
        val etags = linkedMapOf<String, String?>()
        val metaGot = client.get(config, WEBDAV_META_FILE)
        val meta = if (!metaGot.found || metaGot.body.isBlank()) {
            SyncMetaPayload()
        } else {
            etags[WEBDAV_META_FILE] = metaGot.etag
            parseMeta(metaGot.body)
        }
        val profiles = mutableListOf<ProfilePayload>()
        val unreadable = mutableSetOf<String>()
        for (entry in nested) {
            if (entry.collection || isWebdavTempFileName(entry.name) || !entry.name.endsWith(".json")) continue
            val name = "$WEBDAV_PROFILES_DIR/${entry.name}"
            val got = client.get(config, name)
            if (!got.found) continue
            etags[name] = got.etag ?: entry.etag
            if (got.body.isBlank()) continue
            val parsed = parseProfile(got.body)
            if (parsed == null) unreadable.add(name) else profiles.add(parsed)
        }
        return RemoteBundle(profiles, meta, etags, unreadable)
    }

    private fun upload(config: WebdavConfig, remote: RemoteBundle, merged: MergeResult): Int {
        val remoteByUuid = remote.profiles.associateBy { it.uuid }
        var uploaded = 0
        val kept = mutableSetOf<String>()
        for (profile in merged.profiles) {
            val name = profileFileName(profile.uuid)
            kept.add(name)
            if (name in remote.unreadable) continue
            val existing = remoteByUuid[profile.uuid]
            if (existing != null && sameRemote(existing, profile)) continue
            client.put(
                config,
                name,
                encodeProfile(profile),
                ifMatch = remote.etags[name],
                createOnly = name !in remote.etags,
            )
            uploaded += 1
        }
        var removedRemote = 0
        for (name in remote.etags.keys) {
            if (!isProfileFileName(name) || name in kept || name in remote.unreadable) continue
            client.delete(config, name)
            removedRemote += 1
        }
        val metaChanged = uploaded > 0 || removedRemote > 0 ||
            remote.meta.access() != merged.access ||
            remote.meta.tombstones != merged.tombstones ||
            remote.meta.appsStrategies != merged.strategies ||
            remote.meta.appsStrategyTombstones != merged.strategyTombstones ||
            remote.meta.activeAppsStrategy != merged.activeAppsStrategy ||
            remote.meta.activeAppsStrategyUpdatedAt != merged.activeAppsStrategyUpdatedAt
        if (metaChanged) {
            val meta = SyncMetaPayload(
                updatedAt = System.currentTimeMillis(),
                accessControlMode = merged.access.mode,
                accessControlPackages = merged.access.packages,
                accessControlUpdatedAt = merged.access.updatedAt,
                appsStrategies = merged.strategies,
                appsStrategyTombstones = merged.strategyTombstones,
                activeAppsStrategy = merged.activeAppsStrategy,
                activeAppsStrategyUpdatedAt = merged.activeAppsStrategyUpdatedAt,
                tombstones = merged.tombstones,
            )
            client.put(
                config,
                WEBDAV_META_FILE,
                encodeMeta(meta),
                ifMatch = remote.etags[WEBDAV_META_FILE],
                createOnly = WEBDAV_META_FILE !in remote.etags,
            )
        }
        return uploaded
    }

    private fun sweep(config: WebdavConfig, entries: List<Pair<String, WebdavListEntry>>, now: Long) {
        for ((name, entry) in entries) {
            if (!isStaleWebdavTemp(name, entry.lastModified, now)) continue
            try {
                client.delete(config, name)
            } catch (_: Exception) {
            }
        }
    }

    private fun config(settings: StoredWebdavSettings): WebdavConfig {
        if (settings.url.isBlank()) throw WebdavException("empty_url")
        if (settings.username.isBlank()) throw WebdavException("empty_user")
        if (settings.password.isEmpty()) throw WebdavException("empty_password")
        return WebdavConfig(
            url = settings.url.trim(),
            username = settings.username.trim(),
            password = settings.password,
            remotePath = resolveWebdavRemoteFolder(settings.remotePath),
            clientId = WebdavLocalState(context).clientId(),
        )
    }

    private data class RemoteBundle(
        val profiles: List<ProfilePayload>,
        val meta: SyncMetaPayload,
        val etags: Map<String, String?>,
        val unreadable: Set<String>,
    )

    companion object {
        const val ACTION_TEST = "test"
        const val ACTION_SYNC = "sync"
        const val ACTION_PULL = "pull"

        private val gate = Mutex()
    }
}

private fun sameRemote(existing: ProfilePayload, profile: ProfilePayload): Boolean {
    return existing.name == profile.name &&
        existing.type == profile.type &&
        existing.source == profile.source &&
        existing.interval == profile.interval &&
        existing.updatedAt == profile.updatedAt &&
        existing.ageSecretKey == profile.ageSecretKey &&
        fingerprint(existing.files) == fingerprint(profile.files)
}

private fun WebdavException.localize(context: Context): String {
    return when (code) {
        "empty_url" -> context.getString(R.string.webdav_error_empty_url)
        "bad_url" -> context.getString(R.string.webdav_error_bad_url)
        "https" -> context.getString(R.string.webdav_error_https)
        "empty_user" -> context.getString(R.string.webdav_error_empty_user)
        "empty_password" -> context.getString(R.string.webdav_error_empty_password)
        "auth" -> context.getString(R.string.webdav_error_auth)
        "redirect" -> context.getString(R.string.webdav_error_redirect)
        "timeout" -> context.getString(R.string.webdav_error_timeout)
        "connect" -> context.getString(R.string.webdav_error_connect)
        "locked" -> context.getString(R.string.webdav_error_locked)
        "not_found", "conflict_parent" -> context.getString(R.string.webdav_error_not_found)
        "conflict" -> context.getString(R.string.webdav_error_conflict)
        "bad_version" -> context.getString(R.string.webdav_error_bad_version)
        "broken_meta" -> context.getString(R.string.webdav_error_broken)
        "too_large" -> context.getString(R.string.webdav_error_too_large, detail.ifBlank { "?" })
        "http" -> detail.ifBlank { context.getString(R.string.webdav_error_generic, status ?: 0) }
        else -> detail.ifBlank { context.getString(R.string.webdav_error_generic, code) }
    }
}
