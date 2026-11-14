package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import android.content.pm.PackageInfo
import com.github.kr328.clash.common.compat.crossUserProfiles
import com.github.kr328.clash.common.compat.getInstalledPackagesAsUserCompat
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class AppListCacheModule(service: Service) : Module<Unit>(service) {
    private fun PackageInfo.uniqueUidName(): String =
        if (sharedUserId?.isNotBlank() == true) sharedUserId!! else packageName

    private fun List<PackageInfo>.toUidNames(): List<Pair<Int, String>> =
        filter { it.applicationInfo != null }
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

    private fun reload() {
        val packages = buildList {
            addAll(service.packageManager.getInstalledPackages(0).toUidNames())

            // Packages in other profiles (e.g. app-cloning instances) have their
            // own uids; enumerate them separately so connections from cloned apps
            // can be resolved by package name. Requires INTERACT_ACROSS_USERS,
            // silently skipped otherwise.
            service.crossUserProfiles().forEach { profile ->
                service.getInstalledPackagesAsUserCompat(0, profile)
                    ?.toUidNames()
                    ?.let(::addAll)
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

            packageChanged.receive()

            delay(TimeUnit.SECONDS.toMillis(10))
        }
    }
}