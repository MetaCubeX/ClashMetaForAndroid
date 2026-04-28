package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import com.github.kr328.clash.core.Clash
import java.util.*

class TimeZoneModule(service: Service) : Module<Unit>(service) {
    override suspend fun run() {
        val timeZones = receiveBroadcast {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }

        // Push the current timezone once on startup, then react only to broadcasts.
        // The previous loop notified the core *before* every receive(), causing one extra
        // redundant native call on every timezone change.
        var current = TimeZone.getDefault()
        Clash.notifyTimeZoneChanged(current.id, current.rawOffset / 1000)

        while (true) {
            timeZones.receive()
            val next = TimeZone.getDefault()
            if (next.id == current.id && next.rawOffset == current.rawOffset) continue
            current = next
            Clash.notifyTimeZoneChanged(current.id, current.rawOffset / 1000)
        }
    }
}