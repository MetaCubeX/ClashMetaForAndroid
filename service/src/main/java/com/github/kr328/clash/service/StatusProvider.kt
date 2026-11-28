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
            METHOD_CURRENT_PROFILE -> {
                // while the kill switch blocks traffic, clash itself is not running
                return if (serviceRunning && !killSwitchActive)
                    Bundle().apply {
                        putString("name", currentProfile)
                    }
                else
                    null
            }
            METHOD_VPN_STATE -> {
                val state = vpnState ?: return null

                return Bundle().apply {
                    putBoolean(KEY_ALWAYS_ON, state.alwaysOn)
                    putBoolean(KEY_LOCKDOWN, state.lockdown)
                    putBoolean(KEY_KILL_SWITCH_ACTIVE, killSwitchActive)
                }
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

    data class VpnState(val alwaysOn: Boolean, val lockdown: Boolean)

    companion object {
        const val METHOD_CURRENT_PROFILE = "currentProfile"
        const val METHOD_VPN_STATE = "vpnState"

        const val KEY_ALWAYS_ON = "alwaysOn"
        const val KEY_LOCKDOWN = "lockdown"
        const val KEY_KILL_SWITCH_ACTIVE = "killSwitchActive"

        /** Set while [TunService] holds a blocking interface after an unexpected stop. */
        @Volatile
        var killSwitchActive: Boolean = false

        /** Always-on / lockdown state reported by a running [TunService] (API 29+). */
        @Volatile
        var vpnState: VpnState? = null

        private const val CLASH_SERVICE_RUNNING_FILE = "service_running.lock"

        var serviceRunning: Boolean = false
            set(value) {
                field = value

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
    }
}