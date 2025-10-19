package com.github.kr328.clash.common.constants

import android.content.ComponentName
import com.github.kr328.clash.common.util.packageName

object Components {
    private const val componentsPackageName = "com.github.kr328.clash"

    val MAIN_ACTIVITY = ComponentName(packageName, "$componentsPackageName.MainActivity")
    val PROPERTIES_ACTIVITY = ComponentName(packageName, "$componentsPackageName.PropertiesActivity")

    // 新建/编辑配置页
    val NEW_PROFILE_ACTIVITY = ComponentName(packageName, "$componentsPackageName.NewProfileActivity")
}