package com.github.kr328.clash

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.common.model.CoreMode
import com.github.kr328.clash.common.store.CoreStore
import com.github.kr328.clash.common.store.CoreStore.PendingAction
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.design.R
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.util.withProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal fun Context.currentCoreMode(): CoreMode {
    return CoreStore(this).currentMode
}

internal suspend fun AppCompatActivity.requestCoreMode(
    initialMode: CoreMode? = null
): CoreMode? {
    val labels = arrayOf(
        getString(R.string.meta_core),
        getString(R.string.ninja_core),
    )
    val values = arrayOf(CoreMode.Meta, CoreMode.Ninja)
    val initialIndex = values.indexOf(initialMode).takeIf { it >= 0 } ?: 0

    return suspendCoroutine { continuation ->
        var selectedIndex = initialIndex

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_core_mode)
            .setSingleChoiceItems(labels, initialIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                continuation.resume(values[selectedIndex])
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                continuation.resume(null)
            }
            .setOnCancelListener {
                continuation.resume(null)
            }
            .show()

        dialog.setCanceledOnTouchOutside(true)
    }
}

internal suspend fun AppCompatActivity.confirmCoreRestart(target: CoreMode): Boolean {
    val message = getString(
        R.string.core_switch_restart_message,
        getString(target.displayNameRes()),
    )

    return suspendCoroutine { continuation ->
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.core_switch_restart_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ -> continuation.resume(true) }
            .setNegativeButton(R.string.cancel) { _, _ -> continuation.resume(false) }
            .setOnCancelListener { continuation.resume(false) }
            .show()

        dialog.setCanceledOnTouchOutside(true)
    }
}

internal fun CoreMode.displayNameRes(): Int {
    return when (this) {
        CoreMode.Meta -> R.string.meta_core
        CoreMode.Ninja -> R.string.ninja_core
    }
}

internal suspend fun Context.scheduleCoreRestart(
    targetMode: CoreMode,
    pendingAction: PendingAction,
    profileUuid: java.util.UUID? = null,
) {
    runCatching {
        val active = withProfile { queryActive() }
        if (active != null && active.coreMode != targetMode) {
            Log.i("Clearing incompatible active profile ${active.uuid} before switching core to $targetMode")
            ServiceStore(this).activeProfile = null
        }
    }.onFailure {
        Log.w("Failed to clear incompatible active profile: $it")
    }

    val coreStore = CoreStore(this)
    coreStore.currentMode = targetMode
    coreStore.pendingAction = pendingAction
    coreStore.pendingProfileUuid = profileUuid

    Remote.service.coreSwitchInProgress = true

    try {
        val previousRemote = runCatching { Remote.service.remote.get() }.getOrNull()

        runCatching {
            previousRemote?.reload()
        }.onFailure {
            Log.w("Failed to request core reload: $it")
        }

        previousRemote?.let {
            Remote.service.remote.reset(it)
        }

        Remote.service.unbind()
        Remote.service.bind()

        val reboundRemote = Remote.service.remote.get()
        reboundRemote.clash().queryCoreVersion()
    } finally {
        Remote.service.coreSwitchInProgress = false
    }
}

internal fun AppCompatActivity.reloadProgramPages() {
    packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )

        startActivity(intent)
        finishAffinity()
    }
}
