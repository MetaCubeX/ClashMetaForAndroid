package com.github.kr328.clash

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.util.toAppInfo

internal data class AccessControlApps(
    val apps: List<AppInfo>,
    val systemPackages: Set<String>,
    val domesticPackages: Set<String>,
)

/**
 * System and domestic sets ignore the "hide system apps" filter.
 * That filter only changes which rows are visible.
 */
internal fun loadAccessControlApps(
    pm: PackageManager,
    selfPackage: String,
    showSystem: Boolean,
    selected: Set<String>,
    sort: AppInfoSort,
    reverse: Boolean,
    domesticCatalog: Set<String>,
): AccessControlApps {
    val base = compareByDescending<AppInfo> { it.packageName in selected }
    val comparator = if (reverse) base.thenDescending(sort) else base.then(sort)

    val systemPackages = mutableSetOf<String>()
    val domesticPackages = mutableSetOf<String>()
    val visible = mutableListOf<PackageInfo>()

    for (pkg in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
        if (pkg.packageName == selfPackage) continue
        val info = pkg.applicationInfo ?: continue
        val hasInternet = pkg.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
        if (!hasInternet && info.uid >= Process.FIRST_APPLICATION_UID) continue

        val system = info.flags.and(ApplicationInfo.FLAG_SYSTEM) != 0
        if (system) systemPackages.add(pkg.packageName)
        if (pkg.packageName in domesticCatalog) domesticPackages.add(pkg.packageName)
        if (showSystem || !system) visible.add(pkg)
    }

    return AccessControlApps(
        apps = visible.map { it.toAppInfo(pm) }.sortedWith(comparator),
        systemPackages = systemPackages,
        domesticPackages = domesticPackages,
    )
}
