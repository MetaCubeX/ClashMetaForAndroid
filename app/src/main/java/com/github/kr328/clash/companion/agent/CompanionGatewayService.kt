package com.github.kr328.clash.companion.agent

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.CompanionActivity
import com.github.kr328.clash.R
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundCompat
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.companion.CompanionStore
import com.github.kr328.clash.design.R as DR
import com.github.kr328.clash.companion.protocol.DiscoveryTxt
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Dedicated foreground service hosting the clashctl gateway (PROTOCOL.md §1.1). Independent of the
 * VPN service so `/v1/power on` can start the VPN from a stopped state. While running it serves the
 * pinned-HTTPS gateway and advertises mDNS; on stop it withdraws both. Default OFF — only started
 * when the user enables the toggle (§2).
 */
class CompanionGatewayService : Service(), CompanionHooks,
    CoroutineScope by CoroutineScope(Dispatchers.Default) {

    private lateinit var store: CompanionStore
    private lateinit var pairingStore: PairingStore
    private val mdns by lazy { CompanionMdns(this) }

    private var server: GatewayServer? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate() {
        super.onCreate()
        store = CompanionStore(this)
        pairingStore = PairingStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(R.id.nf_companion_status, buildNotification())
        // TLS keygen / keystore load can be slow — do it off the main thread.
        launch(Dispatchers.IO) { startGateway() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopGateway()
        cancel()
        @Suppress("DEPRECATION")
        stopForeground(true)
        super.onDestroy()
    }

    // --- gateway lifecycle --------------------------------------------------------------------

    @Synchronized
    private fun startGateway() {
        if (server != null) return
        try {
            val identity = CompanionTlsIdentity.load(this)
            // Reuse the previously chosen port so a scanned QR stays valid across restarts;
            // fall back to an ephemeral port if the saved one is taken.
            val srv = try {
                GatewayServer(store.gatewayPort, identity.serverSocketFactory(), pairingStore, store, this)
                    .also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
            } catch (bind: java.io.IOException) {
                Log.w("Companion: port ${store.gatewayPort} unavailable, using ephemeral: ${bind.message}")
                GatewayServer(0, identity.serverSocketFactory(), pairingStore, store, this)
                    .also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
            }
            server = srv

            val port = srv.listeningPort
            store.gatewayPort = port // persist the actual bound port
            acquireMulticastLock()
            announce(store.displayName, identity.fingerprint, port)
            CompanionAgent.onStarted(port, identity.fingerprint)
            Log.i("Companion: gateway up on :$port")
        } catch (t: Throwable) {
            Log.e("Companion: failed to start gateway: ${t.message}", t)
            stopSelf()
        }
    }

    @Synchronized
    private fun stopGateway() {
        mdns.unregister()
        server?.stop()
        server = null
        releaseMulticastLock()
        CompanionAgent.onStopped()
    }

    private fun announce(name: String, fingerprint: String, port: Int) {
        mdns.register(
            DiscoveryTxt(
                app = CompanionStore.APP_ID,
                id = store.deviceId,
                name = name,
                ver = 1,
                fp = fingerprint,
            ),
            port,
        )
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("clashctl").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w("Companion: multicast lock failed: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        multicastLock = null
    }

    // --- CompanionHooks -----------------------------------------------------------------------

    override fun powerState(): String =
        if (StatusClient(this).statusSnapshot().serviceRunning) "on" else "off"

    override fun power(action: String): String {
        val running = StatusClient(this).statusSnapshot().serviceRunning
        val target = when (action) {
            "on" -> true
            "off" -> false
            "toggle" -> !running
            else -> running
        }
        if (target && !running) {
            val consent = startClashService()
            if (consent != null) {
                throw CompanionHooks.PowerUnavailable("VPN permission not granted on the agent device")
            }
        } else if (!target && running) {
            stopClashService()
        }
        return if (target) "on" else "off"
    }

    override fun importSubscription(url: String?, payload: String?, name: String) {
        if (url == null) {
            throw CompanionHooks.PayloadUnsupported("inline payload import not supported in P1; send a url")
        }
        runBlocking {
            val uuid = withProfile { create(Profile.Type.Url, name, url) }
            withProfile { commit(uuid) }
        }
    }

    override fun rename(name: String) {
        store.displayName = name
        val fp = CompanionAgent.fingerprint ?: return
        val port = CompanionAgent.port
        if (port > 0) announce(name, fp, port)
    }

    // --- notification -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(
                CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW,
            ).setName(getString(DR.string.companion_notification_channel)).build(),
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(com.github.kr328.clash.service.R.drawable.ic_logo_service)
        .setContentTitle(getString(DR.string.companion_notification_title))
        .setContentText(getString(DR.string.companion_notification_text))
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                R.id.nf_companion_status,
                CompanionActivity::class.intent
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
            ),
        )
        .build()

    companion object {
        private const val CHANNEL_ID = "companion_gateway_channel"

        fun start(context: Context) {
            context.startForegroundServiceCompat(CompanionGatewayService::class.intent)
        }

        fun stop(context: Context) {
            context.stopService(CompanionGatewayService::class.intent)
        }
    }
}
