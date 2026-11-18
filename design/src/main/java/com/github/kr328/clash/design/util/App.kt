package com.github.kr328.clash.design.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.github.kr328.clash.common.compat.foreground
import com.github.kr328.clash.design.model.AppInfo

fun PackageInfo.toAppInfo(pm: PackageManager, labelSuffix: String? = null): AppInfo {
    val label = applicationInfo!!.loadLabel(pm).toString()

    return AppInfo(
        packageName = packageName,
        icon = applicationInfo!!.loadIcon(pm).foreground(),
        label = if (labelSuffix != null) "$label ($labelSuffix)" else label,
        installTime = firstInstallTime,
        updateDate = lastUpdateTime,
    )
}
