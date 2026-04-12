package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.sideload.readGeoipDatabaseFrom
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

class SideloadDatabaseModule(service: Service) : Module<SideloadDatabaseModule.LoadException>(service) {
    data class LoadException(val message: String)

    private val store = ServiceStore(service)
    private var current = ""

    private fun load(packageName: String) {
        if (packageName.isBlank()) {
            current = ""
            Log.d("Sideload geoip: use built-in database")

            return
        }

        val bytes = readGeoipDatabaseFrom(service, packageName)
            ?: throw IllegalStateException("Sideload geoip package is unavailable: $packageName")

        Clash.installSideloadGeoip(bytes)
        current = packageName

        Log.d("Sideload geoip loaded from $packageName")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun run() {
        val packageChanged = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        var selected = ""
        var reload = true

        while (true) {
            val latest = store.sideloadGeoip.trim()
            if (latest != selected) {
                selected = latest
                reload = true
            }

            if (reload) {
                reload = false

                try {
                    load(selected)
                } catch (e: Exception) {
                    current = selected
                    enqueueEvent(LoadException(e.message ?: "Unknown"))
                }
            }

            select<Unit> {
                packageChanged.onReceive {
                    val changed = it.data?.schemeSpecificPart.orEmpty()
                    if (selected.isNotBlank() && changed == selected) {
                        reload = true
                    }
                }
                onTimeout(3000) {}
            }
        }
    }
}
