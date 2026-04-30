package com.github.kr328.clash

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.github.kr328.clash.common.compat.registerReceiverCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.constants.Permissions
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.util.autoSelectFirstRuntimeProxy
import com.github.kr328.clash.util.prepareQuickTileVpnOnly
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class TileService : TileService() {
    private var currentProfile = ""
    private var clashRunning = false
    private val tileJob = SupervisorJob()
    private val tileScope = CoroutineScope(tileJob + Dispatchers.Main.immediate)

    override fun onClick() {
        val tile = qsTile ?: return

        when (tile.state) {
            Tile.STATE_INACTIVE -> {
                startVpnFromTileOrShowPermission()
            }
            Tile.STATE_ACTIVE -> {
                clashRunning = false
                currentProfile = ""
                updateTile()
                stopClashService()
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()

        registerReceiverCompat(
            receiver,
            IntentFilter().apply {
                addAction(Intents.ACTION_CLASH_STARTED)
                addAction(Intents.ACTION_CLASH_STOPPED)
                addAction(Intents.ACTION_PROFILE_LOADED)
                addAction(Intents.ACTION_SERVICE_RECREATED)
            },
            Permissions.RECEIVE_SELF_BROADCASTS,
            null
        )

        val name = StatusClient(this).currentProfile()

        clashRunning = name != null
        currentProfile = name ?: ""

        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()

        unregisterReceiver(receiver)
    }

    private fun updateTile() {
        val tile = qsTile ?: return

        tile.state = if (clashRunning)
            Tile.STATE_ACTIVE
        else
            Tile.STATE_INACTIVE

        tile.label = if (currentProfile.isEmpty())
            getText(R.string.launch_name)
        else
            currentProfile

        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_tile)

        tile.updateTile()
    }

    /**
     * When VPN permission is already granted, start the service from the tile without
     * launching a task-root activity (avoids "opening" the full app on many OEMs).
     */
    private fun startVpnFromTileOrShowPermission() {
        if (VpnService.prepare(this) != null) {
            startQuickTileActivity()
            return
        }
        clashRunning = true
        currentProfile = ""
        updateTile()
        val pendingPermission = runCatching { startClashService() }.getOrNull()
        if (pendingPermission != null) {
            clashRunning = false
            updateTile()
            startQuickTileActivity()
            return
        }
        tileScope.launch {
            runCatching {
                prepareQuickTileVpnOnly()
                autoSelectFirstRuntimeProxy()
            }.onFailure {
                clashRunning = false
                updateTile()
            }
        }
    }

    private fun startQuickTileActivity() {
        val intent = Intent(this, QuickTileStartActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        tileJob.cancel()
        super.onDestroy()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intents.ACTION_CLASH_STARTED -> {
                    clashRunning = true

                    currentProfile = ""
                }
                Intents.ACTION_CLASH_STOPPED, Intents.ACTION_SERVICE_RECREATED -> {
                    clashRunning = false

                    currentProfile = ""
                }
                Intents.ACTION_PROFILE_LOADED -> {
                    currentProfile = StatusClient(this@TileService).currentProfile() ?: ""
                }
            }

            updateTile()
        }
    }
}
