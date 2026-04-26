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
            val activeDelayMs = 800L
            val idleDelayMs = 3000L
            while (isActive) {
                if (!activityStarted) {
                    delay(idleDelayMs)
                    continue
                }
                val interactive = getSystemService<PowerManager>()?.isInteractive ?: true
                try {
                    val raw = withClash { queryConnectionsSnapshot() }
                    if (raw == lastRawSnapshot) {
                        delay(if (interactive) activeDelayMs else idleDelayMs)
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
                        design.patchSnapshot(snap)
                    }
                } catch (e: Exception) {
                    Log.w("Connections refresh query failed", e)
                }
                delay(if (interactive) activeDelayMs else idleDelayMs)
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
                    design.requests.onReceive {
                        when (it) {
                            ConnectionsDesign.Request.OpenLogcat ->
                                startActivity(LogcatActivity::class.intent)
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
}
