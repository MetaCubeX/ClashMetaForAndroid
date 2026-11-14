package com.github.kr328.clash.common.compat

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.lang.reflect.Method

/**
 * Cross-user (app cloning / "app cloning" / work profile) package enumeration.
 *
 * Android VPNs are strictly per-user: a VPN established in the main profile
 * never covers apps running in other profiles (e.g. OEM app-cloning instances
 * in the clone user). Enumerating those instances nevertheless makes them
 * visible in access control and lets the core resolve their uids.
 *
 * Cross-user package queries require INTERACT_ACROSS_USERS. The app declares
 * it, but it is only grantable manually via ADB, e.g.:
 *
 *   adb shell pm grant --user 0 <package> android.permission.INTERACT_ACROSS_USERS
 *
 * Every function here fails soft: it returns empty/null instead of throwing
 * when the user id is unavailable or the permission is missing.
 */

private val getInstalledPackagesAsUserMethod: Method? by lazy {
    runCatching {
        PackageManager::class.java.getDeclaredMethod(
            "getInstalledPackagesAsUser",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
    }.getOrNull()
}

private val userHandleGetIdentifierMethod: Method? by lazy {
    runCatching {
        UserHandle::class.java.getDeclaredMethod("getIdentifier")
    }.getOrNull()
}

private fun UserHandle.identifierCompat(): Int? {
    val method = userHandleGetIdentifierMethod ?: return null

    return runCatching {
        method.invoke(this) as Int
    }.getOrNull()
}

/**
 * Profiles of the current user other than the main profile, e.g. work
 * profiles and app-cloning ("dual app") profiles.
 */
fun Context.crossUserProfiles(): List<UserHandle> {
    val userManager = getSystemService(UserManager::class.java) ?: return emptyList()

    return runCatching {
        userManager.userProfiles
            .filter { it != Process.myUserHandle() }
    }.getOrDefault(emptyList())
}

/**
 * Installed packages of the given profile, or null when the query is not
 * possible (missing INTERACT_ACROSS_USERS, hidden api blocked, ...).
 */
fun Context.getInstalledPackagesAsUserCompat(
    flags: Int,
    profile: UserHandle,
): List<PackageInfo>? {
    val method = getInstalledPackagesAsUserMethod ?: return null
    val userId = profile.identifierCompat() ?: return null

    return runCatching {
        @Suppress("UNCHECKED_CAST")
        method.invoke(packageManager, flags, userId) as? List<PackageInfo>
    }.getOrNull()
}
