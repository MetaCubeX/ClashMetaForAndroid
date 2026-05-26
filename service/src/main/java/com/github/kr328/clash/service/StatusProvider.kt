package com.github.kr328.clash.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.github.kr328.clash.common.Global

class StatusProvider : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            METHOD_STATUS_SNAPSHOT -> {
                Bundle().apply {
                    putBoolean(EXTRA_SERVICE_RUNNING, serviceRunning)
                    putString(EXTRA_CURRENT_PROFILE, currentProfile)
                }
            }
            METHOD_CURRENT_PROFILE -> {
                return if (serviceRunning)
                    Bundle().apply {
                        putString(EXTRA_CURRENT_PROFILE, currentProfile)
                    }
                else
                    null
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw IllegalArgumentException("Stub!")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        throw IllegalArgumentException("Stub!")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw IllegalArgumentException("Stub!")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw IllegalArgumentException("Stub!")
    }

    override fun getType(uri: Uri): String? {
        throw IllegalArgumentException("Stub!")
    }

    override fun onCreate(): Boolean {
        return true
    }

    companion object {
        const val METHOD_CURRENT_PROFILE = "currentProfile"
        const val METHOD_STATUS_SNAPSHOT = "statusSnapshot"
        const val EXTRA_SERVICE_RUNNING = "serviceRunning"
        const val EXTRA_CURRENT_PROFILE = "name"

        private const val CLASH_SERVICE_RUNNING_FILE = "service_running.lock"

        /**
         * Upper bound on how long [awaitServiceShutdown] will block the
         * Service.onCreate main-thread before giving up. 500ms is well below
         * Android's 5s ANR threshold and is enough to cover the cleanup
         * window of a previous Service instance whose onDestroy is still
         * running `cancelAndJoinBlocking` on the runtime scope.
         */
        private const val SERVICE_HANDOFF_TIMEOUT_MS = 500L
        private const val SERVICE_HANDOFF_POLL_MS = 20L

        var serviceRunning: Boolean = false
            set(value) {
                field = value
                if (!value) {
                    currentProfile = null
                }

                shouldStartClashOnBoot = value
            }
        var shouldStartClashOnBoot: Boolean
            get() = Global.application.filesDir.resolve(CLASH_SERVICE_RUNNING_FILE).exists()
            set(value) {
                Global.application.filesDir.resolve(CLASH_SERVICE_RUNNING_FILE).apply {
                    if (value)
                        createNewFile()
                    else
                        delete()
                }
            }
        var currentProfile: String? = null

        /**
         * Block (briefly) for the previous Service instance to finish
         * `onDestroy` and flip [serviceRunning] back to false. Returns true
         * if the slot is now free, false if the previous instance is still
         * inside cleanup after [SERVICE_HANDOFF_TIMEOUT_MS] and the caller
         * should `stopSelf()` to avoid a duplicate runtime.
         *
         * Why this exists: rapid Disconnect+Connect taps queue the new
         * Service.onCreate on the same main-thread message loop as the old
         * Service.onDestroy. `cancelAndJoinBlocking` inside onDestroy holds
         * main long enough that — in practice — the new onCreate ran with
         * [serviceRunning] still true and silently committed suicide,
         * leaving the user disconnected with no UI feedback. A short
         * busy-wait turns that into a deterministic hand-off.
         */
        fun awaitServiceShutdown(): Boolean {
            if (!serviceRunning) return true
            val deadline = System.currentTimeMillis() + SERVICE_HANDOFF_TIMEOUT_MS
            while (serviceRunning && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(SERVICE_HANDOFF_POLL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            return !serviceRunning
        }
    }
}
