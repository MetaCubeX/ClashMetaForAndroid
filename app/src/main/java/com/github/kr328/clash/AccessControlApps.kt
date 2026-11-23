package com.github.kr328.clash

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.classifyBuiltinAppSets
import com.github.kr328.clash.design.util.toAppInfo

internal data class AccessControlApps(
    val apps: List<AppInfo>,
    val systemPackages: Set<String>,
    val domesticPackages: Set<String>,
    val browserPackages: Set<String>,
)

/**
 * Hiding system apps only changes which rows are visible.
 * Browser-catalog packages are browser members and stay out of the other two sets.
 */
internal fun loadAccessControlApps(
    pm: PackageManager,
    selfPackage: String,
    showSystem: Boolean,
    selected: Set<String>,
    sort: AppInfoSort,
    reverse: Boolean,
    domesticCatalog: Set<String>,
    browserCatalog: Set<String>,
): AccessControlApps {
    val base = compareByDescending<AppInfo> { it.packageName in selected }
    val comparator = if (reverse) base.thenDescending(sort) else base.then(sort)

    val eligible = mutableListOf<Pair<String, Boolean>>()
    val visible = mutableListOf<PackageInfo>()

    for (pkg in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
        if (pkg.packageName == selfPackage) continue
        val info = pkg.applicationInfo ?: continue
        val hasInternet = pkg.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
        if (!hasInternet && info.uid >= Process.FIRST_APPLICATION_UID) continue

        val system = info.flags.and(ApplicationInfo.FLAG_SYSTEM) != 0
        eligible.add(pkg.packageName to system)
        if (showSystem || !system) visible.add(pkg)
    }

    val members = classifyBuiltinAppSets(eligible, domesticCatalog, browserCatalog)
    return AccessControlApps(
        apps = visible.map { it.toAppInfo(pm) }.sortedWith(comparator),
        systemPackages = members.system,
        domesticPackages = members.domestic,
        browserPackages = members.browsers,
    )
}
