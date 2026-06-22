package com.github.kr328.clash.design.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * True on Android TV / leanback devices. Used to surface the companion remote-control entry
 * differently per form factor: a visible row on the TV home (where deep settings navigation with a
 * D-pad is painful), versus an entry in the phone's "+" sheet.
 */
fun Context.isTelevision(): Boolean {
    val ui = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature("android.hardware.type.television")
}
