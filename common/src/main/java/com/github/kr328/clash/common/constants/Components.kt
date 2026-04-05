package com.github.kr328.clash.common.constants

import android.content.ComponentName
import com.github.kr328.clash.common.util.packageName

object Components {
    private const val componentsPackageName = "com.github.kr328.clash"

    val MAIN_ACTIVITY = ComponentName(packageName, "$componentsPackageName.MainActivity")
    /** Launcher alias from manifest (`activity-alias android:name=".MainActivityAlias"`). */
    val MAIN_ACTIVITY_ALIAS = ComponentName(packageName, "$componentsPackageName.MainActivityAlias")
    val PROPERTIES_ACTIVITY = ComponentName(packageName, "$componentsPackageName.PropertiesActivity")
}