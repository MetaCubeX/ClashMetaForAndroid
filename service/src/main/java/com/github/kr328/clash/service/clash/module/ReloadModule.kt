package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log

class ReloadModule(service: Service) : Module<Unit>(service) {
    override suspend fun run() {
        val broadcasts = receiveBroadcast {
            addAction(Intents.ACTION_CLASH_REQUEST_RELOAD)
        }

        while (true) {
            broadcasts.receive()

            Log.i("Request reload tun")

            enqueueEvent(Unit)
        }
    }
}
