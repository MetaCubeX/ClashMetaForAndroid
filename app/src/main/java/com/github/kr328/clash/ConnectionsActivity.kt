package com.github.kr328.clash

import android.os.PowerManager
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.model.ConnectionsSnapshot
import com.github.kr328.clash.design.ConnectionsDesign
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
            var lastRevision: Long = Long.MIN_VALUE
            var lastRawSnapshot: String? = null
            while (isActive) {
                if (!activityStarted) {
                    delay(1200)
                    continue
                }
                val interactive = getSystemService<PowerManager>()?.isInteractive ?: true
                try {
                    val revision = withClash {
                        queryTrafficTotal()
                    }
                    if (revision == lastRevision) {
                        delay(if (interactive) 1200 else 2200)
                        continue
                    }
                    lastRevision = revision
                    val raw = withClash { queryConnectionsSnapshot() }
                    if (raw == lastRawSnapshot) {
                        delay(if (interactive) 1200 else 2200)
                        continue
                    }
                    lastRawSnapshot = raw
                    val snap = withContext(Dispatchers.Default) {
                        runCatching {
                            json.decodeFromString(ConnectionsSnapshot.serializer(), raw)
                        }.getOrElse { ConnectionsSnapshot() }
                    }
                    design.patchSnapshot(snap)
                } catch (_: Exception) {
                }
                delay(if (interactive) 1200 else 2200)
            }
        }

        try {
            while (isActive) {
                select<Unit> {
                    design.requests.onReceive {
                        when (it) {
                            ConnectionsDesign.Request.OpenLogcat ->
                                startActivity(LogcatActivity::class.intent)
                        }
                    }
                }
            }
        } finally {
            refresh.cancel()
        }
    }
}
