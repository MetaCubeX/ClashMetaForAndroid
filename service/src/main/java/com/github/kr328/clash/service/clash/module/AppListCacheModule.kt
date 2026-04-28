package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import android.content.pm.PackageInfo
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

class AppListCacheModule(service: Service) : Module<Unit>(service) {
    private fun PackageInfo.uniqueUidName(): String =
        if (sharedUserId?.isNotBlank() == true) sharedUserId!! else packageName

    private fun reload() {
        val packages = service.packageManager.getInstalledPackages(0)
            .filter { it.applicationInfo != null }
            .groupBy { it.uniqueUidName() }
            .map { (_, v) ->
                val info = v[0]

                if (v.size == 1) {
                    // Force use package name if only one app in a single sharedUid group
                    // Example: firefox

                    info.applicationInfo!!.uid to info.packageName
                } else {
                    info.applicationInfo!!.uid to info.uniqueUidName()
                }
            }

        Clash.notifyInstalledAppsChanged(packages)

        Log.d("Installed ${packages.size} packages cached")
    }

    override suspend fun run() {
        val packageChanged = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        while (true) {
            reload()

            // Block until the system tells us a package was added/removed (event-driven only;
            // the previous unconditional 10s polling loop scanned the entire PM list every cycle).
            packageChanged.receive()

            // Short debounce to coalesce install/remove bursts (system updates, batch installs).
            // Drains any further events that arrive within ~1.5s before doing a single full reload.
            withTimeoutOrNull(1_500L) {
                while (true) {
                    packageChanged.receive()
                }
            }
        }
    }
}