package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConnectionsSnapshot
import com.github.kr328.clash.service.model.RequestHistoryRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class RequestHistoryModule(service: Service) : Module<Unit>(service) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun run() = coroutineScope {
        val ticker = ticker(TimeUnit.SECONDS.toMillis(2))

        while (true) {
            select<Unit> {
                ticker.onReceive {
                    ingestSnapshot()
                }
            }
        }
    }

    private fun ingestSnapshot() {
        val raw = runCatching { Clash.queryConnectionsSnapshot() }.getOrElse {
            Log.w("Request history snapshot query failed", it)
            return
        }
        val snap = runCatching {
            json.decodeFromString(ConnectionsSnapshot.serializer(), raw)
        }.getOrElse {
            Log.w("Request history snapshot decode failed; raw size=${raw.length}", it)
            return
        }
        RequestHistoryRepository.ingest(snap.connections)
    }
}
