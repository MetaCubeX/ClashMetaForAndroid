package com.github.kr328.clash.service

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.registerReceiverCompat
import com.github.kr328.clash.common.compat.startForegroundCompat
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.constants.Permissions
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.clash.clashRuntime
import com.github.kr328.clash.service.clash.module.*
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.cancelAndJoinBlocking
import com.github.kr328.clash.service.util.parseCIDR
import com.github.kr328.clash.service.util.sendClashStarted
import com.github.kr328.clash.service.util.sendClashStopped
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select

class TunService : VpnService(), CoroutineScope by CoroutineScope(Dispatchers.Default) {
    private val self: TunService
        get() = this

    private var reason: String? = null

    // kill switch state
    @Volatile private var userRequestedStop = false
    @Volatile private var revoked = false
    @Volatile private var destroying = false
    private var blockingInterface: ParcelFileDescriptor? = null
    private var killSwitchJob: Job? = null
    private var killSwitchReceiver: BroadcastReceiver? = null

    private val runtime = clashRuntime {
        val store = ServiceStore(self)

        val close = install(CloseModule(self))
        val tun = install(TunModule(self))
        val config = install(ConfigurationModule(self))
        val network = install(NetworkObserveModule(self))

        if (store.dynamicNotification)
            install(DynamicNotificationModule(self))
        else
            install(StaticNotificationModule(self))

        install(AppListCacheModule(self))
        install(TimeZoneModule(self))
        install(SuspendModule(self))

        try {
            tun.open()

            // the real interface replaced the blocking one (if any)
            releaseBlockingInterface()

            reportVpnState()

            while (isActive) {
                val quit = select<Boolean> {
                    close.onEvent {
                        userRequestedStop = true

                        true
                    }
                    config.onEvent {
                        reason = it.message

                        true
                    }
                    network.onEvent { n ->
                        if (Build.VERSION.SDK_INT in 22..28) @TargetApi(22) {
                            setUnderlyingNetworks(n?.let { arrayOf(it) })
                        }

                        false
                    }
                }

                if (quit) break
            }
        } catch (e: Exception) {
            Log.e("Create clash runtime: ${e.message}", e)

            reason = e.message
        } finally {
            withContext(NonCancellable) {
                // Engage before closing the core's interface, so the blocking
                // interface replaces it without a gap.
                val blocked = shouldEngageKillSwitch() && engageKillSwitch()

                tun.close()

                if (!blocked)
                    stopSelf()
            }
        }
    }

    private fun shouldEngageKillSwitch(): Boolean {
        return ServiceStore(self).killSwitch && !userRequestedStop && !revoked && !destroying
    }

    /**
     * Hold a VPN interface that routes the same traffic as the real one but
     * never reads from it, so packets are dropped instead of leaking out of
     * the physical network after an unexpected stop.
     */
    private fun engageKillSwitch(): Boolean {
        val store = ServiceStore(self)

        val fd = try {
            with(Builder()) {
                addAddress(TUN_GATEWAY, TUN_SUBNET_PREFIX)
                addAddress(TUN_GATEWAY6, TUN_SUBNET_PREFIX6)

                // Block IPv6 too, even if not proxied, so it cannot leak.
                applyRoutes(store, ipv6 = true)
                applyAccessControl(store)

                addDnsServer(TUN_DNS)
                setMtu(TUN_MTU)
                setBlocking(false)
                setSession("Clash")
                setConfigureIntent(mainActivityIntent())

                if (Build.VERSION.SDK_INT >= 29) {
                    setMetered(false)
                }

                establish()
            }
        } catch (e: Exception) {
            Log.w("Kill switch: establish blocking interface: ${e.message}", e)

            null
        } ?: return false

        synchronized(this) {
            blockingInterface?.close()
            blockingInterface = fd
        }

        StatusProvider.killSwitchActive = true

        Log.w("Kill switch engaged: ${reason ?: "unknown reason"}")

        notifyKillSwitch()

        killSwitchJob = launch {
            val stop = Channel<Unit>(Channel.CONFLATED)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    stop.trySend(Unit)
                }
            }

