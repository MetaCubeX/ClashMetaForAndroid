package com.github.kr328.clash

import android.os.PowerManager
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.model.ConnectionsSnapshot
import com.github.kr328.clash.design.ConnectionsDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ConnectionsActivity : BaseActivity<ConnectionsDesign>() {
    override suspend fun main() {
        val design = ConnectionsDesign(this)
        setContentDesign(design)

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val refresh = launch {
            var lastRawSnapshot: String? = null
            // Base interval for a quiet device with a handful of connections.
            val baseDelayMs = 2000L
            // Bumped from 7s -> 30s. While the activity is in background or screen is off,
            // the live list is invisible; reduce wakeups and avoid pulling JSON snapshots.
            val idleDelayMs = 30_000L
            // Last observed connection count — drives the adaptive throttle on the
            // next iteration so a torrenting user (hundreds of live connections)
            // doesn't burn CPU/GC reparsing a multi-MB JSON every 2s. Updated only
            // when the snapshot actually decoded, so transient parse failures
            // don't shrink the interval back to the base.
            var lastConnectionCount = 0
            while (isActive) {
                val interactive = getSystemService<PowerManager>()?.isInteractive ?: true
                if (!activityStarted || !interactive) {
                    // Skip query entirely: no UI to update, no need to wake the core.
                    delay(idleDelayMs)
                    continue
                }
                try {
                    val raw = withClash { queryConnectionsSnapshot() }
                    if (raw == lastRawSnapshot) {
                        delay(scaledDelayFor(baseDelayMs, lastConnectionCount))
                        continue
                    }
                    lastRawSnapshot = raw
                    val snap = withContext(Dispatchers.Default) {
                        runCatching {
                            json.decodeFromString(ConnectionsSnapshot.serializer(), raw)
                        }.getOrElse {
                            Log.w("Connections snapshot decode failed; raw size=${raw.length}", it)
                            null
                        }
                    }
                    if (snap != null) {
                        if (snap.connections.isEmpty() && raw.length > 64) {
                            Log.w("Connections snapshot parsed but empty list; raw size=${raw.length}")
                        }
                        lastConnectionCount = snap.connections.size
                        design.patchSnapshot(snap)
                    }
                } catch (e: Exception) {
                    Log.w("Connections refresh query failed", e)
                }
                delay(scaledDelayFor(baseDelayMs, lastConnectionCount))
            }
        }

        suspend fun reloadSnapshot() {
            val raw = withClash { queryConnectionsSnapshot() }
            val snap = withContext(Dispatchers.Default) {
                runCatching {
                    json.decodeFromString(ConnectionsSnapshot.serializer(), raw)
                }.getOrElse {
                    Log.w("Connections reload decode failed; raw size=${raw.length}", it)
                    null
                }
            }
            if (snap != null) {
                design.patchSnapshot(snap)
            }
        }

        try {
            while (isActive) {
                select<Unit> {
                    events.onReceive {
                        when (it) {
                            Event.ConnectionsChanged -> launch { reloadSnapshot() }
                            else -> Unit
                        }
                    }

                    design.requests.onReceive {
                        when (it) {
                            ConnectionsDesign.Request.OpenLogcat ->
                                startActivity(LogcatActivity::class.intent)
                            ConnectionsDesign.Request.OpenRequestHistory ->
                                startActivity(RequestHistoryActivity::class.intent)
                            is ConnectionsDesign.Request.CloseConnection -> launch {
                                val closed = withClash { closeConnection(it.id) }
                                design.showToast(
                                    if (closed) R.string.connections_closed_one else R.string.connections_closed_none,
                                    ToastDuration.Short,
                                )
                                reloadSnapshot()
                            }
                            ConnectionsDesign.Request.CloseAllConnections -> launch {
                                val closed = withClash { closeAllConnections() }
                                design.showToast(
                                    getString(R.string.connections_closed_many, closed),
                                    ToastDuration.Short,
                                )
                                reloadSnapshot()
                            }
                        }
                    }
                }
            }
        } finally {
            refresh.cancel()
        }
    }

    /**
     * Adaptive snapshot interval. For a handful of connections we stay at
     * the base 2s (the list stays "live"); above the user-visible noise
     * floor we slow down so the JSON payload + parse stop being a hot
     * loop on CPU/GC. Thresholds were picked empirically: torrenting
     * sessions easily hit 200+ live connections, sustained P2P / scraping
     * workloads cross 500. Snapshots above that no longer feel "live" to
     * a human reader either way, so the slower cadence isn't a UX loss.
     */
    private fun scaledDelayFor(baseDelayMs: Long, connectionCount: Int): Long = when {
        connectionCount > 500 -> baseDelayMs * 4
        connectionCount > 200 -> baseDelayMs * 2
        else -> baseDelayMs
    }
}
