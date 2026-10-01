package com.github.kr328.clash.service.webdav

import android.content.Context
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

object WebdavAutoSync {
    private const val INTERVAL_MS = 15L * 60L * 1000L

    fun enqueue(context: Context) {
        val app = context.applicationContext
        Global.launch(Dispatchers.IO) {
            runCatching { sync(app, force = true) }
                .onFailure { Log.w("WebDAV sync: ${it.message}") }
        }
    }

    fun syncIfDue(context: Context) {
        val app = context.applicationContext
        Global.launch(Dispatchers.IO) {
            runCatching { sync(app, force = false) }
                .onFailure { Log.w("WebDAV sync: ${it.message}") }
        }
    }

    fun noteDeleted(context: Context, uuid: UUID, source: String, type: String, updatedAt: Long) {
        val deletedAt = maxOf(System.currentTimeMillis(), updatedAt + 1)
        WebdavLocalState(context).update { state ->
            state.copy(
                tombstones = state.tombstones + TombstonePayload(
                    uuid = uuid.toString(),
                    source = source,
                    type = type,
                    deletedAt = deletedAt,
                ),
            )
        }
    }

    fun noteAccessControl(context: Context) {
        val now = System.currentTimeMillis()
        WebdavLocalState(context).update { it.copy(accessControlUpdatedAt = now) }
    }

    fun noteAppsStrategy(context: Context, uuid: UUID) {
        val now = System.currentTimeMillis()
        WebdavLocalState(context).update { state ->
            state.copy(strategyUpdatedAt = state.strategyUpdatedAt + (uuid.toString() to now))
        }
    }

    fun noteActiveAppsStrategy(context: Context) {
        val now = System.currentTimeMillis()
        WebdavLocalState(context).update { it.copy(activeStrategyUpdatedAt = now) }
    }

    fun noteStrategyDeleted(context: Context, uuid: UUID) {
        val deletedAt = System.currentTimeMillis()
        WebdavLocalState(context).update { state ->
            state.copy(
                strategyTombstones = state.strategyTombstones + TombstonePayload(
                    uuid = uuid.toString(),
                    type = "AppsStrategy",
                    deletedAt = deletedAt,
                ),
                strategyUpdatedAt = state.strategyUpdatedAt - uuid.toString(),
            )
        }
    }

    private suspend fun sync(context: Context, force: Boolean) {
        val settings = WebdavSettingsStore(context).load()
        if (!settings.autoSync || settings.url.isBlank() || settings.username.isBlank() || settings.password.isEmpty()) {
            return
        }
        if (!force) {
            val last = WebdavLocalState(context).read().lastSyncAt
            if (System.currentTimeMillis() - last < INTERVAL_MS) return
        }
        WebdavSync(context).perform(WebdavSync.ACTION_SYNC, settings)
    }
}
