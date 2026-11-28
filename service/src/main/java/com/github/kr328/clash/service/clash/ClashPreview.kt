package com.github.kr328.clash.service.clash

import android.content.Context
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import kotlinx.coroutines.*

/**
 * Loads the active profile into the core while the service is stopped, so the
 * proxy list can be browsed and health-checked without starting the VPN.
 *
 * Preview loads strip every inbound (see `stripInbounds` in the core), and
 * share [globalLock] with [clashRuntime]: while a runtime is active it owns the
 * core and preview requests are no-ops.
 */
object ClashPreview {
    private const val RELEASE_DELAY = 60_000L
    private const val LOCK_TIMEOUT = 5_000L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var loadedKey: String? = null
    private var releaseJob: Job? = null
    private var holders = 0

    /** Called by the runtime once it owns the core. Caller holds [globalLock]. */
    internal fun invalidate() {
        releaseJob?.cancel()
        releaseJob = null
        loadedKey = null
        holders = 0
    }

    /**
     * Ensure the core has the active profile loaded.
     *
     * Each successful call with [hold] must be balanced by [scheduleRelease].
     *
     * @return null on success (or when a runtime already owns the core),
     *         otherwise the reason the profile could not be loaded.
     */
    suspend fun prepare(context: Context, hold: Boolean = true): String? {
        val runtimeActive = StatusProvider.serviceRunning && !StatusProvider.killSwitchActive

        if (runtimeActive) {
            if (!globalLock.tryLock())
                return null // runtime is active and owns the core
        } else {
            // A runtime that just stopped may still be resetting the core.
            withTimeoutOrNull(LOCK_TIMEOUT) { globalLock.lock() }
                ?: return null
        }

        try {
            if (hold) {
                releaseJob?.cancel()
                releaseJob = null
            }

            val store = ServiceStore(context)
            val uuid = store.activeProfile
                ?: return "No profile selected"
            val active = ImportedDao().queryByUUID(uuid)
                ?: return "No profile selected"
            val dir = context.importedDir.resolve(active.uuid.toString())
            val landing = store.landingProxyJson()

            val key = listOf(
                uuid.toString(),
                dir.resolve("config.yaml").lastModified().toString(),
                active.ageSecretKey.orEmpty(),
                landing.orEmpty(),
                Clash.queryOverride(Clash.OverrideSlot.Persist).toString(),
                Clash.queryOverride(Clash.OverrideSlot.Session).toString(),
            ).joinToString("\u0000")

            if (key == loadedKey) {
                if (hold) holders += 1

                return null
            }

            loadedKey = null

            Clash.reset()
            Clash.setAgeSecretKey(active.ageSecretKey?.takeIf { it.isNotBlank() })
            Clash.setLandingProxy(landing)
            Clash.load(dir, preview = true).await()

            val remove = SelectionDao().querySelections(active.uuid)
                .filterNot { Clash.patchSelector(it.proxy, it.selected) }
                .map { it.proxy }

            SelectionDao().removeSelections(active.uuid, remove)

            loadedKey = key

            if (hold) holders += 1

            Log.d("Preview of ${active.name} loaded")

            return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Load preview: ${e.message}", e)

            // nothing is loaded anymore, outstanding releases become no-ops
            loadedKey = null
            holders = 0
            Clash.reset()

            return e.message ?: "Unknown"
        } finally {
            globalLock.unlock()
        }
    }

    /** Reload the preview if one is loaded, e.g. after an override change. */
    suspend fun reloadIfLoaded(context: Context): Boolean {
        if (loadedKey == null)
            return false

        return prepare(context, hold = false) == null && loadedKey != null
    }

    /**
     * Drop one holder. Once none are left the preview is released after a
     * grace period, so reopening the proxy page shortly afterwards keeps the
     * loaded profile and its delay results.
     */
    fun scheduleRelease() {
        scope.launch {
            if (!globalLock.tryLock())
                return@launch // runtime owns the core, nothing to release

            try {
                if (loadedKey == null)
                    return@launch

                holders = (holders - 1).coerceAtLeast(0)

                if (holders > 0)
                    return@launch

                releaseJob?.cancel()
                releaseJob = scope.launch {
                    delay(RELEASE_DELAY)

                    if (!globalLock.tryLock())
                        return@launch // runtime owns the core now

                    try {
                        if (loadedKey != null && holders == 0) {
                            loadedKey = null
                            releaseJob = null

                            Clash.reset()

                            Log.d("Preview released")
                        }
                    } finally {
                        globalLock.unlock()
                    }
                }
            } finally {
                globalLock.unlock()
            }
        }
    }
}
