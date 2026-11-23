package com.github.kr328.clash.service.model

data class EffectiveAccessControl(
    val mode: AccessControlMode,
    val packages: List<String>,
)

fun resolveAccessControl(
    activeStrategy: AppsStrategyConfig?,
    globalMode: AccessControlMode = AccessControlMode.AcceptAll,
    globalPackages: Set<String> = emptySet(),
): EffectiveAccessControl {
    return if (activeStrategy != null) {
        EffectiveAccessControl(activeStrategy.mode, activeStrategy.packages)
    } else {
        EffectiveAccessControl(globalMode, globalPackages.toList())
    }
}

fun globalAccessControlDrivesTunnel(activeStrategy: AppsStrategyConfig?): Boolean =
    activeStrategy == null

fun legacyAccessControlNeedsMigration(
    mode: AccessControlMode,
    packages: Collection<String>,
): Boolean = when (mode) {
    AccessControlMode.AcceptAll -> false
    AccessControlMode.AcceptSelected -> true
    AccessControlMode.DenySelected -> packages.isNotEmpty()
}
