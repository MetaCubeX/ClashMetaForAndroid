package com.github.kr328.clash.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProxySort

class StatusProvider : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            METHOD_CURRENT_PROFILE -> {
                return if (serviceRunning)
                    Bundle().apply {
                        putString("name", currentProfile)
                    }
                else
                    null
            }
            METHOD_CURRENT_PROXY -> {
                // currentProfile is only set after Clash.load() has finished, which
                // guarantees that the native core is ready to be queried.
                return if (serviceRunning && currentProfile != null)
                    Bundle().apply {
                        putString("name", queryCurrentProxy())
                    }
                else
                    null
            }
            else -> super.call(method, arg, extras)
        }
    }

    private fun queryCurrentProxy(): String? {
        return try {
            val names = Clash.queryGroupNames(false)

            // "Proxy" is the conventional main group. Otherwise fall back to the
            // first group that is not the built-in GLOBAL group.
            val groupName = names.firstOrNull { it == "Proxy" }
                ?: names.firstOrNull { !it.equals("GLOBAL", ignoreCase = true) }
                ?: return null

            Clash.queryGroup(groupName, ProxySort.Default)
                .now
                .takeIf { it.isNotBlank() }
        } catch (e: Throwable) {
            null
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
        const val METHOD_CURRENT_PROXY = "currentProxy"

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