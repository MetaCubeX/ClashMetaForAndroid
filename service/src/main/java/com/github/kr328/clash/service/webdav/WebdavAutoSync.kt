package com.github.kr328.clash.service.webdav

import android.content.Context
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.log.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

object WebdavAutoSync {
    private val gate = Any()
    private var generation = 0
    private var failures = 0

    fun enqueue(context: Context) {
        WebdavLocalState(context).update { state ->
            state.copy(pendingPushRevision = Math.addExact(state.pendingPushRevision, 1))
        }
        schedule(context.applicationContext, WEBDAV_PUSH_DEBOUNCE_MS, resetFailures = true)
    }

    fun syncIfDue(context: Context) {
        val app = context.applicationContext
        Global.launch(Dispatchers.IO) {
            runCatching {
                val settings = WebdavSettingsStore(app).load()
                if (!ready(settings)) return@runCatching
                val state = WebdavLocalState(app).read()
                if (state.pendingPushRevision > state.completedPushRevision) {
                    schedule(app, WEBDAV_PUSH_DEBOUNCE_MS, resetFailures = true)
                } else {
                    pullIfDue(app)
                }
            }
                .onFailure { Log.w("WebDAV pull: ${it.message}") }
        }
    }

    fun noteDeleted(context: Context, uuid: UUID, source: String, type: String, updatedAt: Long) {
        val deletedAt = maxOf(System.currentTimeMillis(), Math.addExact(updatedAt, 1))
        WebdavLocalState(context).update { state ->
            val id = state.clientId.ifBlank { createWebdavClientId() }
            val key = uuid.toString()
            if (key !in state.managedProfiles) return@update state
            state.copy(
                clientId = id,
                pendingPushRevision = Math.addExact(state.pendingPushRevision, 1),
                tombstones = state.tombstones + TombstonePayload(
                    uuid = state.wireUuidByLocalId[key] ?: key,
                    source = source,
                    type = type,
                    deletedAt = deletedAt,
                    rev = state.profileSyncRev[key] ?: 0L,
                    writerId = id,
                ),
            )
        }
    }

    fun noteProfile(context: Context, uuid: UUID, onlyManaged: Boolean = false): Boolean {
        var tracked = false
        WebdavLocalState(context).update { state ->
            if (onlyManaged && uuid.toString() !in state.managedProfiles) return@update state
            tracked = true
            state.copy(
                managedProfiles = state.managedProfiles + uuid.toString(),
                pendingPushRevision = Math.addExact(state.pendingPushRevision, 1),
            )
        }
        return tracked
    }

    fun noteAccessControl(context: Context) {
        val now = System.currentTimeMillis()
        WebdavLocalState(context).update { state ->
            val id = state.clientId.ifBlank { createWebdavClientId() }
            state.copy(
                clientId = id,
                pendingPushRevision = Math.addExact(state.pendingPushRevision, 1),
                accessControlUpdatedAt = now,
                accessSyncRev = Math.addExact(state.accessSyncRev, 1),
                accessWriterId = id,
            )
        }
    }

    fun noteAppsStrategy(context: Context, uuid: UUID) {
        val now = System.currentTimeMillis()
        WebdavLocalState(context).update { state ->
            val id = state.clientId.ifBlank { createWebdavClientId() }
            val key = uuid.toString()
            state.copy(
                clientId = id,
                pendingPushRevision = Math.addExact(state.pendingPushRevision, 1),
                strategyUpdatedAt = state.strategyUpdatedAt + (key to now),
                strategySyncRev = state.strategySyncRev + (key to Math.addExact(state.strategySyncRev[key] ?: 0L, 1)),
                strategyWriterId = state.strategyWriterId + (key to id),
            )
        }
    }

    fun noteActiveAppsStrategy(context: Context) {
        val now = System.currentTimeMillis()
        WebdavLocalState(context).update { state ->
            val id = state.clientId.ifBlank { createWebdavClientId() }
            state.copy(
                clientId = id,
                pendingPushRevision = Math.addExact(state.pendingPushRevision, 1),
                activeStrategyUpdatedAt = now,
                activeStrategySyncRev = Math.addExact(state.activeStrategySyncRev, 1),
                activeStrategyWriterId = id,
            )
        }
    }

    fun noteStrategyDeleted(context: Context, uuid: UUID) {
        val deletedAt = System.currentTimeMillis()
        WebdavLocalState(context).update { state ->
            val id = state.clientId.ifBlank { createWebdavClientId() }
            val key = uuid.toString()
            state.copy(
                clientId = id,
                pendingPushRevision = Math.addExact(state.pendingPushRevision, 1),
                strategyTombstones = state.strategyTombstones + TombstonePayload(
                    uuid = key,
                    type = "AppsStrategy",
                    deletedAt = deletedAt,
                    rev = state.strategySyncRev[key] ?: 0L,
                    writerId = id,
                ),
                strategyUpdatedAt = state.strategyUpdatedAt - key,
                strategySyncRev = state.strategySyncRev - key,
                strategyWriterId = state.strategyWriterId - key,
            )
        }
    }

    private fun schedule(app: Context, waitMs: Long, resetFailures: Boolean, expectedToken: Int? = null) {
        val token = synchronized(gate) {
            if (expectedToken != null && generation != expectedToken) return
            if (resetFailures) failures = 0
            ++generation
        }
        Global.launch(Dispatchers.IO) {
            delay(waitMs)
            val run = synchronized(gate) {
                token == generation
            }
            if (!run) return@launch
            try {
                if (push(app)) {
                    synchronized(gate) { if (token == generation) failures = 0 }
                } else {
                    retry(app, token)
                }
            } catch (err: CancellationException) {
                throw err
            } catch (err: Exception) {
                Log.w("WebDAV sync: ${err.message}")
                retry(app, token)
            }
        }
    }

    private fun retry(app: Context, token: Int) {
        val wait = synchronized(gate) {
            if (token != generation) return
            failures += 1
            if (failures <= 3) WEBDAV_PUSH_DEBOUNCE_MS else WEBDAV_PULL_INTERVAL_MS
        }
        schedule(app, wait, resetFailures = false, expectedToken = token)
    }

    private suspend fun pullIfDue(context: Context) {
        val settings = WebdavSettingsStore(context).load()
        if (!ready(settings)) return
        val last = WebdavLocalState(context).read().lastPullAt
        if (System.currentTimeMillis() - last < WEBDAV_PULL_INTERVAL_MS) return
        val sync = WebdavSync(context)
        sync.perform(WebdavSync.ACTION_PULL, settings)
        if (sync.hasDeferred) enqueue(context)
    }

    private suspend fun push(context: Context): Boolean {
        val settings = WebdavSettingsStore(context).load()
        if (!ready(settings)) return true
        val state = WebdavLocalState(context)
        val revision = state.read().pendingPushRevision
        val sync = WebdavSync(context)
        sync.perform(WebdavSync.ACTION_SYNC, settings)
        if (sync.hasDeferred) return false
        state.update { current ->
            current.copy(completedPushRevision = maxOf(current.completedPushRevision, revision))
        }
        return state.read().let { it.pendingPushRevision <= it.completedPushRevision }
    }

    private fun ready(settings: StoredWebdavSettings): Boolean {
        return settings.autoSync && settings.hasEndpoint() && settings.username.isNotBlank() && settings.password.isNotEmpty()
    }
}
