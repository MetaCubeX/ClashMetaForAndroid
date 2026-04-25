package com.github.kr328.clash.remote

import android.content.Context
import android.net.Uri
import com.github.kr328.clash.common.constants.Authorities
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.StatusProvider

class StatusClient(private val context: Context) {
    data class StatusSnapshot(
        val serviceRunning: Boolean,
        val currentProfile: String?,
    )

    private val uri: Uri
        get() {
            return Uri.Builder()
                .scheme("content")
                .authority(Authorities.STATUS_PROVIDER)
                .build()
        }

    fun currentProfile(): String? {
        return try {
            val result = context.contentResolver.call(
                uri,
                StatusProvider.METHOD_CURRENT_PROFILE,
                null,
                null
            )

            result?.getString(StatusProvider.EXTRA_CURRENT_PROFILE)
        } catch (e: Exception) {
            Log.w("Query current profile: $e", e)

            null
        }
    }

    fun statusSnapshot(): StatusSnapshot {
        return try {
            val result = context.contentResolver.call(
                uri,
                StatusProvider.METHOD_STATUS_SNAPSHOT,
                null,
                null
            )

            StatusSnapshot(
                serviceRunning = result?.getBoolean(StatusProvider.EXTRA_SERVICE_RUNNING) ?: false,
                currentProfile = result?.getString(StatusProvider.EXTRA_CURRENT_PROFILE),
            )
        } catch (e: Exception) {
            Log.w("Query service status: $e", e)

            StatusSnapshot(serviceRunning = false, currentProfile = null)
        }
    }
}