            registerReceiverCompat(
                receiver,
                IntentFilter(Intents.ACTION_CLASH_REQUEST_STOP),
                Permissions.RECEIVE_SELF_BROADCASTS,
                null
            )
            killSwitchReceiver = receiver

            // Wait for the runtime to wind down: a notification module may still
            // post once, and the core is reset only after it ends.
            delay(1000)
            notifyKillSwitch()

            // let the UI show stopped, with the failure reason
            sendClashStopped(reason)

            stop.receive()

            Log.i("Kill switch released by user")

            userRequestedStop = true
            reason = null // already reported when the kill switch engaged

            stopSelf()
        }

        return true
    }

    private fun releaseBlockingInterface() {
        synchronized(this) {
            blockingInterface?.close()
            blockingInterface = null
        }

        StatusProvider.killSwitchActive = false
    }

    private fun cancelKillSwitchJob() {
        killSwitchJob?.cancel()
        killSwitchJob = null

        killSwitchReceiver?.let {
            runCatching { unregisterReceiver(it) }
        }
        killSwitchReceiver = null
    }

    private fun notifyKillSwitch() {
        val content = reason?.let { getString(R.string.kill_switch_active_content, it) }
            ?: getString(R.string.kill_switch_active_content_unknown)

        val stop = PendingIntent.getBroadcast(
            self,
            R.id.nf_vpn_status,
            Intent(Intents.ACTION_CLASH_REQUEST_STOP).setPackage(packageName),
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )

        val notification = NotificationCompat.Builder(self, StaticNotificationModule.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_service)
            .setOngoing(true)
            .setColor(getColorCompat(R.color.color_clash))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentTitle(getText(R.string.kill_switch_active))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(mainActivityIntent())
            .addAction(0, getText(R.string.kill_switch_stop), stop)
            .build()

        startForegroundCompat(R.id.nf_clash_status, notification)
    }

    private fun reportVpnState() {
        StatusProvider.vpnState = if (Build.VERSION.SDK_INT >= 29) {
            StatusProvider.VpnState(alwaysOn = isAlwaysOn, lockdown = isLockdownEnabled)
        } else {
            null
        }
    }

    private fun mainActivityIntent(): PendingIntent {
        return PendingIntent.getActivity(
            self,
            R.id.nf_vpn_status,
            Intent().setComponent(Components.MAIN_ACTIVITY),
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    override fun onRevoke() {
        // another VPN took over or the user revoked us, never block in that case
        revoked = true

        super.onRevoke()
    }

    override fun onCreate() {
        super.onCreate()

        if (StatusProvider.serviceRunning)
            return stopSelf()

        StatusProvider.serviceRunning = true

        StaticNotificationModule.createNotificationChannel(this)
        StaticNotificationModule.notifyLoadingNotification(this)

        runtime.launch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (killSwitchJob != null) {
            // Started again while blocking: retry. The blocking interface stays
            // up until the runtime has established the real one.
            Log.i("Kill switch: restarting clash runtime")

            cancelKillSwitchJob()

            reason = null
            userRequestedStop = false

            StaticNotificationModule.notifyLoadingNotification(this)

            runtime.launch()
        }

        sendClashStarted()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        destroying = true

        cancelKillSwitchJob()
        releaseBlockingInterface()

        StatusProvider.vpnState = null

        TunModule.requestStop()

        StatusProvider.serviceRunning = false

        sendClashStopped(reason)

        cancelAndJoinBlocking()

        Log.i("TunService destroyed: ${reason ?: "successfully"}")

        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        runtime.requestGc()
    }

    private fun TunModule.open() {
        val store = ServiceStore(self)

        val device = with(Builder()) {
            // Interface address
            addAddress(TUN_GATEWAY, TUN_SUBNET_PREFIX)
            if (store.allowIpv6) {
                addAddress(TUN_GATEWAY6, TUN_SUBNET_PREFIX6)
            }

            // Route
            applyRoutes(store, ipv6 = store.allowIpv6)

            // Access Control
            applyAccessControl(store)

            // Blocking
            setBlocking(false)

            // Mtu
            setMtu(TUN_MTU)

            // Session Name
            setSession("Clash")

            // Virtual Dns Server
            addDnsServer(TUN_DNS)
            if (store.allowIpv6) {
                addDnsServer(TUN_DNS6)
            }

            // Open MainActivity
            setConfigureIntent(mainActivityIntent())

            // Metered
            if (Build.VERSION.SDK_INT >= 29) {
                setMetered(false)
            }

            // System Proxy
            if (Build.VERSION.SDK_INT >= 29 && store.systemProxy) {
                listenHttp()?.let {
                    setHttpProxy(
                        ProxyInfo.buildDirectProxy(
                            it.address.hostAddress,
                            it.port,
                            HTTP_PROXY_BLACK_LIST + if (store.bypassPrivateNetwork) HTTP_PROXY_LOCAL_LIST else emptyList()
                        )
                    )
                }
            }

            if (store.allowBypass) {
                allowBypass()
            }

            TunModule.TunDevice(
                fd = establish()?.detachFd()
                    ?: throw NullPointerException("Establish VPN rejected by system"),
                stack = store.tunStackMode,
                gateway = "$TUN_GATEWAY/$TUN_SUBNET_PREFIX" + if (store.allowIpv6) ",$TUN_GATEWAY6/$TUN_SUBNET_PREFIX6" else "",
                portal = TUN_PORTAL + if (store.allowIpv6) ",$TUN_PORTAL6" else "",
                dns = if (store.dnsHijacking) NET_ANY else (TUN_DNS + if (store.allowIpv6) ",$TUN_DNS6" else ""),
            )
        }

        attach(device)
    }

    private fun Builder.applyRoutes(store: ServiceStore, ipv6: Boolean) {
        if (store.bypassPrivateNetwork) {
            resources.getStringArray(R.array.bypass_private_route).map(::parseCIDR).forEach {
                addRoute(it.ip, it.prefix)
            }
            if (ipv6) {
                resources.getStringArray(R.array.bypass_private_route6).map(::parseCIDR).forEach {
                    addRoute(it.ip, it.prefix)
                }
            }

            // Route of virtual DNS
            addRoute(TUN_DNS, 32)
            if (ipv6) {
                addRoute(TUN_DNS6, 128)
            }
        } else {
            addRoute(NET_ANY, 0)
            if (ipv6) {
                addRoute(NET_ANY6, 0)
            }
        }
    }

    private fun Builder.applyAccessControl(store: ServiceStore) {
        when (store.accessControlMode) {
            AccessControlMode.AcceptAll -> Unit
            AccessControlMode.AcceptSelected -> {
                (store.accessControlPackages + packageName).forEach {
                    runCatching { addAllowedApplication(it) }
                }
            }
            AccessControlMode.DenySelected -> {
                (store.accessControlPackages - packageName).forEach {
                    runCatching { addDisallowedApplication(it) }
                }
            }
        }
    }

    companion object {
        private const val TUN_MTU = 9000
        private const val TUN_SUBNET_PREFIX = 30
        private const val TUN_GATEWAY = "172.19.0.1"
        private const val TUN_SUBNET_PREFIX6 = 126
        private const val TUN_GATEWAY6 = "fdfe:dcba:9876::1"
        private const val TUN_PORTAL = "172.19.0.2"
        private const val TUN_PORTAL6 = "fdfe:dcba:9876::2"
        private const val TUN_DNS = TUN_PORTAL
        private const val TUN_DNS6 = TUN_PORTAL6
        private const val NET_ANY = "0.0.0.0"
        private const val NET_ANY6 = "::"

        private val HTTP_PROXY_LOCAL_LIST: List<String> = listOf(
            "localhost",
            "*.local",
            "127.*",
            "10.*",
            "172.16.*",
            "172.17.*",
            "172.18.*",
            "172.19.*",
            "172.2*",
            "172.30.*",
            "172.31.*",
            "192.168.*"
        )
        private val HTTP_PROXY_BLACK_LIST: List<String> = listOf(
            "*zhihu.com",
            "*zhimg.com",
            "*jd.com",
            "100ime-iat-api.xfyun.cn",
            "*360buyimg.com",
        )
    }
}
