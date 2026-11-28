package com.github.kr328.clash.remote

import android.content.Context
import android.net.Uri
import com.github.kr328.clash.common.constants.Authorities
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.StatusProvider

class StatusClient(private val context: Context) {
    private val uri: Uri
        get() {
            return Uri.Builder()
                .scheme("content")
                .authority(Authorities.STATUS_PROVIDER)
                .build()
        }

    /** Always-on / lockdown state, only known while the VPN service is running (API 29+). */
    fun vpnState(): StatusProvider.VpnState? {
        return try {
            val result = context.contentResolver.call(
                uri,
                StatusProvider.METHOD_VPN_STATE,
                null,
                null
            ) ?: return null

            StatusProvider.VpnState(
                alwaysOn = result.getBoolean(StatusProvider.KEY_ALWAYS_ON),
                lockdown = result.getBoolean(StatusProvider.KEY_LOCKDOWN),
            )
        } catch (e: Exception) {
            Log.w("Query vpn state: $e", e)

            null
        }
    }

    fun currentProfile(): String? {
        return try {
            val result = context.contentResolver.call(
                uri,
                StatusProvider.METHOD_CURRENT_PROFILE,
                null,
                null
            )

            result?.getString("name")
        } catch (e: Exception) {
            Log.w("Query current profile: $e", e)

            null
        }
    }
}