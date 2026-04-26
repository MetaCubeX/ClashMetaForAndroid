package com.github.kr328.clash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.R as AppR
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.util.autoSelectFirstRuntimeProxy
import com.github.kr328.clash.util.prepareQuickTileVpnOnly
import com.github.kr328.clash.util.startClashService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class QuickTileStartActivity : ComponentActivity(), CoroutineScope by MainScope() {
    private var requestInFlight = false
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        requestInFlight = false
        if (result.resultCode != RESULT_OK) {
            Log.w("Quick tile VPN permission denied")
            showPermissionDeniedNotification()
            finish()
            return@registerForActivityResult
        }

        launch {
            runCatching {
                val secondRequest = startClashService()
                if (secondRequest != null) {
                    Log.w("Quick tile VPN permission is still required after approval")
                    showPermissionDeniedNotification()
                    finish()
                } else {
                    finishStarted()
                }
            }.onFailure {
                Log.e("Quick tile start after VPN permission failed", it)
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        if (savedInstanceState == null) {
            startFromForeground()
        }
    }

    private fun startFromForeground() {
        launch {
            runCatching {
                prepareQuickTileVpnOnly()
                val vpnRequest = startClashService()
                if (vpnRequest != null) {
                    requestInFlight = true
                    vpnPermissionLauncher.launch(vpnRequest)
                    return@launch
                }
                finishStarted()
            }.onFailure {
                Log.e("Quick tile start failed", it)
                finish()
            }
        }
    }

    private suspend fun finishStarted() {
        runCatching {
            autoSelectFirstRuntimeProxy()
        }.onFailure {
            Log.w("Quick tile server auto-selection failed", it)
        }
        finish()
    }

    private fun showPermissionDeniedNotification() {
        val nm = NotificationManagerCompat.from(this)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(
                CHANNEL_TILE_PERMISSION,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
                .setName(getString(AppR.string.launch_name))
                .setDescription(getString(DesignR.string.quick_tile_permission_denied_notification))
                .build()
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_TILE_PERMISSION)
            .setSmallIcon(AppR.drawable.ic_qs_tile)
            .setContentTitle(getString(AppR.string.launch_name))
            .setContentText(getString(DesignR.string.quick_tile_permission_denied_notification))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_TILE_PERMISSION, notification)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        if (!requestInFlight) {
            cancel()
        }
        super.onDestroy()
    }

    private companion object {
        private const val CHANNEL_TILE_PERMISSION = "quick_tile_permission"
        private const val NOTIFICATION_TILE_PERMISSION = 1402
    }
}
